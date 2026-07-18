package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record ItemUseDecision(boolean allowed, Optional<StageId> stage, Category category,
                              Polarity polarity, Optional<ResourceLocation> providerId,
                              Optional<StructureSessionId> sessionId, Reason reason,
                              String message) {
    public enum Category {
        ITEM_USE
    }

    public enum Polarity {
        NONE,
        MISSING_STAGE,
        PRESENT_IN_CONTEXT
    }

    public enum Reason {
        ALLOWED,
        EMPTY_STACK,
        SPECTATOR,
        CREATIVE_BYPASS,
        CATEGORY_DISABLED,
        MISSING_STAGE,
        ACTIVE_STRUCTURE_LOCK,
        STRUCTURE_CONTEXT_DENIED
    }

    public ItemUseDecision {
        stage = stage == null ? Optional.empty() : stage;
        category = category == null ? Category.ITEM_USE : category;
        polarity = polarity == null ? Polarity.NONE : polarity;
        providerId = providerId == null ? Optional.empty() : providerId;
        sessionId = sessionId == null ? Optional.empty() : sessionId;
        reason = reason == null ? Reason.ALLOWED : reason;
        message = message == null ? "" : message;
    }

    public static ItemUseDecision allowed(Reason reason) {
        return new ItemUseDecision(true, Optional.empty(), Category.ITEM_USE, Polarity.NONE,
            Optional.empty(), Optional.empty(), reason, "");
    }
}
