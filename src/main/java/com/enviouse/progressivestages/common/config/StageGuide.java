package com.enviouse.progressivestages.common.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Author supplied, inert instructions shown beside a stage in the player guide. */
public record StageGuide(String howToUnlock, String nextSteps, String whereToFind,
                         Recommendation recommendation, boolean enabled) {
    public static final int MAX_TEXT_CODE_POINTS = 2048;
    public static final int MAX_TEXT_BYTES = 8 * 1024;
    public static final StageGuide EMPTY = new StageGuide("", "", "", Recommendation.AUTO, true);

    public StageGuide(String howToUnlock, String nextSteps, String whereToFind, Recommendation recommendation) {
        this(howToUnlock, nextSteps, whereToFind, recommendation, true);
    }

    public enum Recommendation {
        AUTO("auto"), INCLUDE("include"), EXCLUDE("exclude");

        private final String name;

        Recommendation(String name) { this.name = name; }

        public String configName() { return name; }

        public static Recommendation parse(String value) {
            if (value == null || value.isBlank()) return AUTO;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Recommendation candidate : values()) {
                if (candidate.name.equals(normalized)) return candidate;
            }
            throw new IllegalArgumentException("Guide recommendation must be auto, include, or exclude");
        }
    }

    public StageGuide {
        howToUnlock = validate("guide.how_to_unlock", howToUnlock);
        nextSteps = validate("guide.next_steps", nextSteps);
        whereToFind = validate("guide.where_to_find", whereToFind);
        recommendation = recommendation == null ? Recommendation.AUTO : recommendation;
    }

    private static String validate(String field, String value) {
        String text = value == null ? "" : value;
        if (text.codePoints().count() > MAX_TEXT_CODE_POINTS) {
            throw new IllegalArgumentException(field + " exceeds " + MAX_TEXT_CODE_POINTS + " characters");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(field + " exceeds " + MAX_TEXT_BYTES + " UTF-8 bytes");
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t') {
                throw new IllegalArgumentException(field + " contains a control character");
            }
            offset += Character.charCount(codePoint);
        }
        return text;
    }

    public boolean hasText() {
        return !howToUnlock.isBlank() || !nextSteps.isBlank() || !whereToFind.isBlank();
    }
}
