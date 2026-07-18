package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageSlotPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageSlotResolverTest {

    @Test
    void unlimitedGroupsStackAndLimitedGroupsCanDenyOrReplace() {
        StageDefinition coal = stage("showcase:coal_engineer", 100, 1, StageSlotPolicy.REPLACE_OLDEST);
        StageDefinition iron = stage("showcase:iron_engineer", 200, 1, StageSlotPolicy.REPLACE_OLDEST);
        StageDefinition diamond = stage("showcase:diamond_engineer", 300, 1, StageSlotPolicy.REPLACE_OLDEST);
        Map<StageId, StageDefinition> stages = map(coal, iron, diamond);

        StageSlotResolver.Decision replace = StageSlotResolver.resolve(diamond, Set.of(coal.getId()),
            id -> Optional.ofNullable(stages.get(id)), id -> id.equals(coal.getId()) ? 10 : 20);
        assertTrue(replace.allowed());
        assertEquals(java.util.List.of(coal.getId()), replace.replacements());

        StageDefinition denied = StageDefinition.builder(StageId.parse("showcase:denied"))
            .slotGroup("engineering").slotLimit(1).slotPolicy(StageSlotPolicy.DENY).build();
        stages.put(denied.getId(), denied);
        assertFalse(StageSlotResolver.resolve(denied, Set.of(coal.getId()),
            id -> Optional.ofNullable(stages.get(id)), id -> 1).allowed());

        StageDefinition stacking = StageDefinition.builder(StageId.parse("showcase:stacking"))
            .slotGroup("engineering").slotLimit(0).slotPolicy(StageSlotPolicy.DENY).build();
        assertTrue(StageSlotResolver.resolve(stacking, Set.of(coal.getId(), iron.getId()),
            id -> Optional.ofNullable(stages.get(id)), id -> 1).replacements().isEmpty());
    }

    @Test
    void lowestPriorityPolicyUsesPriorityBeforeAge() {
        StageDefinition coal = stage("showcase:coal_engineer", 50, 2, StageSlotPolicy.REPLACE_LOWEST_PRIORITY);
        StageDefinition iron = stage("showcase:iron_engineer", 200, 2, StageSlotPolicy.REPLACE_LOWEST_PRIORITY);
        StageDefinition diamond = stage("showcase:diamond_engineer", 300, 2, StageSlotPolicy.REPLACE_LOWEST_PRIORITY);
        Map<StageId, StageDefinition> stages = map(coal, iron, diamond);

        StageSlotResolver.Decision decision = StageSlotResolver.resolve(diamond,
            Set.of(coal.getId(), iron.getId()), id -> Optional.ofNullable(stages.get(id)), id -> 1);

        assertEquals(java.util.List.of(coal.getId()), decision.replacements());
    }

    @Test
    void oldestAndReplaceAllPoliciesChooseDeterministicMembers() {
        StageDefinition coal = stage("showcase:coal_engineer", 50, 2, StageSlotPolicy.REPLACE_OLDEST);
        StageDefinition iron = stage("showcase:iron_engineer", 100, 2, StageSlotPolicy.REPLACE_OLDEST);
        StageDefinition diamond = stage("showcase:diamond_engineer", 200, 2, StageSlotPolicy.REPLACE_OLDEST);
        Map<StageId, StageDefinition> stages = map(coal, iron, diamond);

        StageSlotResolver.Decision oldest = StageSlotResolver.resolve(diamond,
            Set.of(coal.getId(), iron.getId()), id -> Optional.ofNullable(stages.get(id)),
            id -> id.equals(iron.getId()) ? 10 : 20);
        assertEquals(java.util.List.of(iron.getId()), oldest.replacements());

        StageDefinition replaceAll = stage("showcase:replace_all", 300, 2, StageSlotPolicy.REPLACE_ALL);
        stages.put(replaceAll.getId(), replaceAll);
        StageSlotResolver.Decision all = StageSlotResolver.resolve(replaceAll,
            Set.of(coal.getId(), iron.getId()), id -> Optional.ofNullable(stages.get(id)),
            id -> id.equals(iron.getId()) ? 10 : 20);
        assertEquals(java.util.List.of(iron.getId(), coal.getId()), all.replacements());
    }

    private static StageDefinition stage(String id, int priority, int limit, StageSlotPolicy policy) {
        return StageDefinition.builder(StageId.parse(id)).priority(priority)
            .slotGroup("engineering").slotLimit(limit).slotPolicy(policy).build();
    }

    private static Map<StageId, StageDefinition> map(StageDefinition... stages) {
        Map<StageId, StageDefinition> output = new LinkedHashMap<>();
        for (StageDefinition stage : stages) output.put(stage.getId(), stage);
        return output;
    }
}
