package com.enviouse.progressivestages.common.rehaul.value;

import com.enviouse.progressivestages.common.rehaul.template.MergePolicy;
import com.enviouse.progressivestages.common.rehaul.template.ParameterType;
import com.enviouse.progressivestages.common.rehaul.template.TemplateDefinition;
import com.enviouse.progressivestages.common.rehaul.template.TemplateEngine;
import com.enviouse.progressivestages.common.rehaul.template.TemplateParameter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormulaAndTemplateTest {

    @Test
    void formulasAreSafeComposableAndCycleChecked() {
        FormulaRegistry formulas = new FormulaRegistry();
        formulas.rebuild(Map.of("base", "level * 2", "result", "clamp(base + bonus, 0, 100)"));
        assertEquals(16, formulas.evaluate("result", Map.of("level", 5.0, "bonus", 6.0)));
        assertThrows(IllegalArgumentException.class, () -> formulas.rebuild(Map.of("one", "two + 1", "two", "one + 1")));
        assertThrows(IllegalArgumentException.class, () -> new FormulaCompiler().compile("java.lang.System.exit(0)"));
    }

    @Test
    void templatesValidateParametersAndDetectIncludes() {
        ResourceLocation base = ResourceLocation.parse("pack:base");
        ResourceLocation weapon = ResourceLocation.parse("pack:weapon");
        TemplateEngine templates = new TemplateEngine();
        templates.rebuild(List.of(
            new TemplateDefinition(base, List.of(), Map.of("priority",
                new TemplateParameter("priority", ParameterType.INTEGER, true, null)),
                Map.of("rules", List.of(Map.of("priority", "${priority}"))), MergePolicy.DEEP_MERGE),
            new TemplateDefinition(weapon, List.of(base), Map.of("priority",
                new TemplateParameter("priority", ParameterType.INTEGER, true, null), "item",
                new TemplateParameter("item", ParameterType.SELECTOR, true, null)),
                Map.of("items", List.of("${item}")), MergePolicy.DEEP_MERGE)));
        Map<String, Object> expanded = templates.expand(weapon, Map.of("priority", 300, "item", "tag:c:swords"));
        assertEquals(List.of("tag:c:swords"), expanded.get("items"));
        assertThrows(IllegalArgumentException.class, () -> templates.expand(weapon, Map.of("priority", "high", "item", "tag:c:swords")));

        TemplateEngine cyclic = new TemplateEngine();
        assertThrows(IllegalArgumentException.class, () -> cyclic.rebuild(List.of(
            new TemplateDefinition(base, List.of(weapon), Map.of(), Map.of(), MergePolicy.DEEP_MERGE),
            new TemplateDefinition(weapon, List.of(base), Map.of(), Map.of(), MergePolicy.DEEP_MERGE))));
    }
}
