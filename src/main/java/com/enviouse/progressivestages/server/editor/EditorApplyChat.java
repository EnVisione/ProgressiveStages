package com.enviouse.progressivestages.server.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class EditorApplyChat {
    private EditorApplyChat() {}

    static void broadcast(MinecraftServer server, UUID actor, EditorApplyResult result) {
        if (server == null || result == null || !result.success()) return;
        ServerPlayer operator = server.getPlayerList().getPlayer(actor);
        String actorName = operator == null ? actor.toString() : operator.getGameProfile().getName();
        List<String> messages = messages(actorName, result);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(3)) continue;
            for (String message : messages) player.sendSystemMessage(Component.literal(message));
        }
    }

    static List<String> messages(String actorName, EditorApplyResult result) {
        List<String> messages = new ArrayList<>();
        int changes = result.diff().size();
        messages.add("[progressivestages editor] " + actorName + " applied " + changes
            + (changes == 1 ? " file change." : " file changes."));
        for (DraftDiffEntry entry : result.diff()) {
            messages.add("[" + entry.change().name().toLowerCase(Locale.ROOT) + "] " + entry.path()
                + ", " + entry.beforeBytes() + " to " + entry.afterBytes() + " bytes.");
        }
        String transaction = result.transactionId().isBlank()
            ? "" : " transaction " + result.transactionId() + ".";
        messages.add("[progressivestages editor] files reloaded and synchronized. revision "
            + result.configurationRevision() + "." + transaction);
        return List.copyOf(messages);
    }
}
