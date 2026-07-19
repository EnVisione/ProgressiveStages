package com.enviouse.progressivestages.server.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class EditorApplyChat {
    private EditorApplyChat() {}

    static void broadcast(MinecraftServer server, UUID actor, EditorApplyResult result) {
        if (server == null || result == null || !result.success() || result.diff().isEmpty()) return;
        ServerPlayer operator = server.getPlayerList().getPlayer(actor);
        String actorName = operator == null ? actor.toString() : operator.getGameProfile().getName();
        List<Component> messages = messages(actorName, result);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(3)) continue;
            for (Component message : messages) player.sendSystemMessage(message);
        }
    }

    static List<Component> messages(String actorName, EditorApplyResult result) {
        if (result == null || !result.success() || result.diff().isEmpty()) return List.of();
        List<Component> messages = new ArrayList<>();
        int changes = result.diff().size();
        messages.add(Component.literal("[progressivestages editor] " + actorName + " applied " + changes
            + (changes == 1 ? " file change." : " file changes.")).withStyle(ChatFormatting.GOLD));
        for (DraftDiffEntry entry : result.diff()) {
            ChatFormatting color = switch (entry.change()) {
                case ADDED -> ChatFormatting.GREEN;
                case MODIFIED -> ChatFormatting.YELLOW;
                case DELETED -> ChatFormatting.RED;
            };
            messages.add(Component.literal("[" + entry.change().name().toLowerCase(java.util.Locale.ROOT)
                + "] " + entry.path() + ", " + entry.beforeBytes() + " to "
                + entry.afterBytes() + " bytes.").withStyle(color));
        }
        String transaction = result.transactionId().isBlank()
            ? "" : " transaction " + result.transactionId() + ".";
        messages.add(Component.literal("[progressivestages editor] files reloaded and synchronized. revision "
            + result.configurationRevision() + "." + transaction).withStyle(ChatFormatting.GREEN));
        return List.copyOf(messages);
    }
}
