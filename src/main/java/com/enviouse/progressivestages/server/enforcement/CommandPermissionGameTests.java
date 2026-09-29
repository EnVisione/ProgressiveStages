package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.LuckPermsStageOptions;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.loader.StageFileParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class CommandPermissionGameTests {
    private CommandPermissionGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void executionRechecksStagesAfterVanillaRedirects(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "command_test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var order = StageOrder.getInstance();
        var previous = order.getOrderedStages().stream()
            .map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        StageId stage = StageId.parse("progressivestages:command_execution_regression");
        var options = new LuckPermsStageOptions(false, false, LuckPermsStageOptions.InboundMode.SYNCHRONIZED,
            List.of(), List.of(), List.of(
                new LuckPermsStageOptions.CommandPermissionRule("time_gate", "time set", true),
                new LuckPermsStageOptions.CommandPermissionRule("return_gate", "return", true)));
        var stages = helper.getLevel().getData(StageAttachments.TEAM_STAGES);
        var owner = TeamProvider.getInstance().getTeamId(player);
        var originalTimes = new LinkedHashMap<net.minecraft.server.level.ServerLevel, Long>();
        server.getAllLevels().forEach(level -> originalTimes.put(level, level.getDayTime()));
        order.registerStage(StageDefinition.builder(stage).luckPerms(options).build());
        try {
            helper.getLevel().setDayTime(100);
            server.getCommands().performPrefixedCommand(source, "time set 200");
            helper.assertTrue(helper.getLevel().getDayTime() == 100, "Direct denial must not change time.");
            server.getCommands().performPrefixedCommand(source, "execute positioned ~ ~ ~ run time set 300");
            helper.assertTrue(helper.getLevel().getDayTime() == 100, "Redirected denial must not change time.");
            stages.grantStage(owner, stage);
            server.getCommands().performPrefixedCommand(source, "execute positioned ~ ~ ~ run time set 400");
            helper.assertTrue(helper.getLevel().getDayTime() == 400, "Granted redirected command must execute.");
            server.getCommands().performPrefixedCommand(source.withPermission(0), "time set 500");
            helper.assertTrue(helper.getLevel().getDayTime() == 400, "A stage must not bypass native permission.");
            stages.revokeStage(owner, stage);
            server.getCommands().performPrefixedCommand(source, "execute positioned ~ ~ ~ run time set 600");
            helper.assertTrue(helper.getLevel().getDayTime() == 400, "Execution must recheck a revoked stage.");

            var result = new AtomicInteger(-1);
            var successes = new AtomicInteger();
            var failures = new AtomicInteger();
            var callbackSource = source.withCallback((success, value) -> {
                result.set(value);
                if (success) successes.incrementAndGet(); else failures.incrementAndGet();
            });
            server.getCommands().performPrefixedCommand(callbackSource, "return 7");
            helper.assertTrue(result.get() == 0 && successes.get() == 0 && failures.get() == 1,
                "Denied custom execution must report one failure without running its result callback.");
            stages.grantStage(owner, stage);
            server.getCommands().performPrefixedCommand(callbackSource, "return 7");
            helper.assertTrue(result.get() == 7 && successes.get() == 1,
                "Allowed custom execution must preserve its result callback.");
            stages.revokeStage(owner, stage);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 700");
            helper.assertTrue(helper.getLevel().getDayTime() == 700, "Console without a player keeps its semantics.");
            helper.succeed();
        } finally {
            originalTimes.forEach((level, time) -> level.setDayTime(time));
            stages.revokeStage(owner, stage);
            order.clear();
            previous.forEach(order::registerStage);
            player.discard();
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void parsedCommandDefaultsReachActualExecution(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "command_default"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        var order = StageOrder.getInstance();
        var previous = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var stage = StageId.parse("progressivestages:command_default_regression");
        var stages = helper.getLevel().getData(StageAttachments.TEAM_STAGES);
        var owner = TeamProvider.getInstance().getTeamId(player);
        var originalTimes = new LinkedHashMap<net.minecraft.server.level.ServerLevel, Long>();
        server.getAllLevels().forEach(level -> originalTimes.put(level, level.getDayTime()));
        try {
            for (String selection : List.of("omitted", "true", "false")) {
                String text = "[stage]\nid = \"" + stage + "\"\n[[command_permissions]]\nid = \"time_gate\"\npath = \"time\"\n"
                    + (selection.equals("omitted") ? "" : "descendants = " + selection + "\n");
                var parsed = StageFileParser.parseText(text, "stage.toml", "test", false);
                helper.assertTrue(parsed.isSuccess(), "The configured command gate must parse.");
                order.clear();
                previous.forEach(order::registerStage);
                order.registerStage(parsed.getStageDefinition());
                helper.assertTrue(order.getStageDefinition(stage).orElseThrow() == parsed.getStageDefinition(),
                    "Each configured selection must replace the previous fixture definition.");
                helper.getLevel().setDayTime(100);
                server.getCommands().performPrefixedCommand(source, "time set 200");
                boolean descendants = !selection.equals("false");
                helper.assertTrue(helper.getLevel().getDayTime() == (descendants ? 100 : 200),
                    "Omitted and true descendants must gate children while explicit false excludes them.");
                server.getCommands().performPrefixedCommand(source, "execute positioned ~ ~ ~ run time set 250");
                helper.assertTrue(helper.getLevel().getDayTime() == (descendants ? 100 : 250),
                    "Redirected execution must use the parsed descendant selection.");
                stages.grantStage(owner, stage);
                server.getCommands().performPrefixedCommand(source, "time set 300");
                helper.assertTrue(helper.getLevel().getDayTime() == 300, "Stage ownership must permit the child command.");
                server.getCommands().performPrefixedCommand(source.withPermission(0), "time set 350");
                helper.assertTrue(helper.getLevel().getDayTime() == 300, "Native permission remains required.");
                stages.revokeStage(owner, stage);
                server.getCommands().performPrefixedCommand(source, "time set 400");
                helper.assertTrue(helper.getLevel().getDayTime() == (descendants ? 300 : 400),
                    "Revocation must restore the configured command gate.");
            }
        } finally {
            originalTimes.forEach((level, time) -> level.setDayTime(time));
            stages.revokeStage(owner, stage);
            order.clear();
            previous.forEach(order::registerStage);
            player.discard();
        }
        helper.succeed();
    }
}
