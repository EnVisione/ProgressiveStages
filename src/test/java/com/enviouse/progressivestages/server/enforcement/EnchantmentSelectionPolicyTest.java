package com.enviouse.progressivestages.server.enforcement;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantmentSelectionPolicyTest {

    @Test
    void zeroWeightCandidatesAreNeverSelected() {
        List<EnchantmentSelectionPolicy.WeightedCandidate<String>> candidates = List.of(
            new EnchantmentSelectionPolicy.WeightedCandidate<>("disabled", 0),
            new EnchantmentSelectionPolicy.WeightedCandidate<>("enabled", 1)
        );

        RandomSource random = RandomSource.create(42L);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals("enabled",
                EnchantmentSelectionPolicy.pickWeighted(random, candidates).orElseThrow());
        }
    }

    @Test
    void weightedSelectionReturnsEveryPositiveCandidate() {
        List<EnchantmentSelectionPolicy.WeightedCandidate<String>> candidates = List.of(
            new EnchantmentSelectionPolicy.WeightedCandidate<>("common", 10),
            new EnchantmentSelectionPolicy.WeightedCandidate<>("rare", 1)
        );

        boolean common = false;
        boolean rare = false;
        RandomSource random = RandomSource.create(7L);
        for (int attempt = 0; attempt < 500 && (!common || !rare); attempt++) {
            String selected = EnchantmentSelectionPolicy.pickWeighted(random, candidates).orElseThrow();
            common |= selected.equals("common");
            rare |= selected.equals("rare");
        }

        assertTrue(common);
        assertTrue(rare);
    }

    @Test
    void emptyOrDisabledCandidatesProduceNoSelection() {
        RandomSource random = RandomSource.create(1L);

        assertTrue(EnchantmentSelectionPolicy.pickWeighted(random, List.of()).isEmpty());
        assertTrue(EnchantmentSelectionPolicy.pickWeighted(random, List.of(
            new EnchantmentSelectionPolicy.WeightedCandidate<>("disabled", 0))).isEmpty());
    }
}
