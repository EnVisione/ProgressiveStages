package com.enviouse.progressivestages.server.editor;

import java.util.UUID;

public record EditorSessionView(UUID sessionId, UUID owner, UUID draftId, long createdAt, long expiresAt,
                                long lastAccessAt, long baseConfigurationRevision, long baseCatalogRevision) {}
