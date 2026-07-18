package com.enviouse.progressivestages.common.rehaul.challenge;

public record ChallengeHud(boolean enabled, String placement, double scale, String color,
                           String icon, String animation, boolean compact,
                           boolean hideWhenInactive, boolean valuesSecret) {

    public ChallengeHud {
        placement = placement == null ? "top" : placement;
        color = color == null ? "white" : color;
        icon = icon == null ? "" : icon;
        animation = animation == null ? "none" : animation;
        if (!Double.isFinite(scale) || scale <= 0 || scale > 10) throw new IllegalArgumentException("Challenge HUD scale is invalid");
    }

    public static ChallengeHud defaults() {
        return new ChallengeHud(true, "top", 1, "white", "", "none", false, true, false);
    }
}
