package com.enviouse.progressivestages.common.config;

import java.util.LinkedHashSet;
import java.util.List;

/** Chat restrictions supplied by one stage. Restrictions end when the stage is owned. */
public record StageChatOptions(boolean lockAccess, List<String> maskTerms) {
    public static final StageChatOptions NONE = new StageChatOptions(false, List.of());
    public static final int MAX_TERMS = 64;
    public static final int MAX_TERM_LENGTH = 80;

    public StageChatOptions {
        if (maskTerms == null) maskTerms = List.of();
        if (maskTerms.size() > MAX_TERMS) throw new IllegalArgumentException("chat.mask_terms exceeds " + MAX_TERMS + " terms");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : maskTerms) {
            if (raw == null || raw.isBlank() || !raw.equals(raw.trim()) || raw.length() > MAX_TERM_LENGTH
                    || raw.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("chat.mask_terms must contain nonblank text without controls, at most "
                    + MAX_TERM_LENGTH + " characters per term");
            }
            if (!unique.add(raw.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("chat.mask_terms contains duplicate terms");
            }
        }
        maskTerms = List.copyOf(maskTerms);
    }
}
