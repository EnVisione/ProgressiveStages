package com.enviouse.progressivestages.common.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Expands the fixed, inert placeholders supported by player guide text. */
public final class StageGuideTemplate {

    public static final String STAGE_NAME = "stage_name";
    public static final String STAGE_REQUIREMENTS = "stage_requirements";
    public static final String REMAINING_REQUIREMENTS = "remaining_requirements";
    public static final String STAGE_PROGRESS = "stage_progress";
    public static final Set<String> TOKENS = Set.of(
        STAGE_NAME, STAGE_REQUIREMENTS, REMAINING_REQUIREMENTS, STAGE_PROGRESS);

    private StageGuideTemplate() {}

    public record Expansion(String text, List<String> unknownTokens) {
        public Expansion {
            text = text == null ? "" : text;
            unknownTokens = unknownTokens == null ? List.of() : List.copyOf(unknownTokens);
        }

        public boolean hasWarnings() {
            return !unknownTokens.isEmpty();
        }
    }

    /**
     * Expand each recognized token once. Double braces emit a literal brace, and unknown tokens
     * remain visible so an author can correct them without losing prose.
     */
    public static Expansion expand(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) return new Expansion("", List.of());
        Map<String, String> replacements = values == null ? Map.of() : values;
        StringBuilder output = new StringBuilder(template.length());
        Set<String> unknown = new LinkedHashSet<>();
        for (int index = 0; index < template.length();) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                output.append('{');
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                output.append('}');
                index += 2;
                continue;
            }
            if (current == '{') {
                int end = template.indexOf('}', index + 1);
                if (end >= 0) {
                    String token = template.substring(index + 1, end);
                    if (TOKENS.contains(token)) output.append(replacements.getOrDefault(token, ""));
                    else {
                        output.append(template, index, end + 1);
                        if (!token.isBlank()) unknown.add(token);
                    }
                    index = end + 1;
                    continue;
                }
            }
            output.append(current);
            index++;
        }
        return new Expansion(output.toString(), new ArrayList<>(unknown));
    }

    /** Returns whether a template contains an active placeholder, ignoring escaped braces. */
    public static boolean usesToken(String template, String expectedToken) {
        if (template == null || template.isEmpty() || !TOKENS.contains(expectedToken)) return false;
        for (int index = 0; index < template.length();) {
            char current = template.charAt(index);
            if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
                index += 2;
                continue;
            }
            if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
                index += 2;
                continue;
            }
            if (current == '{') {
                int end = template.indexOf('}', index + 1);
                if (end >= 0) {
                    if (expectedToken.equals(template.substring(index + 1, end))) return true;
                    index = end + 1;
                    continue;
                }
            }
            index++;
        }
        return false;
    }
}
