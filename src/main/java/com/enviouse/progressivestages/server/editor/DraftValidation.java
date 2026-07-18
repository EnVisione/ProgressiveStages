package com.enviouse.progressivestages.server.editor;

import java.util.List;

public record DraftValidation(boolean valid, List<String> errors, List<String> warnings,
                              int stages, long validatedRevision) {
    public DraftValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
