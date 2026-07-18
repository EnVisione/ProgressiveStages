package com.enviouse.progressivestages.common.rehaul.lifecycle;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

public record TransitionHistoryEntry(long transactionId, long timestamp, String subject,
                                     ResourceLocation rule, StageId stage,
                                     LifecycleDirection direction, boolean committed,
                                     String explanation) {}
