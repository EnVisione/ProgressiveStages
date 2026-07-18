package com.enviouse.progressivestages.common.rehaul;

public record ViewerPolicy(
        Mode shared,
        Mode emi,
        Mode jei,
        boolean ingredientVisible,
        boolean recipeVisible,
        boolean outputVisible,
        boolean tooltipVisible,
        boolean lockedOverlay) {

    public enum Mode {
        INHERIT,
        SHOW,
        HIDE,
        LOCKED_OVERLAY
    }

    public static final ViewerPolicy INHERIT = new ViewerPolicy(
        Mode.INHERIT, Mode.INHERIT, Mode.INHERIT, true, true, true, true, true);

    public ViewerPolicy {
        shared = shared != null ? shared : Mode.INHERIT;
        emi = emi != null ? emi : Mode.INHERIT;
        jei = jei != null ? jei : Mode.INHERIT;
    }
}
