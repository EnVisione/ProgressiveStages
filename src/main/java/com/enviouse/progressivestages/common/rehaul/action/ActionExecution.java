package com.enviouse.progressivestages.common.rehaul.action;

import java.util.List;

public record ActionExecution(boolean success, boolean rolledBack, List<ActionResult> results,
                              String explanation) {

    public ActionExecution {
        results = results == null ? List.of() : List.copyOf(results);
        explanation = explanation == null ? "" : explanation;
    }
}
