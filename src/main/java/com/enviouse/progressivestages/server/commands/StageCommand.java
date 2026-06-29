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

            // v3.0: /stage tag grant|revoke <players> <tag>  +  /stage tag list <tag>
            .then(Commands.literal("tag")
                .then(Commands.literal("grant")
                    .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("tag", StringArgumentType.word())
                            .suggests(StageCommand::suggestTags)
                            .executes(ctx -> tagBulk(ctx, true)))))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("tag", StringArgumentType.word())
                            .suggests(StageCommand::suggestTags)
                            .executes(ctx -> tagBulk(ctx, false)))))
                .then(Commands.literal("list")
                    .then(Commands.argument("tag", StringArgumentType.word())
                        .suggests(StageCommand::suggestTags)
                        .executes(StageCommand::tagList))))

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

            // /stage gui — open the in-game stage-tree viewer for the calling player
            .then(Commands.literal("gui")
                .executes(StageCommand::openGui))
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

            // /progressivestages trigger reset <player> <stage>
            // Clears the persisted one-shot trigger progress (visited dimension/biome) that a
            // stage's [[triggers]] rules accumulated for a player, so they can be re-tested.
            .then(Commands.literal("trigger")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("stage", StringArgumentType.word())
                            .suggests(StageCommand::suggestStages)
                            .executes(StageCommand::resetTrigger)))))

            // /progressivestages triggers list [player]
            // Lists every stage that declares [[triggers]] rules and (optionally) a player's
            // live progress toward each condition.
            .then(Commands.literal("triggers")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("list")
                    .executes(ctx -> listStageTriggers(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> listStageTriggers(ctx,
                            EntityArgument.getPlayer(ctx, "player"))))))

            // /progressivestages no-creative-popup — toggle the creative-mode bypass
            // warning for the calling player. Open to everyone (per-player preference).
            .then(Commands.literal("no-creative-popup")
                .executes(StageCommand::toggleCreativePopup))
        );
    }

    /** /stage gui — push the player's live trigger progress, which opens the GUI client-side. */
    private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        com.enviouse.progressivestages.common.network.NetworkHandler.sendStageGuiData(player);
        return 1;
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

    // ---------------------------- v3.0 tag bulk ops ----------------------------

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTags(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.Set<String> tags = new java.util.TreeSet<>();
        for (StageId id : StageOrder.getInstance().getAllStageIds()) {
            StageOrder.getInstance().getStageDefinition(id).ifPresent(d -> tags.addAll(d.getTags()));
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(tags, builder);
    }

    private static int tagBulk(CommandContext<CommandSourceStack> context, boolean grant) throws CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        String tag = StringArgumentType.getString(context, "tag");
        List<StageId> stages = StageOrder.getInstance().getStagesWithTag(tag);
        if (stages.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cNo stages declare the tag '&f" + tag + "&c'."));
            return 0;
        }
        var cause = com.enviouse.progressivestages.common.api.StageCause.COMMAND;
        int changes = 0;
        for (ServerPlayer p : players) {
            for (StageId s : stages) {
                boolean has = StageManager.getInstance().hasStage(p, s);
                if (grant && !has) {
                    StageManager.getInstance().grantStageBypassDependencies(p, s, cause);
                    changes++;
                } else if (!grant && has) {
                    StageManager.getInstance().revokeStageWithCause(p, s, cause);
                    changes++;
                }
            }
        }
        final int ch = changes, ns = stages.size(), np = players.size();
        final String verb = grant ? "Granted" : "Revoked";
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&a" + verb + " tag '&f" + tag + "&a' (" + ns + " stage(s)) — &f" + ch
                + "&a change(s) across &f" + np + "&a player(s)."), true);
        return changes;
    }

    private static int tagList(CommandContext<CommandSourceStack> context) {
        String tag = StringArgumentType.getString(context, "tag");
        List<StageId> stages = StageOrder.getInstance().getStagesWithTag(tag);
        if (stages.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cNo stages declare the tag '&f" + tag + "&c'."));
            return 0;
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Stages tagged '&f" + tag + "&7' (" + stages.size() + "):"), false);
        for (StageId s : stages) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes("  &8• &f" + s), false);
        }
        return stages.size();
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
        // StageFileLoader.reload() also rebuilds the per-stage [[triggers]] registry (v2.3).
        StageFileLoader.getInstance().reload();

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
     * /progressivestages trigger reset <player> <stage>
     *
     * <p>Clears the persisted one-shot trigger progress (visited dimension / biome) that a
     * stage's {@code [[triggers]]} rules accumulated for a player, so those conditions can be
     * re-tested. Counter conditions (kills, distance, …) read live vanilla statistics and are
     * not stored by this mod, so they are unaffected.
     */
    private static int resetTrigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.of(stageName);

        if (!StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                StageConfig.getMsgCmdStageNotFound().replace("{stage}", stageName)));
            return 0;
        }

        com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.resetStageFor(player, stageId);

        String playerName = player.getName().getString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            StageConfig.getMsgCmdTriggerReset()
                .replace("{type}", "stage")
                .replace("{key}", stageId.toString())
                .replace("{player}", playerName)), true);

        return 1;
    }

    /**
     * /progressivestages triggers list [player]
     *
     * <p>Lists every stage that declares {@code [[triggers]]} rules. With a player argument,
     * also renders that player's live progress toward each condition — the quick way to see
     * why an auto-grant hasn't fired yet.
     */
    private static int listStageTriggers(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        Set<StageId> stages = com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.stagesWithTriggers();

        if (stages.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&7No stages declare &f[[triggers]]&7 rules yet. Add a &f[[triggers]]&7 section to a stage file."), false);
            return 0;
        }

        source.sendSuccess(() -> TextUtil.parseColorCodes(
            "&6=== Stage Triggers (" + stages.size() + " stage(s)) ==="), false);

        for (StageId stageId : stages) {
            String displayName = StageOrder.getInstance().getStageDefinition(stageId)
                .map(StageDefinition::getDisplayName).orElse(stageId.getPath());
            String suffix = "";
            if (player != null && StageManager.getInstance().hasStage(player, stageId)) {
                suffix = " &aGRANTED";
            }
            final String header = "&e" + stageId.toString() + " &7(&f" + displayName + "&7)" + suffix;
            source.sendSuccess(() -> TextUtil.parseColorCodes(header), false);

            if (player != null) {
                renderTriggerRules(source, player, stageId, "    ");
            } else {
                int idx = 1;
                for (var rule : com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.rulesFor(stageId)) {
                    final int ri = idx++;
                    final String line = "    &8Rule " + ri + " &7(" + rule.mode().name().toLowerCase()
                        + "): &f" + describeConditions(rule.conditions());
                    source.sendSuccess(() -> TextUtil.parseColorCodes(line), false);
                }
            }
        }
        return 1;
    }

    /** Render every {@code [[triggers]]} rule of a stage with live per-condition progress. */
    private static void renderTriggerRules(CommandSourceStack source, ServerPlayer player,
                                           StageId stageId, String indent) {
        var rules = com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator
            .describeProgress(player, stageId);
        int idx = 1;
        for (var rule : rules) {
            final int ri = idx++;
            String ruleMark = rule.satisfied() ? "&a✓" : "&7…";
            String desc = rule.description().isEmpty() ? "" : " &8— &7" + rule.description();
            final String header = indent + ruleMark + " &8Rule " + ri + " &7("
                + rule.mode().name().toLowerCase() + ")" + desc;
            source.sendSuccess(() -> TextUtil.parseColorCodes(header), false);

            for (var cp : rule.conditions()) {
                String cmark = cp.satisfied() ? "&a✓" : "&c✗";
                long shown = Math.min(cp.current(), cp.threshold());
                final String line = indent + "  " + cmark + " &f"
                    + formatCondition(cp.condition()) + " &7[" + shown + "/" + cp.threshold() + "]";
                source.sendSuccess(() -> TextUtil.parseColorCodes(line), false);
            }
        }
    }

    private static String describeConditions(java.util.List<com.enviouse.progressivestages.common.trigger.TriggerCondition> conditions) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (var c : conditions) parts.add(formatCondition(c));
        return String.join("&7, &f", parts);
    }

    private static String formatCondition(com.enviouse.progressivestages.common.trigger.TriggerCondition c) {
        String type = c.type().name().toLowerCase().replace('_', ' ');
        String body = c.target().isEmpty() ? type : (type + " " + c.target());
        if (!c.with().isEmpty()) body = body + " with " + c.with();
        return c.count() > 1 ? (body + " x" + c.count()) : body;
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

        // ── v2.3 per-stage [[triggers]] (any satisfied rule grants the stage) ──
        var ruleProgress = com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator
            .describeProgress(player, stageId);
        if (!ruleProgress.isEmpty()) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&7Triggers (any satisfied rule grants this stage):"), false);
            renderTriggerRules(source, player, stageId, "  ");
        } else if (!hasStage && verbose) {
            source.sendSuccess(() -> TextUtil.parseColorCodes(
                "&8No triggers grant this stage — only &f/stage grant&8 will award it."), false);
        }
    }
}
