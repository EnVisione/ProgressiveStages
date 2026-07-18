package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.rehaul.schema.EditorSchemaRegistry;
import com.enviouse.progressivestages.common.rehaul.schema.SchemaValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSchemaRegistryTest {

    @Test
    void builtInSchemaCoversEveryRequiredEditorSurface() {
        List<String> required = List.of(
            "stage.toml:stage.id",
            "stage.toml:stage.display_name",
            "stage.toml:stage.dependencies",
            "stage.toml:stage.dependency_mode",
            "stage.toml:stage.dependency_count",
            "stage.toml:display.background",
            "rules.toml:items.locked",
            "rules.toml:blocks.locked",
            "rules.toml:fluids.locked",
            "rules.toml:recipes.locked",
            "rules.toml:crops.locked",
            "rules.toml:dimensions.locked",
            "rules.toml:enchants.locked",
            "rules.toml:entities.locked",
            "rules.toml:interactions.locked",
            "rules.toml:loot.locked",
            "rules.toml:mobs.locked",
            "rules.toml:pets.locked",
            "rules.toml:screens.locked",
            "rules.toml:trades.locked",
            "rules.toml:professions.locked",
            "rules.toml:advancements.locked",
            "rules.toml:structures.locked",
            "rules.toml:regions.locked",
            "rules.toml:curios.locked",
            "rules.toml:ores.locked",
            "rules.toml:beacon.locked",
            "rules.toml:brewing.locked",
            "rules.toml:abilities.locked",
            "rules.toml:temporary_rules",
            "rules.toml:item_modifiers",
            "rules.toml:drop_modifiers",
            "progression.toml:grants",
            "progression.toml:revokes",
            "progression.toml:rewards",
            "progression.toml:cost",
            "progression.toml:challenges",
            "progression.toml:variables",
            "progression.toml:formulas",
            "progression.toml:profiles",
            "progression.toml:templates",
            "progression.toml:states",
            "progressivestages.toml:general.starting_stages",
            "progressivestages.toml:enforcement.block_item_use",
            "progressivestages.toml:emi.enabled",
            "progressivestages.toml:performance.enable_lock_cache",
            "progressivestages.toml:integration.ftbteams.enabled"
        );

        assertEquals(List.of(), EditorSchemaRegistry.get().validateCoverage(required));
        assertTrue(EditorSchemaRegistry.get().all().stream().allMatch(field -> !field.help().isBlank()));
        assertTrue(EditorSchemaRegistry.get().all().stream()
            .filter(field -> field.type() == SchemaValueType.PREFIX)
            .allMatch(field -> field.catalog().isPresent() && !field.prefixModes().isEmpty()));
    }
}
