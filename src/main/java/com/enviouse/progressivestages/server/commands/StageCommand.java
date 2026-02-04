package com.enviouse.progressivestages.server.commands;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Stage management commands: /stage grant, revoke, list, check
 */
public class StageCommand {

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
        );

        // /progressivestages reload
        dispatcher.register(Commands.literal("progressivestages")
            .requires(source -> source.hasPermission(3))

            .then(Commands.literal("reload")
                .executes(StageCommand::reloadStages))

            .then(Commands.literal("validate")
                .executes(StageCommand::validateStages))
        );
    }

    private static CompletableFuture<Suggestions> suggestStages(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (StageId stageId : StageOrder.getInstance().getAllStageIds()) {
            builder.suggest(stageId.getPath());
        }
        return builder.buildFuture();
    }

    private static int grantStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(Component.literal("Stage not found: " + stageName)
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        StageManager.getInstance().grantStage(player, stageId);

        context.getSource().sendSuccess(() -> Component.literal("Granted stage ")
            .append(Component.literal(stageName).withStyle(ChatFormatting.GREEN))
            .append(" to ")
            .append(player.getDisplayName()), true);

        return 1;
    }

    private static int revokeStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(Component.literal("Stage not found: " + stageName)
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        StageManager.getInstance().revokeStage(player, stageId);

        context.getSource().sendSuccess(() -> Component.literal("Revoked stage ")
            .append(Component.literal(stageName).withStyle(ChatFormatting.RED))
            .append(" from ")
            .append(player.getDisplayName()), true);

        return 1;
    }

    private static int listStages(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
        final ServerPlayer player;
        if (target != null) {
            player = target;
        } else if (context.getSource().getEntity() instanceof ServerPlayer sp) {
            player = sp;
        } else {
            context.getSource().sendFailure(Component.literal("Specify a player"));
            return 0;
        }

        Set<StageId> stages = StageManager.getInstance().getStages(player);
        String progress = StageManager.getInstance().getProgressString(player);

        context.getSource().sendSuccess(() -> Component.literal("=== Stages for ")
            .append(player.getDisplayName())
            .append(" (" + progress + ") ===")
            .withStyle(ChatFormatting.GOLD), false);

        if (stages.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  No stages unlocked")
                .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (StageId stageId : StageOrder.getInstance().getOrderedStages()) {
                boolean has = stages.contains(stageId);
                Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);
                String displayName = defOpt.map(StageDefinition::getDisplayName).orElse(stageId.getPath());
                int order = defOpt.map(StageDefinition::getOrder).orElse(0);

                context.getSource().sendSuccess(() -> Component.literal("  " + order + ". ")
                    .append(Component.literal(displayName)
                        .withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(has ? " ✓" : ""), false);
            }
        }

        return 1;
    }

    private static int checkStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(Component.literal("Stage not found: " + stageName)
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean has = StageManager.getInstance().hasStage(player, stageId);

        context.getSource().sendSuccess(() -> Component.empty()
            .append(player.getDisplayName())
            .append(has ? " has " : " does not have ")
            .append(Component.literal(stageName).withStyle(has ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

        return has ? 1 : 0;
    }

    private static int stageInfo(CommandContext<CommandSourceStack> context) {
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        Optional<StageDefinition> defOpt = StageOrder.getInstance().getStageDefinition(stageId);

        if (defOpt.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Stage not found: " + stageName)
                .withStyle(ChatFormatting.RED));
            return 0;
        }

        StageDefinition def = defOpt.get();

        context.getSource().sendSuccess(() -> Component.literal("=== " + def.getDisplayName() + " ===")
            .withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("  ID: " + def.getId().toString())
            .withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(() -> Component.literal("  Order: " + def.getOrder())
            .withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(() -> Component.literal("  Description: " + def.getDescription())
            .withStyle(ChatFormatting.GRAY), false);

        var locks = def.getLocks();
        int lockCount = locks.getItems().size() + locks.getRecipes().size() +
            locks.getBlocks().size() + locks.getDimensions().size() +
            locks.getMods().size() + locks.getNames().size();

        context.getSource().sendSuccess(() -> Component.literal("  Total locks: " + lockCount)
            .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int reloadStages(CommandContext<CommandSourceStack> context) {
        StageFileLoader.getInstance().reload();

        // Re-sync all online players
        var server = context.getSource().getServer();
        int syncedPlayers = 0;
        for (var player : server.getPlayerList().getPlayers()) {
            var stages = StageManager.getInstance().getStages(player);
            com.enviouse.progressivestages.common.network.NetworkHandler.sendStageSync(player, stages);
            // Also sync lock registry
            com.enviouse.progressivestages.common.network.NetworkHandler.sendLockSync(player);
            syncedPlayers++;
        }

        final int finalSyncedPlayers = syncedPlayers;
        context.getSource().sendSuccess(() -> Component.literal("Reloaded stage definitions, synced " + finalSyncedPlayers + " players")
            .withStyle(ChatFormatting.GREEN), true);

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

        context.getSource().sendSuccess(() -> Component.literal("=== Stage Validation ===")
            .withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("[ProgressiveStages] Validating stage files...")
            .withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(() -> Component.literal("  Found " + totalFiles + " stage files")
            .withStyle(ChatFormatting.GRAY), false);

        // Validate all files with detailed error reporting
        var validationResults = loader.validateAllStages();

        for (var result : validationResults) {
            if (result.success) {
                final String fname = result.fileName;
                context.getSource().sendSuccess(() -> Component.literal("  SUCCESS: " + fname + " validated")
                    .withStyle(ChatFormatting.GREEN), false);
            } else if (result.syntaxError) {
                syntaxErrors++;
                final String fname = result.fileName;
                final String errorMsg = result.errorMessage;
                context.getSource().sendSuccess(() -> Component.literal("  ERROR: " + fname + " has " + errorMsg)
                    .withStyle(ChatFormatting.RED), false);
            } else {
                validationErrors++;
                final String fname = result.fileName;
                final String errorMsg = result.errorMessage;
                context.getSource().sendSuccess(() -> Component.literal("  ERROR: " + fname + " - " + errorMsg)
                    .withStyle(ChatFormatting.RED), false);

                // List invalid items
                for (String invalidItem : result.invalidItems) {
                    context.getSource().sendSuccess(() -> Component.literal("      - " + invalidItem)
                        .withStyle(ChatFormatting.YELLOW), false);
                }
            }
        }

        // Check for order conflicts among loaded stages
        java.util.Map<Integer, java.util.List<StageId>> orderMap = new java.util.HashMap<>();
        for (StageDefinition stage : stages) {
            orderMap.computeIfAbsent(stage.getOrder(), k -> new java.util.ArrayList<>()).add(stage.getId());
        }

        for (var entry : orderMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                warnings++;
                final int order = entry.getKey();
                final var ids = entry.getValue();
                context.getSource().sendSuccess(() -> Component.literal("  ⚠ Duplicate order " + order + ": " + ids)
                    .withStyle(ChatFormatting.YELLOW), false);
            }
        }

        // Check for empty starting stage
        String startingStage = com.enviouse.progressivestages.common.config.StageConfig.getStartingStage();
        if (startingStage != null && !startingStage.isEmpty()) {
            StageId startId = StageId.of(startingStage);
            boolean found = stages.stream().anyMatch(s -> s.getId().equals(startId));
            if (!found) {
                validationErrors++;
                context.getSource().sendSuccess(() -> Component.literal("  ✗ Starting stage not found: " + startingStage)
                    .withStyle(ChatFormatting.RED), false);
            }
        }

        // Summary
        final int finalSyntaxErrors = syntaxErrors;
        final int finalValidationErrors = validationErrors;
        final int finalWarnings = warnings;
        final int totalErrors = syntaxErrors + validationErrors;
        final int validCount = validationResults.size() - totalErrors;

        if (totalErrors == 0 && warnings == 0) {
            context.getSource().sendSuccess(() -> Component.literal("  SUMMARY: " + validCount + "/" + validationResults.size() + " stage files valid, all passed!")
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  SUMMARY: " + validCount + "/" + validationResults.size() + " stage files valid, " +
                    (finalSyntaxErrors > 0 ? finalSyntaxErrors + " syntax error(s), " : "") +
                    (finalValidationErrors > 0 ? finalValidationErrors + " validation error(s), " : "") +
                    (finalWarnings > 0 ? finalWarnings + " warning(s)" : ""))
                .withStyle(totalErrors > 0 ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
        }

        return totalErrors == 0 ? 1 : 0;
    }
}
