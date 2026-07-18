package com.enviouse.progressivestages.common.rehaul.action;

import java.util.List;

public record ActionChain(List<CompiledAction> actions, boolean atomic) {

    public static final ActionChain EMPTY = new ActionChain(List.of(), true);

    public ActionChain {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
