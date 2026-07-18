package com.enviouse.progressivestages.common.rehaul.decision;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import net.minecraft.resources.ResourceLocation;

public record CandidateTrace(ResourceLocation ruleId, RuleEffect effect, int priority,
                             PrioritySource prioritySource, int specificity, boolean selectorMatched,
                             boolean conditionMatched, boolean suppressed, boolean selected,
                             String explanation) {}
