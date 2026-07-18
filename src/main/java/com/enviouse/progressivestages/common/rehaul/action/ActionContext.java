package com.enviouse.progressivestages.common.rehaul.action;

import java.util.Map;
import java.util.Objects;

public record ActionContext(ActionSubject subject, long nowMillis, Map<String, Object> values,
                            Map<String, ActionService> services) {

    public ActionContext {
        Objects.requireNonNull(subject, "subject");
        values = values == null ? Map.of() : Map.copyOf(values);
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    public interface ActionService {
        ActionResult call(Map<String, Object> arguments, ActionContext context);
    }
}
