package com.enviouse.progressivestages.server.commands;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.TextUtil;
import com.enviouse.progressivestages.compat.ftbquests.FTBQuestsCompat;
import com.enviouse.progressivestages.compat.ftbquests.FtbQuestsHooks;
import com.enviouse.progressivestages.server.enforcement.ConditionalLockEngine;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
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

import java.util.ArrayList;
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
        var stageRoot = dispatcher.register(Commands.literal("stage")
            .executes(StageCommand::openGui)

            // /stage grant <player> <stage>
            .then(Commands.literal("grant")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(StageCommand::suggestStages)
                        .executes(StageCommand::grantStage))))

            // /stage revoke <player> <stage>
            .then(Commands.literal("revoke")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests(StageCommand::suggestStages)
                        .executes(StageCommand::revokeStage))))

            // v3.0: /stage tag grant|revoke <players> <tag>  +  /stage tag list <tag>
            .then(Commands.literal("tag")
                .requires(source -> source.hasPermission(2))
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

            // v3.0: category bulk operations mirror tags but use the GUI category metadata.
            .then(Commands.literal("category")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant")
                    .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("category", StringArgumentType.string())
                            .suggests(StageCommand::suggestCategories)
                            .executes(ctx -> categoryBulk(ctx, true)))))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("category", StringArgumentType.string())
                            .suggests(StageCommand::suggestCategories)
                            .executes(ctx -> categoryBulk(ctx, false)))))
                .then(Commands.literal("list")
                    .then(Commands.argument("category", StringArgumentType.string())
                        .suggests(StageCommand::suggestCategories)
                        .executes(StageCommand::categoryList))))

            // Full-definition bulk operations and an explicit client-cache repair command.
            .then(Commands.literal("bulk")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant")
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> allStagesBulk(ctx, true))))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> allStagesBulk(ctx, false)))))
            .then(Commands.literal("sync")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(StageCommand::syncPlayers)))

            // v3.0: /stage simulate [player] — dry-run what they'd unlock next and what's short
            .then(Commands.literal("simulate")
                .executes(ctx -> simulate(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> simulate(ctx, EntityArgument.getPlayer(ctx, "player")))))

            // v3.0: /stage new <id> — scaffold a stage TOML file
            .then(Commands.literal("new")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(StageCommand::newStage)))

            // v3.0: /stage export — write a markdown progression guide
            .then(Commands.literal("export")
                .requires(source -> source.hasPermission(3))
                .executes(StageCommand::exportGuide))

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

            // v3.0 named counters: bridge commands, scripts, and declarative custom_counter triggers.
            .then(Commands.literal("counter")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("counter", StringArgumentType.word())
                            .executes(StageCommand::getCounter))))
                .then(Commands.literal("add")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("counter", StringArgumentType.word())
                            .then(Commands.argument("amount", LongArgumentType.longArg())
                                .executes(StageCommand::addCounter)))))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("counter", StringArgumentType.word())
                            .then(Commands.argument("value", LongArgumentType.longArg())
                                .executes(StageCommand::setCounter)))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("counter", StringArgumentType.word())
                            .executes(StageCommand::resetCounter)))))

            .then(Commands.literal("rule")
                .then(Commands.literal("list")
                    .executes(ctx -> listActiveRules(ctx, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> listActiveRules(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("info")
                    .then(Commands.argument("rule", StringArgumentType.word())
                        .suggests(StageCommand::suggestConditionalRules)
                        .executes(StageCommand::conditionalRuleInfo)))
                .then(Commands.literal("activate")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("rule", StringArgumentType.word())
                            .suggests(StageCommand::suggestConditionalRules)
                            .executes(ctx -> activateConditionalRule(ctx, 0L))
                            .then(Commands.argument("seconds", LongArgumentType.longArg(1L, 31_536_000L))
                                .executes(ctx -> activateConditionalRule(ctx,
                                    LongArgumentType.getLong(ctx, "seconds")))))))
                .then(Commands.literal("clear")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("rule", StringArgumentType.word())
                            .suggests(StageCommand::suggestConditionalRules)
                            .executes(StageCommand::clearConditionalRule))))
                .then(Commands.literal("clearall")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(StageCommand::clearAllConditionalRules))))

            // /stage gui — open the in-game stage-tree viewer for the calling player
            .then(Commands.literal("gui")
                .executes(StageCommand::openGui))
        );

        // Friendly public command aliases.
        dispatcher.register(Commands.literal("stages").executes(StageCommand::openGui).redirect(stageRoot));
        dispatcher.register(Commands.literal("pstages").executes(StageCommand::openGui).redirect(stageRoot));

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

    private static int getCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String counter = StringArgumentType.getString(context, "counter");
        long value = com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.getCounter(player, counter);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&6" + player.getName().getString() + " &7counter &f" + counter + " &7= &a" + value), false);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static int addCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String counter = StringArgumentType.getString(context, "counter");
        long amount = LongArgumentType.getLong(context, "amount");
        long value = com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.addCounter(player, counter, amount);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aUpdated &f" + counter + " &7for &f" + player.getName().getString() + "&7: &a" + value), true);
        return 1;
    }

    private static int setCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String counter = StringArgumentType.getString(context, "counter");
        long value = LongArgumentType.getLong(context, "value");
        long result = com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.setCounter(player, counter, value);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aSet &f" + counter + " &7for &f" + player.getName().getString() + " &7to &a" + result), true);
        return 1;
    }

    private static int resetCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String counter = StringArgumentType.getString(context, "counter");
        com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.resetCounter(player, counter);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aReset &f" + counter + " &7for &f" + player.getName().getString()), true);
        return 1;
    }

    private static int listActiveRules(CommandContext<CommandSourceStack> context,
                                       ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : context.getSource().getPlayerOrException();
        Map<net.minecraft.resources.ResourceLocation, Long> active = ConditionalLockEngine.activeRules(player);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&6Active conditional rules for &f" + player.getName().getString() + "&6."), false);
        if (active.isEmpty()) {
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes("&7No timed rules are active."), false);
            return 1;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<net.minecraft.resources.ResourceLocation, Long> entry : active.entrySet()) {
            long seconds = Math.max(0L, (entry.getValue() - now + 999L) / 1_000L);
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                "&f" + entry.getKey() + " &7expires in &a" + seconds + " seconds&7."), false);
        }
        return active.size();
    }

    private static int conditionalRuleInfo(CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "rule");
        Optional<ConditionalRule> found = ConditionalLockEngine.findRule(requested);
        if (found.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cConditional rule not found or ambiguous. &f" + requested));
            return 0;
        }
        ConditionalRule rule = found.get();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes("&6Conditional rule &f" + rule.id()), false);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Owner &f" + rule.ownerStage() + "&7. Effect &f" + rule.effect().name().toLowerCase(java.util.Locale.ROOT)
                + "&7. Activation &f" + rule.activation().name().toLowerCase(java.util.Locale.ROOT)
                + "&7. Priority &f" + rule.priority() + "&7."), false);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Stage state &f" + rule.stageState().name().toLowerCase(java.util.Locale.ROOT)
                + (rule.isTriggered() ? "&7. Trigger &f" + rule.triggerType().name().toLowerCase(java.util.Locale.ROOT)
                    + "&7. Duration &f" + Math.max(1L, rule.durationMillis() / 1_000L) + " seconds&7." : "&7.")), false);
        for (ConditionalRule.TargetType type : rule.targets().types()) {
            String included = rule.targets().included(type).stream().map(entry -> entry.raw()).collect(java.util.stream.Collectors.joining(", "));
            String excluded = rule.targets().excluded(type).stream().map(entry -> entry.raw()).collect(java.util.stream.Collectors.joining(", "));
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                "&7" + type.name().toLowerCase(java.util.Locale.ROOT) + " targets. &f" + included
                    + (excluded.isEmpty() ? "" : " &7except &f" + excluded)), false);
        }
        return 1;
    }

    private static int activateConditionalRule(CommandContext<CommandSourceStack> context,
                                               long seconds) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String requested = StringArgumentType.getString(context, "rule");
        Optional<ConditionalRule> found = ConditionalLockEngine.findRule(requested);
        if (found.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cConditional rule not found or ambiguous. &f" + requested));
            return 0;
        }
        if (!found.get().isTriggered()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cOnly triggered rules can be activated manually."));
            return 0;
        }
        boolean activated = ConditionalLockEngine.activate(player, found.get().id(), seconds > 0L ? seconds * 1_000L : 0L);
        if (!activated) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                "&cThe rule could not activate. Check its stage state, context, and refresh setting."));
            return 0;
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aActivated &f" + found.get().id() + " &afor &f" + player.getName().getString() + "&a."), true);
        return 1;
    }

    private static int clearConditionalRule(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String requested = StringArgumentType.getString(context, "rule");
        if (!ConditionalLockEngine.clear(player, requested)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cThat rule is not active or its id is ambiguous."));
            return 0;
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aCleared &f" + requested + " &afor &f" + player.getName().getString() + "&a."), true);
        return 1;
    }

    private static int clearAllConditionalRules(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int cleared = ConditionalLockEngine.clearAll(player);
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aCleared &f" + cleared + " &atimed rules for &f" + player.getName().getString() + "&a."), true);
        return cleared;
    }

    private static CompletableFuture<Suggestions> suggestConditionalRules(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (net.minecraft.resources.ResourceLocation id : ConditionalLockEngine.ruleIds()) {
            String full = id.toString();
            if (full.startsWith(remaining) || id.getPath().startsWith(remaining)
                    || id.getPath().substring(id.getPath().lastIndexOf('/') + 1).startsWith(remaining)) {
                builder.suggest(full);
            }
        }
        return builder.buildFuture();
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
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);

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
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
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

    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        java.util.Set<String> categories = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (StageDefinition definition : com.enviouse.progressivestages.common.api.ProgressiveStagesAPI
                .getAllDefinitions()) {
            if (!definition.getCategory().isBlank()) categories.add(definition.getCategory());
        }
        return net.minecraft.commands.SharedSuggestionProvider.suggest(categories, builder);
    }

    private static int categoryBulk(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        String category = StringArgumentType.getString(context, "category");
        List<StageId> stages = com.enviouse.progressivestages.common.api.ProgressiveStagesAPI
            .getStagesInCategory(category);
        if (stages.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                "&cNo stages use the category '&f" + category + "&c'."));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            for (StageId id : stages) {
                if (grant) {
                    if (com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.grantStageBypass(
                            player, id, com.enviouse.progressivestages.common.api.StageCause.COMMAND)) changed++;
                } else if (com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.revokeStage(
                        player, id, com.enviouse.progressivestages.common.api.StageCause.COMMAND)) changed++;
            }
        }
        final int result = changed;
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&a" + (grant ? "Granted" : "Revoked") + " category '&f" + category
                + "&a': &f" + result + "&a requested-stage change(s)."), true);
        return changed;
    }

    private static int categoryList(CommandContext<CommandSourceStack> context) {
        String category = StringArgumentType.getString(context, "category");
        List<StageId> stages = com.enviouse.progressivestages.common.api.ProgressiveStagesAPI
            .getStagesInCategory(category);
        if (stages.isEmpty()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                "&cNo stages use the category '&f" + category + "&c'."));
            return 0;
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&7Stages in category '&f" + category + "&7' (" + stages.size() + "):"), false);
        for (StageId id : stages) context.getSource().sendSuccess(
            () -> TextUtil.parseColorCodes("  &8• &f" + id), false);
        return stages.size();
    }

    private static int allStagesBulk(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        int changed = 0;
        for (ServerPlayer player : players) {
            if (grant) {
                for (StageId id : StageOrder.getInstance().getOrderedStages()) {
                    if (com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.grantStageBypass(
                            player, id, com.enviouse.progressivestages.common.api.StageCause.COMMAND)) changed++;
                }
            } else {
                changed += com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.revokeStages(
                    player, new ArrayList<>(StageManager.getInstance().getStages(player)),
                    com.enviouse.progressivestages.common.api.StageCause.COMMAND);
            }
        }
        final int result = changed;
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aBulk " + (grant ? "grant" : "revoke") + " complete: &f" + result
                + "&a requested-stage change(s)."), true);
        return changed;
    }

    private static int syncPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        java.util.Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.syncPlayer(player);
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aRe-synced ProgressiveStages state to &f" + players.size() + "&a player(s)."), false);
        return players.size();
    }

    // ---------------------------- v3.0 authoring / debug ----------------------------

    private static String condLabel(com.enviouse.progressivestages.common.trigger.TriggerCondition c) {
        String t = c.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String base = c.target().isEmpty() ? t : t + " " + c.target();
        return c.with().isEmpty() ? base : base + " with " + c.with();
    }

    /** /stage simulate — dry-run: what's reachable next and exactly which conditions are short. */
    private static int simulate(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        var src = context.getSource();
        StageManager sm = StageManager.getInstance();
        StageOrder order = StageOrder.getInstance();
        List<StageId> available = new ArrayList<>();
        List<StageId> blocked = new ArrayList<>();
        java.util.Map<StageId, Float> pct = new java.util.HashMap<>();
        for (StageId id : order.getAllStageIds()) {
            if (sm.hasStage(player, id)) continue;
            if (!sm.getMissingDependencies(player, id).isEmpty()) { blocked.add(id); continue; }
            available.add(id);
            pct.put(id, com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.stagePercent(player, id));
        }
        available.sort((a, b) -> Float.compare(pct.get(b), pct.get(a)));

        final String pname = player.getName().getString();
        src.sendSuccess(() -> TextUtil.parseColorCodes("&6&l▶ Simulate &7— &f" + pname), false);
        if (available.isEmpty() && blocked.isEmpty()) {
            src.sendSuccess(() -> TextUtil.parseColorCodes("&7Every stage is already owned."), false);
            return 1;
        }
        int shown = 0;
        for (StageId id : available) {
            if (shown++ >= 8) break;
            final int p = Math.round(pct.get(id) * 100f);
            final String name = order.getStageDefinition(id).map(StageDefinition::getDisplayName).orElse(id.getPath());
            src.sendSuccess(() -> TextUtil.parseColorCodes("&a➤ &f" + name + " &7(" + p + "%)"), false);
            var rules = com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.describeProgress(player, id);
            if (rules.isEmpty()) {
                src.sendSuccess(() -> TextUtil.parseColorCodes("    &8(granted by command / quest / purchase)"), false);
            }
            for (var rule : rules) {
                for (var cp : rule.conditions()) {
                    if (cp.satisfied()) continue;
                    final long need = Math.max(0, cp.threshold() - cp.current());
                    final String lbl = condLabel(cp.condition());
                    final long cur = cp.current(), th = cp.threshold();
                    src.sendSuccess(() -> TextUtil.parseColorCodes(
                        "    &c✗ &7" + lbl + " &8" + cur + "/" + th + " &c(need " + need + " more)"), false);
                }
            }
        }
        if (!blocked.isEmpty()) {
            src.sendSuccess(() -> TextUtil.parseColorCodes("&8— blocked by prerequisites —"), false);
            for (StageId id : blocked) {
                final String name = order.getStageDefinition(id).map(StageDefinition::getDisplayName).orElse(id.getPath());
                final String miss = sm.getMissingDependencies(player, id).stream()
                    .map(StageId::getPath).reduce((a, b) -> a + ", " + b).orElse("");
                src.sendSuccess(() -> TextUtil.parseColorCodes("&8🔒 &7" + name + " &8← needs &7" + miss), false);
            }
        }
        return 1;
    }

    /** /stage new <id> — scaffold a stage TOML file in the config directory. */
    private static int newStage(CommandContext<CommandSourceStack> context) {
        String rawId = StringArgumentType.getString(context, "id");
        StageId stageId = StageId.tryParse(rawId);
        if (stageId == null || java.util.Arrays.stream(stageId.getPath().split("/"))
                .anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cInvalid stage ID."));
            return 0;
        }
        if (StageOrder.getInstance().stageExists(stageId)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cThat stage ID is already loaded."));
            return 0;
        }
        java.nio.file.Path dir = StageFileLoader.getInstance().getStagesDirectory();
        if (dir == null) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cStages directory not ready."));
            return 0;
        }
        String configId = stageId.isDefaultNamespace() ? stageId.getPath() : stageId.toString();
        String relative = stageId.isDefaultNamespace()
            ? stageId.getPath() : stageId.getNamespace() + "/" + stageId.getPath();
        java.nio.file.Path root = dir.toAbsolutePath().normalize();
        java.nio.file.Path file = root.resolve(relative + ".toml").normalize();
        if (!file.startsWith(root)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cInvalid stage file path."));
            return 0;
        }
        if (java.nio.file.Files.exists(file)) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cA stage file already exists at &f" + file + "&c."));
            return 0;
        }
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, scaffold(configId));
        } catch (java.io.IOException e) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cFailed to write: " + e.getMessage()));
            return 0;
        }
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
            "&aCreated &f" + file + "&a. Edit it, then run &e/stage reload&a."), true);
        return 1;
    }

    private static String scaffold(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        String display = leaf.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
            + leaf.substring(1).replace('_', ' ');
        return "# Stage file scaffolded by /stage new. See the diamond_age.toml for the full reference.\n"
            + "[stage]\n"
            + "id = \"" + id + "\"\n"
            + "display_name = \"" + display + "\"\n"
            + "description = \"\"\n"
            + "# icon = \"minecraft:diamond\"\n"
            + "# dependency = [\"iron_age\"]      # prerequisite stage(s)\n"
            + "# dependency_mode = \"all\"         # all | any | at_least\n"
            + "# dependency_count = 1              # used by at_least\n"
            + "# tags = [\"tier1\"]               # for /stage tag grant ...\n\n"
            + "[display]\n"
            + "# x = 0                              # explicit map position; omit x+y for auto-layout\n"
            + "# y = 0\n"
            + "frame = \"task\"                    # task | goal | challenge\n"
            + "reveal = \"always\"                 # always | dependencies | unlocked\n"
            + "# background = \"minecraft:block/stone\"\n"
            + "# sort_order = 0                     # stable order within auto-layout layers\n\n"
            + "# --- what this stage LOCKS (omit any you don't need) ---\n"
            + "[items]\n"
            + "locked = []   # [\"id:minecraft:diamond\", \"mod:create\", \"tag:c:ingots\", \"name:*sword\"]\n\n"
            + "# [blocks]\n# locked = []\n\n"
            + "# --- auto-grant when conditions are met (optional) ---\n"
            + "# [[triggers]]\n"
            + "# mode = \"all_of\"\n"
            + "# [[triggers.conditions]]\n"
            + "# type = \"mine\"\n"
            + "# block = \"minecraft:diamond_ore\"\n"
            + "# count = 10\n\n"
            + "# KubeJS/command bridge: type = \"custom_counter\", counter = \"quest_points\"\n"
            + "# /stage counter add <player> quest_points 1\n\n"
            + "# --- optional: [cost] (purchasable), [rewards], [unlock] juice, [attribute], [revoke] ---\n";
    }

    /** /stage export — write a markdown progression guide built from the stage graph. */
    private static int exportGuide(CommandContext<CommandSourceStack> context) {
        StageOrder order = StageOrder.getInstance();
        StringBuilder sb = new StringBuilder("# Progression Guide\n\n_Generated by ProgressiveStages._\n\n");
        for (StageId id : order.getAllStageIds()) {
            var defOpt = order.getStageDefinition(id);
            if (defOpt.isEmpty()) continue;
            StageDefinition def = defOpt.get();
            sb.append("## ").append(def.getDisplayName()).append(" (`").append(id).append("`)\n\n");
            if (def.getDescription() != null && !def.getDescription().isEmpty()) {
                sb.append(def.getDescription()).append("\n\n");
            }
            if (!def.getDependencies().isEmpty()) {
                sb.append("**Requires:** ").append(def.getDependencies().stream()
                    .map(StageId::getPath).reduce((a, b) -> a + ", " + b).orElse("")).append("\n\n");
            }
            var dependents = order.getDependents(id);
            if (!dependents.isEmpty()) {
                sb.append("**Leads to:** ").append(dependents.stream()
                    .map(StageId::getPath).reduce((a, b) -> a + ", " + b).orElse("")).append("\n\n");
            }
            if (def.hasTriggers()) {
                sb.append("**Unlock by:**\n");
                for (var rule : def.getTriggers()) {
                    for (var c : rule.conditions()) {
                        sb.append("- ").append(condLabel(c)).append(" ×").append(c.count()).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (def.isPurchasable()) sb.append("**Purchasable** from the skill tree.\n\n");
        }
        java.nio.file.Path dir = StageFileLoader.getInstance().getStagesDirectory();
        java.nio.file.Path out = (dir != null ? dir.getParent()
            : net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()).resolve("progressivestages_guide.md");
        try {
            java.nio.file.Files.writeString(out, sb.toString());
        } catch (java.io.IOException e) {
            context.getSource().sendFailure(TextUtil.parseColorCodes("&cFailed to write guide: " + e.getMessage()));
            return 0;
        }
        final String path = out.toString();
        context.getSource().sendSuccess(() -> TextUtil.parseColorCodes("&aExported progression guide → &f" + path), true);
        return 1;
    }

    private static int grantStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.tryParse(stageName);

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

    /** Clear pending bypass confirmations between logical-server runs. */
    public static void clearRuntimeState() {
        bypassConfirmations.clear();
    }

    private static int revokeStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String stageName = StringArgumentType.getString(context, "stage");
        StageId stageId = StageId.tryParse(stageName);

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
        StageId stageId = StageId.tryParse(stageName);

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
        StageId stageId = StageId.tryParse(stageName);

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
            context.getSource().sendSuccess(() -> TextUtil.parseColorCodes(
                "&7Dependency policy: &f" + def.getDependencyMode().configName()
                    + " &8(required: " + def.getDependencyCount() + "/" + deps.size() + ")"), false);
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
        StageFileLoader loader = StageFileLoader.getInstance();
        if (!loader.reload()) {
            context.getSource().sendFailure(TextUtil.parseColorCodes(
                "&cStage reload rejected. The previous valid configuration is still active."));
            for (String error : loader.getLastReloadErrors()) {
                context.getSource().sendFailure(TextUtil.parseColorCodes("&7" + error));
            }
            return 0;
        }

        int syncedPlayers = loader.syncPlayersAfterReload();

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
            StageId startId = StageId.tryParse(startingStage);
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
        StageId stageId = StageId.tryParse(stageName);

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
                    final String line = "    &8Rule " + ri + " &7(" + rule.mode().name().toLowerCase(java.util.Locale.ROOT)
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
                + rule.mode().name().toLowerCase(java.util.Locale.ROOT) + ")" + desc;
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
        String type = c.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
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
        StageId stageId = StageId.tryParse(stageName);

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
