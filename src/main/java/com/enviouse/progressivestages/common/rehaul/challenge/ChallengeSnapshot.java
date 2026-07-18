package com.enviouse.progressivestages.common.rehaul.challenge;

import java.util.List;

public record ChallengeSnapshot(List<ChallengeSessionView> sessions) {
    public ChallengeSnapshot {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
}
