package com.enviouse.progressivestages.common.rehaul;

import com.enviouse.progressivestages.common.rehaul.lifecycle.CompiledLifecycleRule;
import com.enviouse.progressivestages.common.rehaul.challenge.CompiledChallenge;
import com.enviouse.progressivestages.common.rehaul.modifier.CompiledModifier;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityProfile;
import com.enviouse.progressivestages.common.rehaul.state.StageStateDefinition;
import com.enviouse.progressivestages.common.rehaul.template.TemplateDefinition;
import com.enviouse.progressivestages.common.rehaul.value.VariableDefinition;

import java.util.List;
import java.util.Map;

public record CompiledProgression(List<CompiledLifecycleRule> lifecycleRules,
                                  List<CompiledModifier> modifiers,
                                  List<CompiledChallenge> challenges,
                                  List<AffinityProfile> profiles,
                                  List<VariableDefinition> variables,
                                  Map<String, String> formulas,
                                  List<TemplateDefinition> templates,
                                  List<StageStateDefinition> states,
                                  Map<String, Object> extensions) {

    public static final CompiledProgression EMPTY = new CompiledProgression(
        List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());

    public CompiledProgression {
        lifecycleRules = lifecycleRules == null ? List.of() : List.copyOf(lifecycleRules);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        challenges = challenges == null ? List.of() : List.copyOf(challenges);
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        variables = variables == null ? List.of() : List.copyOf(variables);
        formulas = formulas == null ? Map.of() : Map.copyOf(formulas);
        templates = templates == null ? List.of() : List.copyOf(templates);
        states = states == null ? List.of() : List.copyOf(states);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }
}
