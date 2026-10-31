package com.enviouse.progressivestages.common.config;

import net.minecraft.world.level.GameType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** An optional, personal stage source maintained while the player's game mode matches. */
public record StageGameModeGrant(Set<GameType> modes) {
    public static final StageGameModeGrant NONE = new StageGameModeGrant(Set.of());

    public StageGameModeGrant {
        modes = modes == null || modes.isEmpty() ? Set.of() : Set.copyOf(modes);
    }

    public boolean enabled() { return !modes.isEmpty(); }

    public static StageGameModeGrant parse(List<String> names) {
        if (names == null || names.isEmpty()) return NONE;
        EnumSet<GameType> parsed = EnumSet.noneOf(GameType.class);
        for (String name : names) {
            if (name == null) throw new IllegalArgumentException("gamemode.grant_in contains an invalid game mode");
            GameType mode = switch (name.trim().toUpperCase(Locale.ROOT)) {
                case "SURVIVAL" -> GameType.SURVIVAL;
                case "CREATIVE" -> GameType.CREATIVE;
                case "ADVENTURE" -> GameType.ADVENTURE;
                case "SPECTATOR" -> GameType.SPECTATOR;
                default -> throw new IllegalArgumentException("gamemode.grant_in contains an invalid game mode: " + name);
            };
            parsed.add(mode);
        }
        return new StageGameModeGrant(parsed);
    }
}
