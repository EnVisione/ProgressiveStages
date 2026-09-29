package com.enviouse.progressivestages.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;

class StageGuideTest {
    @Test
    void keepsUnicodeAndNewlinesButRejectsControls() {
        StageGuide guide = new StageGuide("Mine the 🪨 vein.\nThen return.", "Open the map", "Near spawn", StageGuide.Recommendation.AUTO);
        assertEquals("Mine the 🪨 vein.\nThen return.", guide.howToUnlock());
        assertTrue(guide.enabled());
        assertFalse(new StageGuide("", "", "", StageGuide.Recommendation.AUTO, false).enabled());
        assertThrows(IllegalArgumentException.class,
            () -> new StageGuide("bad" + Character.toString((char) 1) + "text", "", "", StageGuide.Recommendation.AUTO));
    }

    @Test
    void enforcesCodePointAndByteLimits() {
        String tooLong = "x".repeat(StageGuide.MAX_TEXT_CODE_POINTS + 1);
        assertThrows(IllegalArgumentException.class,
            () -> new StageGuide(tooLong, "", "", StageGuide.Recommendation.AUTO));
        assertThrows(IllegalArgumentException.class,
            () -> new StageGuide("😀".repeat(StageGuide.MAX_TEXT_BYTES / 4 + 1), "", "", StageGuide.Recommendation.AUTO));
    }

    @Test
    void expandsFixedTokensOnceAndPreservesEscapes() {
        StageGuideTemplate.Expansion expansion = StageGuideTemplate.expand(
            "{stage_name}. {{stage_name}} {stage_progress} {unknown}",
            Map.of(StageGuideTemplate.STAGE_NAME, "Miner", StageGuideTemplate.STAGE_PROGRESS, "50%"));
        assertEquals("Miner. {stage_name} 50% {unknown}", expansion.text());
        assertEquals(java.util.List.of("unknown"), expansion.unknownTokens());
    }

    @Test
    void detectsOnlyActiveKnownPlaceholders() {
        assertTrue(StageGuideTemplate.usesToken(
            "Complete {stage_requirements} to unlock.", StageGuideTemplate.STAGE_REQUIREMENTS));
        assertFalse(StageGuideTemplate.usesToken(
            "Show {{stage_requirements}} literally.", StageGuideTemplate.STAGE_REQUIREMENTS));
        assertFalse(StageGuideTemplate.usesToken(
            "Complete {stage_name} to unlock.", StageGuideTemplate.STAGE_REQUIREMENTS));
        assertFalse(StageGuideTemplate.usesToken(
            "Complete {unknown} to unlock.", StageGuideTemplate.STAGE_REQUIREMENTS));
    }
}
