package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.loader.Schema4StageCompiler;
import com.enviouse.progressivestages.server.loader.StageFileParser;
import com.enviouse.progressivestages.server.loader.StagePackageParser;
import com.enviouse.progressivestages.server.rehaul.RehaulRuntime;
import com.enviouse.progressivestages.server.ServerEventHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class BlockOverrideGameTests {
    private static final StageId STAGE_A = StageId.parse("progressivestages:block_override_a");
    private static final StageId STAGE_B = StageId.parse("progressivestages:block_override_b");
    private static final StageId STAGE_C = StageId.parse("progressivestages:block_override_c");
    private static final StageId BREAK_GATE = StageId.parse("progressivestages:block_override_break_gate");
    private static final StageId TAG_RELOAD_STAGE = StageId.parse("progressivestages:block_override_tag_reload");
    private static final String TAG_RELOAD_PACK = "progressivestages-block-override-reload";

    private BlockOverrideGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_block_overrides")
    public static void selectorsOwnershipPrioritiesAndDropsShareOneServerDecision(GameTestHelper helper) {
        var stageA = parse(helper, STAGE_A, """
            [[blocks.overrides]]
            target = "id:minecraft:diamond_ore"
            display_as = "minecraft:stone"
            drop_as = "minecraft:cobblestone"
            priority = 50

            [[blocks.overrides]]
            target = "id:minecraft:stone"
            display_as = "minecraft:dirt"
            drop_as = "minecraft:oak_planks"
            priority = 60

            [[blocks.overrides]]
            target = "tag:minecraft:coal_ores"
            display_as = "minecraft:gravel"
            drop_as = "minecraft:coal"
            priority = 30

            [[blocks.overrides]]
            target = "mod:minecraft"
            display_as = "minecraft:netherrack"
            drop_as = "minecraft:redstone"
            priority = 10

            [[blocks.overrides]]
            targets = ["id:minecraft:iron_ore", "tag:minecraft:iron_ores"]
            display_as = "minecraft:deepslate"
            drop_as = "minecraft:raw_iron"
            priority = 20

            [[blocks.overrides]]
            target = "id:minecraft:chest"
            display_as = "minecraft:dirt"
            drop_as = "minecraft:stick"
            priority = 25
            """);
        var stageB = parse(helper, STAGE_B, """
            [[blocks.overrides]]
            target = "id:minecraft:diamond_ore"
            display_as = "minecraft:oak_planks"
            drop_as = "minecraft:oak_planks"
            priority = 40
            """);
        var stageC = parse(helper, STAGE_C, """
            [[blocks.overrides]]
            target = "id:minecraft:diamond_ore"
            display_as = "minecraft:gravel"
            drop_as = "minecraft:copper_ingot"
            priority = 50
            """);
        var breakGate = parse(helper, BREAK_GATE, """
            [[rules]]
            id = "block_override_break_gate"
            effect = "lock"
            action = "break"
            targets.blocks = ["id:minecraft:diamond_ore"]
            """);

        var runtime = RehaulRuntime.get();
        var previousSnapshot = runtime.snapshot();
        var registry = LockRegistry.getInstance();
        ServerPlayer player = player(helper, "override-test");
        ServerPlayer secondPlayer = player(helper, "override-test-two");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 8, 1));
        BlockPos chestPos = helper.absolutePos(new BlockPos(6, 8, 6));
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null,
            "The ore test position must start as empty space.");
        helper.assertTrue(level.getBlockState(chestPos).isAir() && level.getBlockEntity(chestPos) == null,
            "The container test position must start as empty space.");
        var ownership = level.getData(StageAttachments.TEAM_STAGES);
        registry.clear();
        StageOrder.getInstance().clear();
        level.players().add(player);
        level.players().add(secondPlayer);
        try {
            Map<StageId, com.enviouse.progressivestages.common.rehaul.CompiledStage> compiled = new LinkedHashMap<>();
            for (var parsed : List.of(stageA, stageB, stageC, breakGate)) {
                var definition = parsed.getStageDefinition();
                StageOrder.getInstance().registerStage(definition);
                registry.registerStage(definition);
                compiled.put(definition.getId(), Schema4StageCompiler.compile(definition,
                    parsed.getSourceConfig(), "block_override_test", 0));
            }
            runtime.rules().rebuild(CompiledSnapshot.create(1, compiled));

            var diamondOverrides = registry.getOreOverridesFor(Blocks.DIAMOND_ORE);
            helper.assertTrue(diamondOverrides.size() == 4,
                "The three exact stage rows and the broad mod selector must register for diamond ore.");
            helper.assertTrue(diamondOverrides.get(0).requiredStage.equals(STAGE_A)
                    && diamondOverrides.get(0).priority == 50,
                "Equal-priority winners must use the stable stage ID tie break.");
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_A)).orElse(false),
                "The highest-priority unowned stage must win.");
            var secondTeam = TeamProvider.getInstance().getTeamId(secondPlayer);
            ownership.grantStage(secondTeam, STAGE_A);
            helper.assertTrue(registry.findActiveOreOverride(secondPlayer, Blocks.DIAMOND_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_C)).orElse(false)
                    && registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE)
                        .map(entry -> entry.requiredStage.equals(STAGE_A)).orElse(false),
                "A second player must select an override from their own stage ownership.");
            ownership.revokeStage(secondTeam, STAGE_A);

            var stone = registry.findActiveOreOverride(player, Blocks.STONE);
            helper.assertTrue(stone.map(entry -> entry.displayAs.equals(net.minecraft.resources.ResourceLocation.parse("minecraft:dirt"))
                    && entry.dropAs.equals(net.minecraft.resources.ResourceLocation.parse("minecraft:oak_planks")))
                    .orElse(false),
                "An exact non-ore block must use its own override.");
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.COAL_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_A) && entry.dropAs.equals(net.minecraft.resources.ResourceLocation.parse("minecraft:coal")))
                    .orElse(false),
                "A block tag must expand to matching ore blocks.");
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.GRANITE)
                    .map(entry -> entry.requiredStage.equals(STAGE_A) && entry.priority == 10)
                    .orElse(false),
                "A mod selector must cover other blocks from the selected namespace.");
            helper.assertTrue(registry.getOreOverridesFor(Blocks.IRON_ORE).stream()
                    .filter(entry -> entry.requiredStage.equals(STAGE_A) && entry.priority == 20).count() == 1,
                "An exact ID and matching tag in one list must not register duplicate block entries.");

            var stageTeam = TeamProvider.getInstance().getTeamId(player);
            ownership.grantStage(stageTeam, STAGE_A);
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_C)).orElse(false),
                "Owning the first stage must select the next unowned equal-priority stage.");
            ownership.grantStage(stageTeam, STAGE_C);
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_B) && entry.priority == 40).orElse(false),
                "After equal-priority owners are granted, the lower-priority active stage must still apply.");
            ownership.grantStage(stageTeam, STAGE_B);
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE).isEmpty(),
                "Owning every matching stage must restore the real block behavior.");

            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
            level.setBlock(pos, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE).isEmpty(),
                "The player fixture owns every diamond override before checking vanilla behavior.");
            ownership.revokeStage(stageTeam, STAGE_A);
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE)
                    .map(entry -> entry.requiredStage.equals(STAGE_A)).orElse(false),
                "Revoking the required stage must reactivate its block override.");
            var realState = Blocks.DIAMOND_ORE.defaultBlockState();
            var displayedState = Blocks.STONE.defaultBlockState();
            ownership.grantStage(stageTeam, STAGE_A);
            float expectedBreakSpeed = player.getDigSpeed(displayedState, pos);
            ownership.revokeStage(stageTeam, STAGE_A);
            var breakSpeed = new PlayerEvent.BreakSpeed(player, realState, 1.0f, pos);
            ServerEventHandler.onBreakSpeed(breakSpeed);
            helper.assertTrue(Math.abs(breakSpeed.getNewSpeed() - expectedBreakSpeed) < 0.0001f,
                "The server break speed handler must match the displayed stone. actual=" + breakSpeed.getNewSpeed()
                    + ", expected=" + expectedBreakSpeed + ", override=" + registry.findActiveOreOverride(player, Blocks.DIAMOND_ORE));
            var harvest = new PlayerEvent.HarvestCheck(player, realState, level, pos, false);
            ServerEventHandler.onHarvestCheck(harvest);
            helper.assertTrue(harvest.canHarvest() == player.hasCorrectToolForDrops(displayedState, level, pos),
                "The server harvest handler must use the displayed block's tool requirement.");

            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            helper.assertTrue(registry.findActiveOreOverride(player, Blocks.CHEST).isPresent(),
                "A container may have a visual override while its real server block remains a chest.");
            helper.assertTrue(level.getBlockState(chestPos).is(Blocks.CHEST)
                    && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3,
                "Block display rules must not replace a container or change its contents.");
            helper.assertTrue(BlockEnforcer.canInteractWithBlock(player, Blocks.CHEST)
                    && BlockEnforcer.canPlaceBlock(player, Blocks.CHEST),
                "A display override must not create an interaction or placement lock.");

            ownership.revokeStage(stageTeam, STAGE_A);
            ownership.revokeStage(stageTeam, STAGE_B);
            ownership.revokeStage(stageTeam, STAGE_C);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            level.setBlock(pos, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
            BlockDropsCapture deniedDrops = new BlockDropsCapture(pos);
            NeoForge.EVENT_BUS.register(deniedDrops);
            long itemCountBeforeDeniedBreak = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).size();
            BlockEvent.BreakEvent deniedBreak = new BlockEvent.BreakEvent(level, pos,
                Blocks.DIAMOND_ORE.defaultBlockState(), player);
            try {
                NeoForge.EVENT_BUS.post(deniedBreak);
            } finally {
                NeoForge.EVENT_BUS.unregister(deniedDrops);
            }
            helper.assertTrue(deniedBreak.isCanceled() && level.getBlockState(pos).is(Blocks.DIAMOND_ORE),
                "A separate block break rule must cancel the real server break event.");
            helper.assertTrue(!deniedDrops.captured
                    && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).size() == itemCountBeforeDeniedBreak,
                "A denied block break must not run replacement drop handling or create drops.");
            ownership.grantStage(stageTeam, BREAK_GATE);
            assertBreakDrops(helper, player, pos, Blocks.STONE, Items.OAK_PLANKS, registry, ownership, stageTeam);
            assertBreakDrops(helper, player, pos, Blocks.OAK_LOG, Items.REDSTONE, registry, ownership, stageTeam);
            assertBreakDrops(helper, player, pos, Blocks.DIAMOND_ORE, Items.COBBLESTONE, registry, ownership, stageTeam);
            ownership.grantStage(stageTeam, STAGE_A);
            assertBreakDrops(helper, player, pos, Blocks.DIAMOND_ORE, Items.COPPER_INGOT, registry, ownership, stageTeam);
            ownership.grantStage(stageTeam, STAGE_C);
            assertBreakDrops(helper, player, pos, Blocks.DIAMOND_ORE, Items.OAK_PLANKS, registry, ownership, stageTeam);
            ownership.grantStage(stageTeam, STAGE_B);
            assertBreakDrops(helper, player, pos, Blocks.DIAMOND_ORE, Items.DIAMOND, registry, ownership, stageTeam);
            helper.succeed();
        } finally {
            ownership.revokeStage(TeamProvider.getInstance().getTeamId(player), STAGE_A);
            ownership.revokeStage(TeamProvider.getInstance().getTeamId(player), STAGE_B);
            ownership.revokeStage(TeamProvider.getInstance().getTeamId(player), STAGE_C);
            ownership.revokeStage(TeamProvider.getInstance().getTeamId(player), BREAK_GATE);
            ownership.revokeStage(TeamProvider.getInstance().getTeamId(secondPlayer), STAGE_A);
            level.players().remove(player);
            level.players().remove(secondPlayer);
            player.discard();
            secondPlayer.discard();
            runtime.rules().rebuild(previousSnapshot);
            registry.clear();
            StageOrder.getInstance().clear();
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 6000,
        batch = "progressivestages_block_override_tag_reload")
    public static void blockTagReloadRebuildsTheExpandedOverrideIndex(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        PackRepository repository = server.getPackRepository();
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(TAG_RELOAD_PACK);
        List<String> originalSelection = List.copyOf(repository.getSelectedIds());
        String packId = "file/" + TAG_RELOAD_PACK;

        try {
            writeTagReloadPack(packRoot, "minecraft:stone");
            repository.reload();
            if (repository.getAvailablePacks().stream().noneMatch(pack -> pack.getId().equals(packId))) {
                deleteTree(packRoot);
                helper.fail("The temporary block tag reload pack was not discovered.");
                return;
            }

            List<String> selectedPacks = new ArrayList<>(originalSelection);
            selectedPacks.add(packId);
            repository.setSelected(selectedPacks);
            server.reloadResources(selectedPacks).whenComplete((ignored, error) -> server.execute(() -> {
                if (error != null) {
                    restoreTagReloadPack(helper, server, repository, originalSelection, packRoot,
                        "The initial tag reload failed. " + error.getMessage());
                    return;
                }
                if (!hasOverrideFor(Blocks.STONE, TAG_RELOAD_STAGE)
                        || hasOverrideFor(Blocks.OAK_LOG, TAG_RELOAD_STAGE)) {
                    restoreTagReloadPack(helper, server, repository, originalSelection, packRoot,
                        "The first reload did not index only the stone tag member.");
                    return;
                }

                try {
                    writeBlockTag(packRoot, "minecraft:oak_log");
                } catch (IOException writeFailure) {
                    restoreTagReloadPack(helper, server, repository, originalSelection, packRoot,
                        "The updated tag fixture could not be written. " + writeFailure.getMessage());
                    return;
                }

                server.reloadResources(selectedPacks).whenComplete((ignoredUpdate, updateError) ->
                    server.execute(() -> {
                        String failure = updateError != null
                            ? "The updated tag reload failed. " + updateError.getMessage()
                            : !hasOverrideFor(Blocks.OAK_LOG, TAG_RELOAD_STAGE)
                                || hasOverrideFor(Blocks.STONE, TAG_RELOAD_STAGE)
                                    ? "The tag reload kept stale block index entries."
                                    : null;
                        restoreTagReloadPack(helper, server, repository, originalSelection, packRoot, failure);
                    }));
            }));
        } catch (IOException | RuntimeException setupFailure) {
            try {
                deleteTree(packRoot);
            } catch (IOException cleanupFailure) {
                setupFailure.addSuppressed(cleanupFailure);
            }
            helper.fail("The block tag reload fixture could not be prepared. " + setupFailure.getMessage());
        }
    }

    private static void restoreTagReloadPack(GameTestHelper helper, net.minecraft.server.MinecraftServer server,
                                             PackRepository repository, List<String> originalSelection,
                                             Path packRoot, String failure) {
        repository.setSelected(originalSelection);
        server.reloadResources(originalSelection).whenComplete((ignored, restoreError) -> server.execute(() -> {
            String result = failure;
            if (restoreError != null) {
                result = (result == null ? "" : result + " ")
                    + "The original data pack selection could not be restored. " + restoreError.getMessage();
            }
            try {
                deleteTree(packRoot);
                repository.reload();
            } catch (IOException cleanupFailure) {
                result = (result == null ? "" : result + " ")
                    + "The temporary block tag pack could not be removed. " + cleanupFailure.getMessage();
            }
            if (result == null) helper.succeed();
            else helper.fail(result);
        }));
    }

    private static boolean hasOverrideFor(Block block, StageId stage) {
        return LockRegistry.getInstance().getOreOverridesFor(block).stream()
            .anyMatch(entry -> entry.requiredStage.equals(stage));
    }

    private static void writeTagReloadPack(Path packRoot, String block) throws IOException {
        Files.createDirectories(packRoot);
        Files.writeString(packRoot.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":48,\"description\":\"Temporary block tag reload fixture\"}}",
            StandardCharsets.UTF_8);
        Files.createDirectories(packRoot.resolve("data/progressivestages/progressivestages/stages"));
        Files.writeString(packRoot.resolve(
                "data/progressivestages/progressivestages/stages/block_override_tag_reload.toml"), """
                [stage]
                id = "progressivestages:block_override_tag_reload"

                [[blocks.overrides]]
                target = "tag:progressivestages:block_override_reload"
                display_as = "minecraft:dirt"
                drop_as = "minecraft:cobblestone"
                """, StandardCharsets.UTF_8);
        writeBlockTag(packRoot, block);
    }

    private static void writeBlockTag(Path packRoot, String block) throws IOException {
        Path tagPath = packRoot.resolve("data/progressivestages/tags/block/block_override_reload.json");
        Files.createDirectories(tagPath.getParent());
        Files.writeString(tagPath, "{\"replace\":false,\"values\":[\"" + block + "\"]}",
            StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void assertBreakDrops(GameTestHelper helper, ServerPlayer player, BlockPos pos, Block block,
                                        net.minecraft.world.item.Item expected,
                                        LockRegistry registry,
                                        com.enviouse.progressivestages.common.data.TeamStageData ownership,
                                        UUID team) {
        var level = helper.getLevel();
        Set<UUID> existing = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))
            .stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        level.setBlock(pos, block.defaultBlockState(), 3);
        BlockDropsCapture capture = new BlockDropsCapture(pos);
        NeoForge.EVENT_BUS.register(capture);
        boolean destroyed;
        try {
            destroyed = level.destroyBlock(pos, true, player);
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }
        helper.assertTrue(destroyed, "The server must destroy the test ore.");
        List<ItemEntity> drops = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))
            .stream().filter(entity -> !existing.contains(entity.getUUID())).toList());
        var activeOverride = registry.findActiveOreOverride(player, block);
        helper.assertTrue(capture.captured && capture.eventCount == 1 && !capture.canceled
                && capture.drops.size() == 1 && capture.drops.getFirst().is(expected),
            "The final server BlockDropsEvent must contain only its configured replacement. captured="
                + capture.captured + ", events=" + capture.events + ".");
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(expected),
            "The actual server BlockDropsEvent must yield exactly " + expected + " for " + block
                + " while stage ownership is "
                + ownership.hasStage(team, STAGE_A) + "/" + ownership.hasStage(team, STAGE_C)
                + "/" + ownership.hasStage(team, STAGE_B) + "; actual="
                + drops.stream().map(entity -> entity.getItem() + " at " + entity.position()).toList()
                + "; active override=" + activeOverride.map(entry -> entry.requiredStage + " -> " + entry.dropAs)
                    .orElse("none") + ".");
        drops.forEach(entity -> entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
    }

    private static final class BlockDropsCapture {
        private final BlockPos target;
        private boolean captured;
        private boolean canceled;
        private int eventCount;
        private List<ItemStack> drops = List.of();
        private final List<String> events = new ArrayList<>();

        private BlockDropsCapture(BlockPos target) {
            this.target = target;
        }

        @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onBlockDrops(BlockDropsEvent event) {
            if (!event.getPos().equals(target)) return;
            captured = true;
            eventCount++;
            canceled = event.isCanceled();
            drops = event.getDrops().stream().map(ItemEntity::getItem).map(ItemStack::copy).toList();
            events.add("canceled=" + canceled + ", drops=" + drops);
        }
    }

    private static StageFileParser.ParseResult parse(GameTestHelper helper, StageId id, String rules) {
        String stage = "[schema]\nversion = 4\n[stage]\nid = \"" + id + "\"\n";
        var parsed = StagePackageParser.parseContents("block_override_test", "stage.toml", stage,
            "rules.toml", rules, "progression.toml", "");
        helper.assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        return parsed;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
            cookie.clientInformation());
    }
}
