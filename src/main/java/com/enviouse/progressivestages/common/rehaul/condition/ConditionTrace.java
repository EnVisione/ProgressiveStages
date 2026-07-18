package com.enviouse.progressivestages.common.rehaul.condition;

import java.util.List;

public record ConditionTrace(String path, String type, ConditionResult result, List<ConditionTrace> children) {

    public ConditionTrace {
        path = path == null ? "" : path;
        type = type == null ? "" : type;
        children = children == null ? List.of() : List.copyOf(children);
    }
}
