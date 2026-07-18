package com.enviouse.progressivestages.common.rehaul.template;

public record TemplateParameter(String name, ParameterType type, boolean required, Object defaultValue) {
    public TemplateParameter {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Template parameter name cannot be blank");
        type = type == null ? ParameterType.STRING : type;
    }
}
