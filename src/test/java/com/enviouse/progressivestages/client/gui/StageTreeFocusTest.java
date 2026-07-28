package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.common.api.StageId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageTreeFocusTest {

    private static final StageId ROOT = new StageId("test:root");
    private static final StageId MAGE = new StageId("test:mage");
    private static final StageId WARRIOR = new StageId("test:warrior");
    private static final StageId WIZARD = new StageId("test:wizard");
    private static final StageId KNIGHT = new StageId("test:knight");
    private static final StageId SPELLBLADE = new StageId("test:spellblade");

    private static final Map<StageId, List<StageId>> DEPENDENCIES = Map.of(
        ROOT, List.of(),
        MAGE, List.of(ROOT),
        WARRIOR, List.of(ROOT),
        WIZARD, List.of(MAGE),
        KNIGHT, List.of(WARRIOR),
        SPELLBLADE, List.of(WIZARD, KNIGHT)
    );

    @Test
    void focusIncludesAncestorsAndEveryDescendantBranch() {
        Set<StageId> branch = StageTreeFocus.branch(
            MAGE, DEPENDENCIES.keySet(), id -> DEPENDENCIES.getOrDefault(id, List.of()));

        assertEquals(Set.of(ROOT, MAGE, WIZARD, SPELLBLADE), branch);
    }

    @Test
    void focusDoesNotPullInSiblingPathsThroughACommonAncestor() {
        Set<StageId> branch = StageTreeFocus.branch(
            WIZARD, DEPENDENCIES.keySet(), id -> DEPENDENCIES.getOrDefault(id, List.of()));

        assertEquals(Set.of(ROOT, MAGE, WIZARD, SPELLBLADE), branch);
    }

    @Test
    void hiddenStagesAreExcludedFromTheFocusedBranch() {
        Set<StageId> visible = Set.of(ROOT, MAGE, WARRIOR, WIZARD, KNIGHT);

        Set<StageId> branch = StageTreeFocus.branch(
            MAGE, visible, id -> DEPENDENCIES.getOrDefault(id, List.of()));

        assertEquals(Set.of(ROOT, MAGE, WIZARD), branch);
    }
}
