package com.enviouse.progressivestages.common.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.api.StageId;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class BlockOverrideRegistryTest {
    private final LockRegistry registry = LockRegistry.getInstance();

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void expandsIdAndModTargetsOnceAndOrdersByPriority() {
        var override = new LockDefinition.BlockOverride(List.of(
            PrefixEntry.parse("id:minecraft:diamond_ore|priority=2"),
            PrefixEntry.parse("mod:minecraft|priority=4")),
            ResourceLocation.withDefaultNamespace("stone"),
            ResourceLocation.withDefaultNamespace("cobblestone"), 5,
            "blocks.overrides", "blocks.overrides[0].targets");
        var locks = LockDefinition.builder().blockOverrides(List.of(override)).build();
        var stage = StageDefinition.builder(StageId.parse("test:block_overrides"))
            .locks(locks).build();

        registry.registerStage(stage);

        var diamond = registry.getOreOverridesFor(Blocks.DIAMOND_ORE);
        assertEquals(1, diamond.size());
        assertEquals(4, diamond.getFirst().priority);
        assertEquals("blocks.overrides", diamond.getFirst().sourceTable);
        assertEquals("minecraft:diamond_ore", diamond.getFirst().target.toString());

        assertTrue(registry.getOreOverridesFor(Blocks.STONE).stream()
            .allMatch(entry -> entry.priority == 4));
        assertEquals(BuiltInRegistries.BLOCK.getKey(Blocks.STONE),
            registry.getOreOverridesFor(Blocks.STONE).getFirst().target);
    }

    @Test
    void clearingRulesAlsoClearsTheExpandedBlockIndex() {
        var override = new LockDefinition.BlockOverride(List.of(PrefixEntry.parse("id:minecraft:diamond_ore")),
            ResourceLocation.withDefaultNamespace("stone"),
            ResourceLocation.withDefaultNamespace("cobblestone"), 0, "blocks.overrides");
        var stage = StageDefinition.builder(StageId.parse("test:block_override_clear"))
            .locks(LockDefinition.builder().blockOverrides(List.of(override)).build()).build();
        registry.registerStage(stage);
        assertTrue(registry.getOreOverridesFor(Blocks.DIAMOND_ORE).size() > 0);

        registry.clear();

        assertTrue(registry.getOreOverridesFor(Blocks.DIAMOND_ORE).isEmpty());
    }
}
