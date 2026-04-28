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
        );

        // /progressivestages subcommands
        dispatcher.register(Commands.literal("progressivestages")
            .requires(source -> source.hasPermission(3))

            .then(Commands.literal("reload")
                .executes(StageCommand::reloadStages))

            .then(Commands.literal("validate")
                .executes(StageCommand::validateStages))

            // /progressivestages ftb status [player]
            .then(Commands.literal("ftb")
                .then(Commands.literal("status")
                    .executes(ctx -> ftbStatus(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> ftbStatus(ctx, EntityArgument.getPlayer(ctx, "player"))))))

            // /progressivestages trigger reset <player> <type> <key>
            .then(Commands.literal("trigger")
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("type", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("dimension");
                                builder.suggest("boss");
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(StageCommand::resetTrigger))))))
        );
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
     * Resets a one-time trigger for a player.
     */
    private static int resetTrigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String type = StringArgumentType.getString(context, "type");
        String key = StringArgumentType.getString(context, "key");

        // Validate type
        if (!type.equals("dimension") && !type.equals("boss")) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdTriggerInvalidType().replace("{type}", type)));
            return 0;
        }

        // Get persistence and clear trigger
        TriggerPersistence persistence = TriggerPersistence.get(context.getSource().getServer());
        persistence.clearTrigger(type, key, player.getUUID());

        String playerName = player.getName().getString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdTriggerReset()
                .replace("{type}", type)
                .replace("{key}", key)
                .replace("{player}", playerName)), true);

        return 1;
    }
}
