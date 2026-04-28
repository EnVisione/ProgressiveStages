package com.enviouse.progressivestages.server;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.common.team.TeamStageSync;
import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.compat.ftbquests.FTBQuestsCompat;
import com.enviouse.progressivestages.server.commands.StageCommand;
import com.enviouse.progressivestages.server.enforcement.*;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.enviouse.progressivestages.server.triggers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main event handler for server-side events
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public class ServerEventHandler {

    // Track last inventory scan time per player (for scan frequency)
    private static final Map<UUID, Long> lastScanTime = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Initialize team provider
        TeamProvider.getInstance().initialize();

        // Initialize stage manager
        StageManager.getInstance().initialize(event.getServer());

        // Initialize team stage sync
        TeamStageSync.initialize(event.getServer());

        // Load stage files
        StageFileLoader.getInstance().initialize(event.getServer());

        // Load trigger config and register trigger event handlers
        TriggerConfigLoader.loadTriggerConfig();
        NeoForge.EVENT_BUS.register(AdvancementStageGrants.class);
        NeoForge.EVENT_BUS.register(ItemPickupStageGrants.class);
        NeoForge.EVENT_BUS.register(DimensionStageGrants.class);
        NeoForge.EVENT_BUS.register(BossKillStageGrants.class);

        // Initialize FTB Teams integration (soft dependency)
        // Uses reflection to avoid loading FTBTeamsIntegration class (which imports FTB Teams API)
        // when FTB Teams is not installed — any direct class reference would cause NoClassDefFoundError
        if (net.neoforged.fml.ModList.get().isLoaded("ftbteams") && StageConfig.isFtbTeamsIntegrationEnabled()) {
            try {
                Class<?> ftbTeamsIntClass = Class.forName(
                    "com.enviouse.progressivestages.server.integration.FTBTeamsIntegration");
                ftbTeamsIntClass.getMethod("registerIfAvailable").invoke(null);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                com.mojang.logging.LogUtils.getLogger().warn(
                    "[ProgressiveStages] FTB Teams classes not available, skipping team integration: {}", e.getMessage());
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn(
                    "[ProgressiveStages] Failed to initialize FTB Teams integration: {}", e.getMessage());
            }
        }

        // Initialize FTB Quests compatibility (soft dependency)
        FTBQuestsCompat.init();

        // 2.0 soft-dep compat modules (Nature's Compass, Curios, Mekanism, automation report)
        com.enviouse.progressivestages.compat.ModCompatRegistry.initializeAll();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Sync stages to player on login (fires bulk event for FTB Quests)
            StageManager.getInstance().syncStagesOnLogin(player);

            // Send stage definitions to client first (v1.3 - includes dependencies)
            NetworkHandler.sendStageDefinitionsSync(player);

            // Sync lock registry to player BEFORE stage data so that when EMI reload
            // fires on stage sync arrival, ClientLockCache is already populated.
            NetworkHandler.sendLockSync(player);

            // Send stage data to client (triggers EMI reload on arrival)
            var stages = StageManager.getInstance().getStages(player);
            NetworkHandler.sendStageSync(player, stages);

            // Send initial creative bypass state
            if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
                NetworkHandler.sendCreativeBypass(player, true);
            }
        }
    }

    // ============ Gamemode Change Handling ============

    @SubscribeEvent
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!StageConfig.isAllowCreativeBypass()) {
                return; // Creative bypass disabled in config
            }

            GameType newMode = event.getNewGameMode();
            GameType oldMode = event.getCurrentGameMode();

            if (newMode == GameType.CREATIVE && oldMode != GameType.CREATIVE) {
                // Entering creative mode - enable bypass on client
                NetworkHandler.sendCreativeBypass(player, true);
            } else if (newMode != GameType.CREATIVE && oldMode == GameType.CREATIVE) {
                // Leaving creative mode - disable bypass on client
                NetworkHandler.sendCreativeBypass(player, false);
            }
        }
    }

    // ============ Crafting Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if the crafted item is locked
            if (!ItemEnforcer.canHoldItem(player, event.getCrafting())) {
                // Remove the crafted item — the player shouldn't have it
                event.getCrafting().setCount(0);
                ItemEnforcer.notifyLocked(player, event.getCrafting().getItem());
                return;
            }

            // Check recipe-only lock (item is not locked but the specific recipe is).
            // ResultSlotMixin is the primary gate (blocks mayPickup), but this is a backstop
            // for crafting paths that bypass the mixin (e.g., recipe book, auto-crafters).
            if (!event.getCrafting().isEmpty() &&
                    event.getInventory() instanceof net.minecraft.world.inventory.CraftingContainer craftingContainer) {

                // Check recipe_items lock (locks ALL recipes for this output item)
                if (RecipeEnforcer.isOutputItemRecipeLocked(player, event.getCrafting().getItem())) {
                    RecipeEnforcer.notifyOutputLocked(player, event.getCrafting().getItem());
                    event.getCrafting().setCount(0);
                    return;
                }

                // Check recipes lock (locks ONE specific recipe by ID).
                // Primary: use the recipe ID stored by CraftingMenuMixin (most reliable).
                // Fallback: do a recipe lookup (for paths that bypass the mixin).
                net.minecraft.resources.ResourceLocation recipeId =
                        com.enviouse.progressivestages.common.util.CraftingRecipeTracker.getLastRecipe(player.getUUID());
                if (recipeId == null) {
                    var server = player.getServer();
                    if (server != null) {
                        var found = server.getRecipeManager()
                                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                                        craftingContainer.asCraftInput(), player.level());
                        if (found.isPresent()) {
                            recipeId = found.get().id();
                        }
                    }
                }
                if (recipeId != null && RecipeEnforcer.isRecipeLockedForPlayer(player, recipeId)) {
                    RecipeEnforcer.notifyLocked(player, recipeId);
                    event.getCrafting().setCount(0);
                }
            }
        }
    }

    // ============ Item Use Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ItemEnforcer.canUseItem(player, event.getItemStack())) {
                event.setCanceled(true);
                ItemEnforcer.notifyLockedWithCooldown(player, event.getItemStack().getItem());
                return;
            }
            // Screen-lock for item-opened GUIs (backpacks, portable crafting, etc.).
            if (!ScreenEnforcer.canOpenFromItem(player, event.getItemStack())) {
                event.setCanceled(true);
                ScreenEnforcer.notifyLockedItem(player, event.getItemStack());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemStartUse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ItemEnforcer.canUseItem(player, event.getItem())) {
                event.setCanceled(true);
                ItemEnforcer.notifyLockedWithCooldown(player, event.getItem().getItem());
            }
        }
    }

    // ============ Left-Click / Mining Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Block mining/breaking when the held tool/item is locked
            if (!event.getItemStack().isEmpty() && !ItemEnforcer.canUseItem(player, event.getItemStack())) {
                event.setCanceled(true);
                ItemEnforcer.notifyLockedWithCooldown(player, event.getItemStack().getItem());
            }
        }
    }

    // ============ Item Pickup Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemEntity itemEntity = event.getItemEntity();
            if (!ItemEnforcer.canPickupItem(player, itemEntity.getItem())) {
                event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
                // Use cooldown system to prevent chat spam
                ItemEnforcer.notifyLockedWithCooldown(player, itemEntity.getItem().getItem());
            }
        }
    }

    // ============ Inventory Scanning & Dimension Tick Check ============

    private static final Map<UUID, Long> lastDimensionCheck = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        long currentTime = player.level().getGameTime();

        // ── Tick-based dimension enforcement (safety net for mods that bypass both events) ──
        if (StageConfig.isBlockDimensionTravel()) {
            Long lastDimCheck = lastDimensionCheck.get(playerId);
            if (lastDimCheck == null || currentTime - lastDimCheck >= 20) { // Check every 20 ticks (1 second)
                lastDimensionCheck.put(playerId, currentTime);

                if (!(StageConfig.isAllowCreativeBypass() && player.isCreative())) {
                    net.minecraft.resources.ResourceLocation currentDim = player.level().dimension().location();
                    if (DimensionEnforcer.isDimensionLockedForPlayer(player, currentDim)) {
                        // Player is in a locked dimension — find a safe dimension to send them to
                        // Try overworld first, then any unlocked dimension
                        net.minecraft.server.level.ServerLevel targetLevel = player.server.overworld();
                        net.minecraft.resources.ResourceLocation overworldId = targetLevel.dimension().location();

                        if (DimensionEnforcer.isDimensionLockedForPlayer(player, overworldId)) {
                            // Overworld is also locked — try to find any unlocked dimension
                            for (net.minecraft.server.level.ServerLevel level : player.server.getAllLevels()) {
                                if (!DimensionEnforcer.isDimensionLockedForPlayer(player, level.dimension().location())) {
                                    targetLevel = level;
                                    break;
                                }
                            }
                        }

                        if (!DimensionEnforcer.isDimensionLockedForPlayer(player, targetLevel.dimension().location())) {
                            net.minecraft.core.BlockPos spawn = targetLevel.getSharedSpawnPos();
                            player.teleportTo(targetLevel,
                                    spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                                    java.util.Set.of(), player.getYRot(), player.getXRot());
                            DimensionEnforcer.notifyLocked(player, currentDim);
                        }
                    }
                }
            }
        }

        // ── Region / structure entry checks (runs at region_tick_frequency) ──
        int regionFreq = StageConfig.getRegionTickFrequency();
        Long lastRegion = lastRegionCheck.get(playerId);
        if (lastRegion == null || currentTime - lastRegion >= regionFreq) {
            lastRegionCheck.put(playerId, currentTime);
            RegionEnforcer.checkPlayerEntry(player);
            StructureEnforcer.checkPlayerEntry(player);
        }

        // ── Fluid submersion effects (every tick while in a locked fluid) ──
        FluidEnforcer.applySubmersionEffects(player);

        // ── Inventory scanning ──
        int scanFrequency = StageConfig.getInventoryScanFrequency();
        if (scanFrequency <= 0) {
            return;
        }

        Long lastScan = lastScanTime.get(playerId);

        if (lastScan == null || currentTime - lastScan >= scanFrequency) {
            lastScanTime.put(playerId, currentTime);

            // Full inventory drop takes priority over hotbar-only move
            if (StageConfig.isBlockItemInventory()) {
                InventoryScanner.scanAndDropLockedItems(player);
            } else if (StageConfig.isBlockItemHotbar()) {
                InventoryScanner.scanAndMoveLockedItemsFromHotbar(player);
            }

            // Strip locked enchantments from every item the player is carrying.
            if (StageConfig.isBlockEnchants()) {
                boolean anyStripped = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                    if (EnchantEnforcer.stripLockedEnchants(player, s)) anyStripped = true;
                }
                if (anyStripped) player.containerMenu.broadcastChanges();
            }
        }
    }

    private static final Map<UUID, Long> lastRegionCheck = new ConcurrentHashMap<>();

    // ============ Block Placement Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            Block block = event.getPlacedBlock().getBlock();
            if (!BlockEnforcer.canPlaceBlock(player, block)) {
                event.setCanceled(true);
                BlockEnforcer.notifyPlacementLocked(player, block);
                return;
            }
            // Crop planting
            if (!CropEnforcer.canPlace(player, block)) {
                event.setCanceled(true);
                CropEnforcer.notifyLocked(player, block);
                return;
            }
            // Region / structure block-place guard
            net.minecraft.core.BlockPos pos = event.getPos();
            if (!RegionEnforcer.canPlaceBlock(player, pos)) {
                event.setCanceled(true);
                return;
            }
            if (!StructureEnforcer.canPlaceBlock(player, pos)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            net.minecraft.core.BlockPos pos = event.getPos();

            // Structure chest-locking: containers (chest / barrel / shulker / lootr / any
            // block entity implementing Container) inside a locked structure can't be
            // broken by players lacking the stage — same intent as the right-click gate,
            // so loot can't be spilled by breaking the block.
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                if (StructureEnforcer.isContainerAt(sl, pos)) {
                    var entryStage = StructureEnforcer.getLockedEntryStageAt(sl, pos);
                    if (entryStage.isPresent()
                            && !(StageConfig.isAllowCreativeBypass() && player.isCreative())
                            && !StageManager.getInstance().hasStage(player, entryStage.get())) {
                        event.setCanceled(true);
                        ItemEnforcer.notifyLockedWithCooldown(player, entryStage.get(),
                            StageConfig.getMsgTypeLabelStructureContents());
                        return;
                    }
                }
            }

            if (!RegionEnforcer.canBreakBlock(player, pos)) {
                event.setCanceled(true);
                return;
            }
            if (!StructureEnforcer.canBreakBlock(player, pos)) {
                event.setCanceled(true);
            }
        }
    }

    // ============ Block Interaction Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            net.minecraft.world.level.block.state.BlockState targetState = event.getLevel().getBlockState(event.getPos());
            Block block = targetState.getBlock();

            // Screen lock — block opening locked GUIs (crafting tables, anvils, modded machines)
            if (!ScreenEnforcer.canOpenScreen(player, block)) {
                event.setCanceled(true);
                ScreenEnforcer.notifyLocked(player, block);
                return;
            }

            // Bucket pickup of a locked fluid — empty bucket right-clicks a fluid source.
            if (FluidEnforcer.isBucket(event.getItemStack())) {
                if (!FluidEnforcer.canPickupFluid(player, event.getLevel(), event.getPos())) {
                    event.setCanceled(true);
                    FluidEnforcer.notifyPickupLocked(player, event.getLevel(), event.getPos());
                    return;
                }
            }

            // Structure chest-locking: if the target block sits inside a locked structure and
            // the player lacks the gate stage, refuse the interaction entirely. This covers
            // clicking a chest through a window / over a wall where the push-back tick hasn't
            // fired. The guard runs for every block click inside a locked structure, not just
            // containers — picking open a locked tomb's pressure plate is equally gated.
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                var entryStage = StructureEnforcer.getLockedEntryStageAt(sl, event.getPos());
                if (entryStage.isPresent()
                        && !(StageConfig.isAllowCreativeBypass() && player.isCreative())
                        && !StageManager.getInstance().hasStage(player, entryStage.get())) {
                    event.setCanceled(true);
                    ItemEnforcer.notifyLockedWithCooldown(player, entryStage.get(), StageConfig.getMsgTypeLabelStructureContents());
                    return;
                }
            }

            // Check if block interaction is locked (state-aware: covers Visual Workbench replacements)
            if (!BlockEnforcer.canInteractWithBlock(player, targetState)) {
                event.setCanceled(true);
                BlockEnforcer.notifyInteractionLocked(player, targetState);
                return;
            }

            // Check interaction locks (item-on-block, Create-style interactions)
            if (!InteractionEnforcer.canInteract(player, event.getItemStack(), block)) {
                event.setCanceled(true);
                InteractionEnforcer.notifyLocked(player, event.getItemStack(), block);
                return;
            }

            // Also check if the held item is locked (for item-on-block interactions)
            if (!event.getItemStack().isEmpty()) {
                if (!ItemEnforcer.canUseItem(player, event.getItemStack())) {
                    event.setCanceled(true);
                    ItemEnforcer.notifyLocked(player, event.getItemStack().getItem());
                    return;
                }

                // Check if trying to place a locked block
                if (event.getItemStack().getItem() instanceof BlockItem blockItem) {
                    if (!BlockEnforcer.canPlaceBlock(player, blockItem.getBlock())) {
                        event.setCanceled(true);
                        BlockEnforcer.notifyPlacementLocked(player, blockItem.getBlock());
                    }
                }
            }
        }
    }

    // ============ Dimension Travel Enforcement ============

    /**
     * Primary gate: cancels dimension travel BEFORE it happens.
     * Works for all teleportation that goes through Entity.changeDimension().
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDimensionTravel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Always save the player's current position so the safety net can use it
            DimensionEnforcer.savePositionBeforeTravel(player);

            if (!DimensionEnforcer.canTravelToDimension(player, event.getDimension())) {
                event.setCanceled(true);
                DimensionEnforcer.notifyLocked(player, event.getDimension().location());
            }
        }
    }

    /**
     * Safety net: catches dimension changes that bypassed the pre-travel event.
     * Some mods (e.g., Twilight Forest) use custom teleportation mechanisms that
     * may not fire EntityTravelToDimensionEvent. This handler detects the player
     * is now in a locked dimension and teleports them back.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DimensionEnforcer.handlePostTravelSafetyNet(player, event.getFrom(), event.getTo());
        }
    }

    // ============ Mob Spawn Gating (v1.5) ============

    /**
     * Gates mob spawns behind stages. Uses FinalizeSpawnEvent which is fired for
     * natural spawns, spawners, spawn eggs, and most modded spawn paths.
     *
     * <p>We cancel via {@code setSpawnCancelled(true)} instead of {@code setCanceled(true)}
     * because the latter only skips {@code finalizeSpawn} — the entity would still be added
     * to the world. {@code setSpawnCancelled} is the correct API for preventing the spawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // 1. Try replacement first — a configured replacement takes precedence over a plain cancel.
        if (MobReplacementEnforcer.tryReplace(event.getEntity(), event.getLevel(),
                event.getX(), event.getY(), event.getZ(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
            return;
        }

        // 2. Region-level spawn suppression
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            if (RegionEnforcer.blocksMobSpawn(sl, event.getX(), event.getY(), event.getZ())) {
                event.setSpawnCancelled(true);
                return;
            }
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(event.getX(), event.getY(), event.getZ());
            if (StructureEnforcer.blocksMobSpawn(sl, bp)) {
                event.setSpawnCancelled(true);
                return;
            }
        }

        // 3. Plain stage-based cancel
        if (MobSpawnEnforcer.shouldCancelSpawn(event.getEntity(), event.getLevel(),
                event.getX(), event.getY(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }

    // ============ Entity Interaction Enforcement (item_on_entity) ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var entityType = event.getTarget().getType();

            // Structure chest-locking applied to entities too: lootr minecarts, item frames
            // with loot, and other entity-based containers sitting inside a locked structure
            // must refuse interaction for players lacking the stage.
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                var entryStage = StructureEnforcer.getLockedEntryStageAt(sl, event.getTarget().blockPosition());
                if (entryStage.isPresent()
                        && !(StageConfig.isAllowCreativeBypass() && player.isCreative())
                        && !StageManager.getInstance().hasStage(player, entryStage.get())) {
                    event.setCanceled(true);
                    ItemEnforcer.notifyLockedWithCooldown(player, entryStage.get(),
                        StageConfig.getMsgTypeLabelStructureContents());
                    return;
                }
            }

            if (!InteractionEnforcer.canInteractWithEntity(player, event.getItemStack(), entityType)) {
                event.setCanceled(true);
                InteractionEnforcer.notifyEntityInteractionLocked(player, event.getItemStack(), entityType);
                return;
            }
            // Pet taming/breeding gate
            if (!PetEnforcer.canInteract(player, entityType, event.getTarget())) {
                event.setCanceled(true);
                PetEnforcer.notifyLocked(player, entityType, event.getTarget());
            }
        }
    }

    // ============ Entity Attack Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if the entity type is locked
            if (!EntityEnforcer.canAttackEntity(player, event.getTarget().getType())) {
                event.setCanceled(true);
                EntityEnforcer.notifyLocked(player, event.getTarget().getType());
                return;
            }

            // Also check if the held weapon/item is locked (can't attack with a locked sword, etc.)
            var heldItem = player.getMainHandItem();
            if (!heldItem.isEmpty() && !ItemEnforcer.canUseItem(player, heldItem)) {
                event.setCanceled(true);
                ItemEnforcer.notifyLockedWithCooldown(player, heldItem.getItem());
            }
        }
    }

    // ============ 2.0: Enchantment Enforcement (anvil) ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        // If either input — the target item or the enchantment source — already carries
        // a locked enchantment, refuse the result so the player can't slip the enchant
        // onto a different item via the anvil.
        if (EnchantEnforcer.anyEnchantLocked(player, event.getLeft())
                || EnchantEnforcer.anyEnchantLocked(player, event.getRight())) {
            event.setCanceled(true);
        }
    }

    // ============ 2.0: Crop Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.level.block.Block block = event.getState().getBlock();
        net.minecraft.core.BlockPos pos = event.getPos();
        if (CropEnforcer.shouldCancelGrowth(sl, block, pos.getX(), pos.getY(), pos.getZ())) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBonemeal(BonemealEvent event) {
        net.minecraft.world.entity.player.Player p = event.getPlayer();
        if (!(p instanceof ServerPlayer player)) return;
        net.minecraft.world.level.block.Block block = event.getState().getBlock();
        if (!CropEnforcer.canBonemeal(player, block)) {
            event.setCanceled(true);
            CropEnforcer.notifyLocked(player, block);
        }
    }

    // ============ 2.0: Loot Filtering ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.entity.player.Player killer = null;
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player p) killer = p;
        LootEnforcer.filterLivingDrops(event.getDrops(), sl,
            event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), killer);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockDrops(BlockDropsEvent event) {
        net.minecraft.server.level.ServerLevel sl = event.getLevel();
        net.minecraft.world.entity.player.Player breaker = null;
        if (event.getBreaker() instanceof net.minecraft.world.entity.player.Player p) breaker = p;
        net.minecraft.core.BlockPos pos = event.getPos();
        // Loot-category filter (applies to any registered block that drops a locked item).
        LootEnforcer.filterBlockDrops(event.getDrops(), sl,
            pos.getX(), pos.getY(), pos.getZ(), breaker);
        // Crop-harvest filter — keep only seeds when the broken block is a locked crop.
        LootEnforcer.filterCropHarvest(event.getDrops(), sl, event.getState().getBlock(),
            pos.getX(), pos.getY(), pos.getZ(), breaker);
    }

    // ============ 2.0: Fluid Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (FluidEnforcer.shouldCancelFluidPlace(event.getLevel(), event.getPos(), event.getNewState())) {
            // Cancelling preserves the original block (prevents the locked fluid from replacing it).
            event.setCanceled(true);
        }
    }

    // ============ 2.0: Explosion Region/Structure Guards ============

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        RegionEnforcer.filterExplosionBlocks(sl, event.getAffectedBlocks());
        StructureEnforcer.filterExplosionBlocks(sl, event.getAffectedBlocks());
    }

    // ============ 2.0: Pet Riding ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMount(EntityMountEvent event) {
        // Only gate mounts (not dismounts) by players onto locked pet types.
        if (!event.isMounting()) return;
        if (!(event.getEntityMounting() instanceof ServerPlayer player)) return;
        if (event.getEntityBeingMounted() == null) return;
        if (!PetEnforcer.canInteract(player, event.getEntityBeingMounted().getType(),
                event.getEntityBeingMounted())) {
            event.setCanceled(true);
            PetEnforcer.notifyLocked(player, event.getEntityBeingMounted().getType(),
                event.getEntityBeingMounted());
        }
    }

    // ============ Cleanup ============

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastScanTime.remove(player.getUUID());
            lastDimensionCheck.remove(player.getUUID());
            lastRegionCheck.remove(player.getUUID());
            ItemEnforcer.clearCooldowns(player.getUUID());
            DimensionEnforcer.cleanupPlayer(player.getUUID());
        }
    }
}
