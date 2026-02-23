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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        // Initialize FTB Quests compatibility (soft dependency)
        FTBQuestsCompat.init();
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

            // Send stage data to client
            var stages = StageManager.getInstance().getStages(player);
            NetworkHandler.sendStageSync(player, stages);

            // Sync lock registry to player (for EMI integration)
            NetworkHandler.sendLockSync(player);

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
                // We can't truly cancel the craft here, but the inventory scanner will drop it
                // And the mixin will hide the output slot
                ItemEnforcer.notifyLocked(player, event.getCrafting().getItem());
            }
        }
    }

    // ============ Item Use Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ItemEnforcer.canUseItem(player, event.getItemStack())) {
                event.setCanceled(true);
                ItemEnforcer.notifyLocked(player, event.getItemStack().getItem());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemStartUse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ItemEnforcer.canUseItem(player, event.getItem())) {
                event.setCanceled(true);
                ItemEnforcer.notifyLocked(player, event.getItem().getItem());
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

    // ============ Inventory Scanning ============

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        int scanFrequency = StageConfig.getInventoryScanFrequency();
        if (scanFrequency <= 0) {
            return;
        }

        UUID playerId = player.getUUID();
        long currentTime = player.level().getGameTime();
        Long lastScan = lastScanTime.get(playerId);

        if (lastScan == null || currentTime - lastScan >= scanFrequency) {
            lastScanTime.put(playerId, currentTime);
            InventoryScanner.scanAndDropLockedItems(player);
        }
    }

    // ============ Block Placement Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            Block block = event.getPlacedBlock().getBlock();
            if (!BlockEnforcer.canPlaceBlock(player, block)) {
                event.setCanceled(true);
                BlockEnforcer.notifyPlacementLocked(player, block);
            }
        }
    }

    // ============ Block Interaction Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Block block = event.getLevel().getBlockState(event.getPos()).getBlock();

            // Check if block interaction is locked
            if (!BlockEnforcer.canInteractWithBlock(player, block)) {
                event.setCanceled(true);
                BlockEnforcer.notifyInteractionLocked(player, block);
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDimensionTravel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!DimensionEnforcer.canTravelToDimension(player, event.getDimension())) {
                event.setCanceled(true);
                DimensionEnforcer.notifyLocked(player, event.getDimension().location());
            }
        }
    }

    // ============ Entity Attack Enforcement ============

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!EntityEnforcer.canAttackEntity(player, event.getTarget().getType())) {
                event.setCanceled(true);
                EntityEnforcer.notifyLocked(player, event.getTarget().getType());
            }
        }
    }

    // ============ Cleanup ============

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastScanTime.remove(player.getUUID());
            ItemEnforcer.clearCooldowns(player.getUUID());
        }
    }
}
