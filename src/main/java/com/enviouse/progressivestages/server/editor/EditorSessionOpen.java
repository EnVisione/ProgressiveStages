package com.enviouse.progressivestages.server.editor;

import java.util.UUID;

public record EditorSessionOpen(UUID sessionId, String secret, UUID draftId, long expiresAt,
                                long configurationRevision, long catalogRevision, int protocolVersion) {}
