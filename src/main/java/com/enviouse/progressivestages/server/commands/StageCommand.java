package com.enviouse.progressivestages.server.commands;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.TextUtil;
import com.enviouse.progressivestages.compat.ftbquests.FTBQuestsCompat;
import com.enviouse.progressivestages.compat.ftbquests.FtbQuestsHooks;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.enviouse.progressivestages.server.triggers.TriggerPersistence;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Stage management commands: /stage grant, revoke, list, check
 */
public class StageCommand {

    // Admin bypass confirmation cache (playerUUID + stageId -> expiry timestamp)
    private static final Map<String, Long> bypassConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 10_000; // 10 seconds

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stage")
            .requires(source -> source.hasPermission(2))

            // /stage grant <player> <stage>
            .then(Commands.literal("grant")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(StageCommand::suggestStages)
                        .executes(StageCommand::grantStage))))

            // /stage revoke <player> <stage>
            .then(Commands.literal("revoke")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(StageCommand::suggestStages)
                        .executes(StageCommand::revokeStage))))

            // /stage list [player]
            .then(Commands.literal("list")
                .executes(ctx -> listStages(ctx, null))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> listStages(ctx, EntityArgument.getPlayer(ctx, "player")))))

            // /stage check <player> <stage>
            .then(Commands.literal("check")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(StageCommand::suggestStages)
                        .executes(StageCommand::checkStage))))

            // /stage info <stage>
            .then(Commands.literal("info")
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests(StageCommand::suggestStages)
                    .executes(StageCommand::stageInfo)))

            // /stage tree - Shows dependency tree (v1.3)
            .then(Commands.literal("tree")
                .executes(StageCommand::showDependencyTree))

            // /stage progress [stage|next|all] [player]
            //
            // Three modes:
            //   • /stage progress                         — bare form, defaults to `next` for the caller.
            //   • /stage progress next [player]           — every stage whose deps are met but isn't granted yet.
            //   • /stage progress all  [player]           — every unowned stage in registration order.
            //   • /stage progress <stage> [player]        — per-trigger satisfaction breakdown for one stage.
            //
            // Edge case: if a stage is literally named `next` or `all`, query it via
            // `/stage info` instead — Brigadier will route the literal first here.
            .then(Commands.literal("progress")
                .executes(ctx -> showProgressNext(ctx, null))
                .then(Commands.literal("next")
                    .executes(ctx -> showProgressNext(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showProgressNext(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("all")
                    .executes(ctx -> showProgressAll(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showProgressAll(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.argument("stage", StringArgumentType.word())
                    .suggests(StageCommand::suggestProgressStages)
                    .executes(ctx -> showProgress(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> showProgress(ctx, EntityArgument.getPlayer(ctx, "player"))))))
        );

        // /progressivestages subcommands
        // Most subcommands require permission level 3 (admin); no-creative-popup is open
        // to every player since it's a per-player preference toggle.
        dispatcher.register(Commands.literal("progressivestages")

            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(3))
                .executes(StageCommand::reloadStages))

            .then(Commands.literal("validate")
                .requires(source -> source.hasPermission(3))
                .executes(StageCommand::validateStages))

            // /progressivestages ftb status [player]
            .then(Commands.literal("ftb")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("status")
                    .executes(ctx -> ftbStatus(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> ftbStatus(ctx, EntityArgument.getPlayer(ctx, "player"))))))

            // /progressivestages trigger reset <player> <type> <key>
            .then(Commands.literal("trigger")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("type", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("dimension");
                                builder.suggest("boss");
                                builder.suggest("multi");
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(StageCommand::resetTrigger))))))

            // /progressivestages multi list [player]
            // Inspect 2.0 multi-trigger requirements and (optionally) per-player progress.
            .then(Commands.literal("multi")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("list")
                    .executes(ctx -> listMultiRequirements(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> listMultiRequirements(ctx,
                            EntityArgument.getPlayer(ctx, "player"))))))

            // /progressivestages no-creative-popup — toggle the creative-mode bypass
            // warning for the calling player. Open to everyone (per-player preference).
            .then(Commands.literal("no-creative-popup")
                .executes(StageCommand::toggleCreativePopup))
        );
    }

    private static int toggleCreativePopup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean nowHidden = com.enviouse.progressivestages.server.CreativeBypassNotifier.toggleHidden(player);
        String template = nowHidden
            ? StageConfig.getMsgCmdCreativePopupDisabled()
            : StageConfig.getMsgCmdCreativePopupEnabled();
        context.getSource().sendSystemMessage(TextUtil.parseColorCodes(template));
        return 1;
    }

    /**
     * Brigadier suggestions for stage IDs.
     * Provides autocomplete for all registered stages, filtered by prefix.
     *
     * <p>Outputs normalized IDs aligned with StageId normalization rules:
     * <ul>
     *   <li>All lowercase</li>
     *   <li>Namespace: a-z, 0-9, underscore, hyphen, period</li>
     *   <li>Path: a-z, 0-9, underscore, hyphen, period, forward slash</li>
     *   <li>For default namespace (progressivestages), suggests just the path</li>
     *   <li>For other namespaces, suggests full namespaced ID</li>
     * </ul>
     *
     * <p>This ensures suggestions always produce valid StageIds when selected.
     */
    private static CompletableFuture<Suggestions> suggestStages(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (StageId stageId : StageOrder.getInstance().getAllStageIds()) {
            // Get normalized path and full ID (already lowercase from StageId)
            String path = stageId.getPath();
            String full = stageId.toString();

            // Filter by prefix - match against both path and full ID
            if (path.startsWith(remaining) || full.startsWith(remaining)) {
                // For default namespace, suggest just the path (cleaner)
                // This works for both flat IDs (iron_age) and hierarchical (tech/iron_age)
                if (stageId.isDefaultNamespace()) {
                    builder.suggest(path);
                } else {
                    // For other namespaces, suggest full ID
                    builder.suggest(full);
                }
            }
        }
        return builder.buildFuture();
    }

    /**
     * Suggestions for the {@code /stage progress <stage>} argument.
     *
     * <p>When the command source is a player, the player's next-reachable stages
     * (deps met, not yet owned) are surfaced first so {@code <tab>} on an empty
     * input nominates concrete progression candidates — the auto-fill behavior
     * the user asked for. Any other stage is still accepted: the full stage
     * list is appended so historical or out-of-order queries continue to work.
     */
    private static CompletableFuture<Suggestions> suggestProgressStages(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        Set<String> seen = new HashSet<>();

        // Prioritize next-reachable stages for the executing player.
        if (context.getSource().getEntity() instanceof ServerPlayer sp) {
            Set<StageId> owned = StageManager.getInstance().getStages(sp);
            for (StageId stageId : findNextStages(sp, owned)) {
                offerSuggestion(builder, stageId, remaining, seen);
            }
        }

        // Then every other stage — keeps free-form lookups working (granted stages,
        // stages still gated behind locked deps, admin debugging).
        for (StageId stageId : StageOrder.getInstance().getAllStageIds()) {
            offerSuggestion(builder, stageId, remaining, seen);
        }
        return builder.buildFuture();
    }

    private static void offerSuggestion(SuggestionsBuilder builder, StageId stageId, String remaining, Set<String> seen) {
        String suggest = stageId.isDefaultNamespace() ? stageId.getPath() : stageId.toString();
        if (!seen.add(suggest)) return;
        String path = stageId.getPath();
        String full = stageId.toString();
        if (path.startsWith(remaining) || full.startsWith(remaining)) {
            builder.suggest(suggest);
        }
    }

    private static int grantStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }
        
        // Check if player already has this stage
        if (StageManager.getInstance().hasStage(player, stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdAlreadyHasStage().replace("{stage}", stageName)));
            return 0;
        }

        // Check for missing dependencies (v1.3)
        List<StageId> missingDeps = StageManager.getInstance().getMissingDependencies(player, stageId);
        
        if (!missingDeps.isEmpty() && !StageConfig.isLinearProgression()) {
            // Check for admin bypass confirmation
            String confirmKey = getConfirmationKey(player, stageId);
            Long confirmExpiry = bypassConfirmations.get(confirmKey);
            long now = System.currentTimeMillis();
            
            if (confirmExpiry != null && now < confirmExpiry) {
                // Bypass confirmed - grant the stage directly
                bypassConfirmations.remove(confirmKey);
                StageManager.getInstance().grantStageBypassDependencies(player, stageId, 
                    com.enviouse.progressivestages.common.api.StageCause.COMMAND);
                
                String playerName = player.getName().getString();
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdGrantBypass()
                        .replace("{stage}", stageName)
                        .replace("{player}", playerName)), true);
                return 1;
            } else {
                // Request confirmation
                bypassConfirmations.put(confirmKey, now + CONFIRMATION_TIMEOUT_MS);
                cleanupExpiredConfirmations();
                
                String missingList = missingDeps.stream()
                    .map(StageId::getPath)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                
                String playerName = player.getName().getString();
                context.getSource().sendFailure(TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdGrantMissingDeps()
                        .replace("{stage}", stageName)
                        .replace("{player}", playerName)
                        .replace("{dependencies}", missingList)));
                context.getSource().sendFailure(TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdGrantBypassHint()));
                return 0;
            }
        }

        // No dependency issues or linear_progression is on - grant normally
        StageManager.getInstance().grantStage(player, stageId);

        String playerName = player.getName().getString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdGrantSuccess()
                .replace("{stage}", stageName)
                .replace("{player}", playerName)), true);

        return 1;
    }
    
    private static String getConfirmationKey(ServerPlayer player, StageId stageId) {
        return player.getUUID() + ":" + stageId.toString();
    }
    
    private static void cleanupExpiredConfirmations() {
        long now = System.currentTimeMillis();
        bypassConfirmations.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static int revokeStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }

        StageManager.getInstance().revokeStage(player, stageId);

        String playerName = player.getName().getString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdRevokeSuccess()
                .replace("{stage}", stageName)
                .replace("{player}", playerName)), true);

        return 1;
    }

    private static int listStages(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        final ServerPlayer player;
        if (target != null) {
            player = target;
        } else if (context.getSource().getEntity() instanceof ServerPlayer sp) {
            player = sp;
        } else {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdSpecifyPlayer()));
            return 0;
        }

        Set<StageId> stages = StageManager.getInstance().getStages(player);
        int total = StageOrder.getInstance().getStageCount();

        String playerName = player.getName().getString();
        int stageCount = stages.size();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdListHeader()
                .replace("{player}", playerName)
                .replace("{count}", String.valueOf(stageCount))
                .replace("{total}", String.valueOf(total))), false);

        if (stages.isEmpty()) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdListEmpty()), false);
        } else {
            for (StageId stageId : StageOrder.getInstance().getOrderedStages()) {
                boolean has = stages.contains(stageId);
                Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);
                String displayName = defOpt.map(StageDefinition::getDisplayName).orElse(stageId.getPath());
                List<StageId> deps = defOpt.map(StageDefinition::getDependencies).orElse(java.util.Collections.emptyList());

                String depStr;
                if (deps.isEmpty()) {
                    depStr = "";
                } else {
                    String depsJoined = deps.stream().map(StageId::getPath).reduce((a, b) -> a + ", " + b).orElse("");
                    depStr = StageConfig.getMsgCmdListRequiresFormat().replace("{deps}", depsJoined);
                }

                String color = has ? "&a" : "&8";
                String check = has ? " &a\u2713" : "";
                String entryName = color + displayName;
                String entryLine = StageConfig.getMsgCmdListEntry()
                    .replace("{name}", entryName)
                    .replace("{check}", check)
                    .replace("{deps}", depStr);
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(entryLine), false);
            }
        }

        return 1;
    }

    private static int checkStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }

        boolean has = StageManager.getInstance().hasStage(player, stageId);

        String playerName = player.getName().getString();
        String template = has ? StageConfig.getMsgCmdCheckHas() : StageConfig.getMsgCmdCheckNotHas();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            template.replace("{player}", playerName)
                    .replace("{stage}", stageName)), false);

        return has ? 1 : 0;
    }

    private static int stageInfo(CommandContext<CommandSourceStack> context) {
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);

        if (defOpt.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }

        StageDefinition def = defOpt.get();

        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdInfoHeader().replace("{stage}", def.getDisplayName())), false);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdInfoId().replace("{id}", def.getId().toString())), false);

        // v1.3: Show dependencies instead of order
        List<StageId> deps = def.getDependencies();
        if (deps.isEmpty()) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdInfoDepsNone()), false);
        } else {
            String depStr = deps.stream().map(StageId::getPath).reduce((a, b) -> a + ", " + b).orElse("");
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdInfoDeps().replace("{deps}", depStr)), false);
        }

        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdInfoDescription().replace("{description}", def.getDescription())), false);

        var locks = def.getLocks();
        int lockCount = locks.items().locked().size()
            + locks.blocks().locked().size()
            + locks.fluids().locked().size()
            + locks.entities().locked().size()
            + locks.enchants().locked().size()
            + locks.crops().locked().size()
            + locks.screens().locked().size()
            + locks.loot().locked().size()
            + locks.petsTaming().locked().size()
            + locks.petsBreeding().locked().size()
            + locks.mobSpawns().locked().size()
            + locks.recipeIds().locked().size()
            + locks.recipeOutputs().locked().size()
            + locks.lockedDimensions().size()
            + locks.interactions().size()
            + locks.mobReplacements().size()
            + locks.regions().size();

        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdInfoTotalLocks().replace("{count}", String.valueOf(lockCount))), false);

        return 1;
    }

    /**
     * /stage tree - Shows dependency tree (v1.3)
     * Displays all stages with their dependencies in a tree format.
     */
    private static int showDependencyTree(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdTreeHeader()), false);

        // Find root stages (no dependencies)
        List<StageId> rootStages = new java.util.ArrayList<>();
        for (StageId stageId : StageOrder.getInstance().getOrderedStages()) {
            Set<StageId> deps = StageOrder.getInstance().getDependencies(stageId);
            if (deps.isEmpty()) {
                rootStages.add(stageId);
            }
        }

        if (rootStages.isEmpty()) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdTreeEmpty()), false);
            return 1;
        }

        // Print tree starting from root stages
        Set<StageId> printed = new HashSet<>();
        for (StageId root : rootStages) {
            printStageTreeNode(context, root, 0, printed);
        }

        // Print any orphaned stages (have dependencies that don't exist)
        for (StageId stageId : StageOrder.getInstance().getOrderedStages()) {
            if (!printed.contains(stageId)) {
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdTreeOrphaned().replace("{path}", stageId.getPath())), false);
            }
        }

        return 1;
    }

    private static void printStageTreeNode(CommandContext<CommandSourceStack> context, StageId stageId, int depth, Set<StageId> printed) {
        if (printed.contains(stageId)) {
            return; // Already printed (avoid infinite loops)
        }
        printed.add(stageId);

        String indent = "  " + "│   ".repeat(Math.max(0, depth - 1)) + (depth > 0 ? "├── " : "");
        String displayName = StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::getDisplayName)
            .orElse(stageId.getPath());

        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdTreeNode()
                .replace("{indent}", indent)
                .replace("{name}", displayName)
                .replace("{path}", stageId.getPath())), false);

        // Print stages that depend on this one
        Set<StageId> dependents = StageOrder.getInstance().getDependents(stageId);
        for (StageId dependent : dependents) {
            printStageTreeNode(context, dependent, depth + 1, printed);
        }
    }

    private static int reloadStages(CommandContext<CommandSourceStack> context) {
        StageFileLoader.getInstance().reload();

        // Reload trigger config
        com.enviouse.progressivestages.server.triggers.TriggerConfigLoader.reload();

        // Re-sync all online players with updated lock data and stage definitions
        var server = context.getSource().getServer();
        int syncedPlayers = 0;
        for (var player : server.getPlayerList().getPlayers()) {
            com.enviouse.progressivestages.common.network.NetworkHandler.sendStageDefinitionsSync(player);
            var stages = StageManager.getInstance().getStages(player);
            com.enviouse.progressivestages.common.network.NetworkHandler.sendStageSync(player, stages);
            com.enviouse.progressivestages.common.network.NetworkHandler.sendLockSync(player);
            syncedPlayers++;
        }

        final int finalSyncedPlayers = syncedPlayers;
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdReloadSuccess()
                .replace("{count}", String.valueOf(finalSyncedPlayers))), true);

        return 1;
    }

    private static int validateStages(CommandContext<CommandSourceStack> context) {
        var loader = StageFileLoader.getInstance();
        var stages = loader.getAllStages();

        int totalFiles = loader.countStageFiles();
        int loadedCount = stages.size();
        int syntaxErrors = 0;
        int validationErrors = 0;
        int warnings = 0;

        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdValidateHeader()), false);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdValidateStarting().replace("{prefix}", StageConfig.getMsgPrefix())), false);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdValidateFound().replace("{count}", String.valueOf(totalFiles))), false);

        // Validate all files with detailed error reporting
        var validationResults = loader.validateAllStages();

        for (var result : validationResults) {
            if (result.success) {
                final String fname = result.fileName;
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdValidateSuccess().replace("{file}", fname)), false);
            } else if (result.syntaxError) {
                syntaxErrors++;
                final String fname = result.fileName;
                final String errorMsg = result.errorMessage;
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdValidateSyntaxError()
                        .replace("{file}", fname)
                        .replace("{error}", errorMsg)), false);
            } else {
                validationErrors++;
                final String fname = result.fileName;
                final String errorMsg = result.errorMessage;
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdValidateValidationError()
                        .replace("{file}", fname)
                        .replace("{error}", errorMsg)), false);

                // List invalid items
                for (String invalidItem : result.invalidItems) {
                    context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                        StageConfig.getMsgCmdValidateInvalidItem().replace("{item}", invalidItem)), false);
                }
            }
        }

        // Check for order conflicts among loaded stages
        // v1.3: Check for dependency issues instead of order conflicts
        List<String> depErrors = StageOrder.getInstance().validateDependencies();
        for (String depError : depErrors) {
            warnings++;
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdValidateDepWarning().replace("{message}", depError)), false);
        }

        // Check for empty starting stages
        List<String> startingStages = com.enviouse.progressivestages.common.config.StageConfig.getStartingStages();
        for (String startingStage : startingStages) {
            if (startingStage == null || startingStage.isEmpty()) continue;
            StageId startId = StageId.of(startingStage);
            boolean found = stages.stream().anyMatch(s -> s.getId().equals(startId));
            if (!found) {
                validationErrors++;
                final String stageName = startingStage;
                context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdValidateStartingNotFound().replace("{stage}", stageName)), false);
            }
        }

        // Summary
        final int finalSyntaxErrors = syntaxErrors;
        final int finalValidationErrors = validationErrors;
        final int finalWarnings = warnings;
        final int totalErrors = syntaxErrors + validationErrors;
        final int validCount = validationResults.size() - totalErrors;

        final int total = validationResults.size();
        if (totalErrors == 0 && warnings == 0) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdValidateSummaryOk()
                    .replace("{valid}", String.valueOf(validCount))
                    .replace("{total}", String.valueOf(total))), false);
        } else {
            String errorsPart = (finalSyntaxErrors > 0 ? finalSyntaxErrors + " syntax error(s), " : "")
                + (finalValidationErrors > 0 ? finalValidationErrors + " validation error(s), " : "");
            String warningsPart = finalWarnings > 0 ? finalWarnings + " warning(s)" : "";
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdValidateSummaryErrors()
                    .replace("{valid}", String.valueOf(validCount))
                    .replace("{total}", String.valueOf(total))
                    .replace("{errors_part}", errorsPart)
                    .replace("{warnings_part}", warningsPart)), false);
        }

        return totalErrors == 0 ? 1 : 0;
    }

    /**
     * /progressivestages ftb status [player]
     * Shows FTB Quests integration status for debugging.
     */
    private static int ftbStatus(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> TextUtil.parseColorCodes(StageConfig.getMsgCmdFtbStatusHeader()), false);

        boolean enabled = StageConfig.isFtbQuestsIntegrationEnabled();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusConfigEnabled().replace("{value}", enabled ? "YES" : "NO")), false);

        boolean providerRegistered = FtbQuestsHooks.isProviderRegistered();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusProviderRegistered().replace("{value}", providerRegistered ? "YES" : "NO")), false);

        boolean compatActive = FTBQuestsCompat.isEnabled();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusCompatActive().replace("{value}", compatActive ? "YES" : "NO")), false);

        int pendingCount = FTBQuestsCompat.getPendingCount();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusPendingRechecks().replace("{value}", String.valueOf(pendingCount))), false);

        int budget = StageConfig.getFtbRecheckBudget();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusRecheckBudget().replace("{value}", String.valueOf(budget))), false);

        boolean hasPrevious = FtbQuestsHooks.hasPreviousProvider();
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdFtbStatusPreviousProvider().replace("{value}", hasPrevious ? "YES" : "NO")), false);

        if (player != null) {
            String pname = player.getName().getString();
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdFtbStatusPlayerHeader().replace("{player}", pname)), false);

            Set<StageId> stages = StageManager.getInstance().getStages(player);
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdFtbStatusPlayerStages().replace("{value}", String.valueOf(stages.size()))), false);

            if (!stages.isEmpty()) {
                StringBuilder stageList = new StringBuilder();
                for (StageId stage : stages) {
                    if (stageList.length() > 0) stageList.append(", ");
                    stageList.append(stage.getPath());
                }
                final String list = stageList.toString();
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    StageConfig.getMsgCmdFtbStatusPlayerStageList().replace("{list}", list)), false);
            }

            boolean recheckInProgress = FtbQuestsHooks.isRecheckInProgress(player.getUUID());
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                StageConfig.getMsgCmdFtbStatusRecheckInProgress().replace("{value}", recheckInProgress ? "YES" : "NO")), false);
        }

        return 1;
    }

    /**
     * /progressivestages trigger reset <player> <type> <key>
     * Resets a one-time trigger for a player. Supports {@code dimension}, {@code boss},
     * and {@code multi} (where {@code key} is the multi-requirement id).
     */
    private static int resetTrigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String type = StringArgumentType.getString(context, "type");
        String key = StringArgumentType.getString(context, "key");

        // Validate type
        if (!type.equals("dimension") && !type.equals("boss") && !type.equals("multi")) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdTriggerInvalidType().replace("{type}", type)));
            return 0;
        }

        if (type.equals("multi")) {
            // Clear every sub-key of the named requirement for this player.
            com.enviouse.progressivestages.server.triggers.MultiTriggerManager
                .resetForPlayer(player, key);
        } else {
            TriggerPersistence persistence = TriggerPersistence.get(context.getSource().getServer());
            persistence.clearTrigger(type, key, player.getUUID());
        }

        String playerName = player.getName().getString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdTriggerReset()
                .replace("{type}", type)
                .replace("{key}", key)
                .replace("{player}", playerName)), true);

        return 1;
    }

    /**
     * /progressivestages multi list [player]
     *
     * <p>Lists every loaded {@code [[multi]]} requirement with its stage, mode, and
     * sub-trigger list. When a player is given, also reports the player's progress
     * (e.g. "2/3 sub-triggers satisfied"). Useful for diagnosing why a player hasn't
     * received a multi-stage yet.
     */
    private static int listMultiRequirements(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        var reqs = com.enviouse.progressivestages.server.triggers.MultiTriggerManager.getAll();
        CommandSourceStack source = context.getSource();

        if (reqs.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&7No multi-trigger requirements loaded. Add &f[[multi]]&7 entries to &ftriggers.toml&7."), false);
            return 0;
        }

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&6=== Multi-Trigger Requirements (" + reqs.size() + ") ==="), false);

        for (var req : reqs) {
            String header = "&e" + req.requirementId() + " &7-> &f" + req.stageId().toString()
                + " &8(" + req.mode().name().toLowerCase() + ")";
            if (player != null) {
                int total = req.subTriggers().size();
                int done = com.enviouse.progressivestages.server.triggers.MultiTriggerManager
                    .countSatisfied(req, player);
                boolean has = StageManager.getInstance().hasStage(player, req.stageId());
                String progress = has ? "&aGRANTED" : ("&7[" + done + "/" + total + "]");
                header = header + " " + progress;
            }
            final String headerFinal = header;
            source.sendSuccess(() -> TextUtil.parseColorCodes(headerFinal), false);

            for (var sub : req.subTriggers()) {
                String prefix = "    &8• &f" + sub.canonicalKey();
                if (player != null) {
                    TriggerPersistence persistence = TriggerPersistence.get(source.getServer());
                    boolean ok = persistence.hasTriggered("multi",
                        req.requirementId() + ":" + sub.canonicalKey(), player.getUUID());
                    prefix = prefix + " " + (ok ? "&a✓" : "&c✗");
                }
                final String line = prefix;
                source.sendSuccess(() -> TextUtil.parseColorCodes(line), false);
            }
        }

        return 1;
    }

    /**
     * /stage progress &lt;stage&gt; [player]
     *
     * <p>Per-stage breakdown for one player: missing dependencies and the
     * satisfaction state of every trigger (advancement / item / dimension / boss /
     * multi-requirement) that grants the given stage. Designed for players and
     * pack authors to see at a glance what's left to unlock the stage —
     * complement to {@code /stage check} (yes/no) and {@code /stage list}
     * (one-line summary per stage).
     */
    private static int showProgress(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }

        ServerPlayer player = resolvePlayer(context, target);
        if (player == null) return 0;

        renderStageProgress(context.getSource(), player, stageId, true);
        return 1;
    }

    /**
     * /stage progress next [player]
     *
     * <p>Lists every stage that is currently <em>reachable</em> for the player —
     * i.e. the player doesn't have it yet, but every declared dependency is
     * satisfied — and renders the full per-trigger breakdown for each one.
     *
     * <p>This is the "what should I do next?" view. Stages whose dependencies
     * are themselves locked are intentionally hidden (use {@code /stage tree}
     * or {@code /stage progress all} to see them).
     *
     * <p>If the player has no next stages but does not own every stage, the
     * remaining stages are gated behind locked prerequisites — surfaced as a
     * hint so players know to consult the tree.
     */
    private static int showProgressNext(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(context, target);
        if (player == null) return 0;

        CommandSourceStack source = context.getSource();
        Set<StageId> owned = StageManager.getInstance().getStages(player);
        int total = StageOrder.getInstance().getStageCount();
        List<StageId> nextStages = findNextStages(player, owned);

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&6=== Next Stages for &f" + player.getName().getString()
                + " &7(" + owned.size() + "/" + total + " unlocked) &6==="), false);

        if (nextStages.isEmpty()) {
            if (owned.size() >= total && total > 0) {
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "&aAll stages unlocked! &7Nothing left to progress."), false);
            } else {
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "&8No reachable next stages. &7Remaining stages are gated by locked"
                        + " dependencies — see &f/stage tree&7."), false);
            }
            return 1;
        }

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Showing &f" + nextStages.size() + "&7 reachable stage(s):"), false);

        boolean first = true;
        for (StageId id : nextStages) {
            if (!first) {
                source.sendSuccess(() -> TextUtil.parseColorCodes("&8──────────"), false);
            }
            first = false;
            renderStageProgress(source, player, id, false);
        }
        return 1;
    }

    /**
     * /stage progress all [player]
     *
     * <p>Renders progress for every stage the player doesn't yet have, in
     * registration order — including stages still locked behind unmet
     * dependencies. Useful for pack authors auditing a full progression
     * tree, or for players who want the complete roadmap rather than just
     * what's immediately reachable.
     */
    private static int showProgressAll(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(context, target);
        if (player == null) return 0;

        CommandSourceStack source = context.getSource();
        Set<StageId> owned = StageManager.getInstance().getStages(player);
        int total = StageOrder.getInstance().getStageCount();

        List<StageId> remaining = new java.util.ArrayList<>();
        for (StageId id : StageOrder.getInstance().getOrderedStages()) {
            if (!owned.contains(id)) remaining.add(id);
        }

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&6=== All Remaining Stages for &f" + player.getName().getString()
                + " &7(" + owned.size() + "/" + total + " unlocked) &6==="), false);

        if (remaining.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&aAll stages unlocked!"), false);
            return 1;
        }

        boolean first = true;
        for (StageId id : remaining) {
            if (!first) {
                source.sendSuccess(() -> TextUtil.parseColorCodes("&8──────────"), false);
            }
            first = false;
            renderStageProgress(source, player, id, false);
        }
        return 1;
    }

    /**
     * Resolve the target player: the explicit {@code target} arg if non-null,
     * otherwise the command sender if they are a player. Reports the standard
     * "specify player" failure and returns null when neither is available
     * (e.g. console with no target).
     */
    private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (target != null) return target;
        if (context.getSource().getEntity() instanceof ServerPlayer sp) return sp;
        context.getSource().sendFailure(TextUtil.parseColorCodes(
            StageConfig.getMsgCmdSpecifyPlayer()));
        return null;
    }

    /**
     * The set of stages a player can <em>currently</em> unlock: they don't have
     * the stage yet, and every declared dependency is satisfied. Order matches
     * {@link StageOrder#getOrderedStages()} so the output is deterministic.
     */
    private static List<StageId> findNextStages(ServerPlayer player, Set<StageId> owned) {
        List<StageId> result = new java.util.ArrayList<>();
        for (StageId id : StageOrder.getInstance().getOrderedStages()) {
            if (owned.contains(id)) continue;
            List<StageId> missing = StageManager.getInstance().getMissingDependencies(player, id);
            if (missing.isEmpty()) result.add(id);
        }
        return result;
    }

    /**
     * Render the full progress block for a single stage. Used by every
     * {@code /stage progress *} variant so the formatting stays consistent.
     *
     * @param verbose when true, prints the "no triggers grant this stage" hint
     *                if the stage has no automatic trigger; off in list views
     *                where that line would be noisy on every stage.
     */
    private static void renderStageProgress(CommandSourceStack source, ServerPlayer player,
                                            StageId stageId, boolean verbose) {
        String displayName = StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::getDisplayName)
            .orElse(stageId.getPath());
        boolean hasStage = StageManager.getInstance().hasStage(player, stageId);

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&6=== Progress: &f" + displayName + " &6for &f" + player.getName().getString() + " &6==="), false);
        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Stage: &f" + stageId.toString() + " &7| Status: " + (hasStage ? "&aGRANTED" : "&cNOT GRANTED")), false);

        // ── Description (only when present; helps players understand the stage) ──
        String description = StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::getDescription).orElse("");
        if (description != null && !description.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&8\"&7" + description + "&8\""), false);
        }

        // ── Dependencies ──
        List<StageId> deps = StageOrder.getInstance().getStageDefinition(stageId)
            .map(StageDefinition::getDependencies).orElse(java.util.Collections.emptyList());
        if (!deps.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes("&7Dependencies:"), false);
            Set<StageId> owned = StageManager.getInstance().getStages(player);
            for (StageId dep : deps) {
                boolean has = owned.contains(dep);
                String mark = has ? "&a✓" : "&c✗";
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "  " + mark + " &f" + dep.getPath()), false);
            }
        }

        // ── Single-trigger surfaces (any one grants the stage) ──
        List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>> advMatches =
            collectMatching(com.enviouse.progressivestages.server.triggers.AdvancementStageGrants.getAllMappings(), stageId);
        List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>> itemMatches =
            collectMatching(com.enviouse.progressivestages.server.triggers.ItemPickupStageGrants.getAllMappings(), stageId);
        List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>> dimMatches =
            collectMatching(com.enviouse.progressivestages.server.triggers.DimensionStageGrants.getAllMappings(), stageId);
        List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>> bossMatches =
            collectMatching(com.enviouse.progressivestages.server.triggers.BossKillStageGrants.getAllMappings(), stageId);

        boolean anySingle = !advMatches.isEmpty() || !itemMatches.isEmpty()
            || !dimMatches.isEmpty() || !bossMatches.isEmpty();

        if (anySingle) {
            source.sendSuccess(() -> TextUtil.parseColorCodes("&7Single triggers (any one grants):"), false);
            TriggerPersistence persistence = TriggerPersistence.get(source.getServer());

            for (var e : advMatches) {
                net.minecraft.advancements.AdvancementHolder holder =
                    source.getServer().getAdvancements().get(e.getKey());
                boolean done = holder != null
                    && player.getAdvancements().getOrStartProgress(holder).isDone();
                String mark = done ? "&a✓" : "&c✗";
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "  " + mark + " &8advancement: &f" + e.getKey()), false);
            }
            for (var e : itemMatches) {
                // Item-pickup triggers are stateless (no persistence). Best-effort: report whether
                // the item is currently in the player's inventory.
                boolean inInv = playerHasItem(player, e.getKey());
                String mark = inInv ? "&a✓ (in inventory)" : "&c✗";
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "  " + mark + " &8item: &f" + e.getKey()), false);
            }
            for (var e : dimMatches) {
                boolean fired = persistence.hasTriggered("dimension", e.getKey().toString(), player.getUUID());
                String mark = fired ? "&a✓" : "&c✗";
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "  " + mark + " &8dimension: &f" + e.getKey()), false);
            }
            for (var e : bossMatches) {
                boolean fired = persistence.hasTriggered("boss", e.getKey().toString(), player.getUUID());
                String mark = fired ? "&a✓" : "&c✗";
                source.sendSuccess(() -> TextUtil.parseColorCodes(
                    "  " + mark + " &8boss: &f" + e.getKey()), false);
            }
        }

        // ── Multi-requirements targeting this stage ──
        List<com.enviouse.progressivestages.server.triggers.MultiTrigger> multis =
            com.enviouse.progressivestages.server.triggers.MultiTriggerManager.getAll()
                .stream()
                .filter(req -> req.stageId().equals(stageId))
                .toList();

        if (!multis.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes("&7Multi requirements:"), false);
            TriggerPersistence persistence = TriggerPersistence.get(source.getServer());
            for (var req : multis) {
                int total = req.subTriggers().size();
                int done = com.enviouse.progressivestages.server.triggers.MultiTriggerManager
                    .countSatisfied(req, player);
                String header = "  &e" + req.requirementId()
                    + " &8(" + req.mode().name().toLowerCase() + ") &7[" + done + "/" + total + "]";
                source.sendSuccess(() -> TextUtil.parseColorCodes(header), false);
                for (var sub : req.subTriggers()) {
                    boolean ok = persistence.hasTriggered("multi",
                        req.requirementId() + ":" + sub.canonicalKey(), player.getUUID());
                    String mark = ok ? "&a✓" : "&c✗";
                    source.sendSuccess(() -> TextUtil.parseColorCodes(
                        "      " + mark + " &f" + sub.canonicalKey()), false);
                }
            }
        }

        if (!anySingle && multis.isEmpty() && !hasStage && verbose) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&8No triggers grant this stage — only &f/stage grant&8 will award it."), false);
        }
    }

    private static List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>>
        collectMatching(Map<net.minecraft.resources.ResourceLocation, StageId> mappings, StageId target) {
        List<Map.Entry<net.minecraft.resources.ResourceLocation, StageId>> out = new java.util.ArrayList<>();
        for (var e : mappings.entrySet()) {
            if (e.getValue().equals(target)) out.add(e);
        }
        return out;
    }

    private static boolean playerHasItem(ServerPlayer player, net.minecraft.resources.ResourceLocation itemId) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null && key.equals(itemId)) return true;
        }
        return false;
    }
}
