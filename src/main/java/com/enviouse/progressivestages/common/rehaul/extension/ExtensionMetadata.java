package com.enviouse.progressivestages.common.rehaul.extension;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ExtensionMetadata(ResourceLocation id, ExtensionKind kind, String title,
                                String description, String icon, List<ExtensionArgument> arguments,
                                Set<String> scopes, Set<String> eventInterests,
                                Set<ResourceLocation> capabilities, List<Map<String, Object>> examples,
                                MissingCallbackPolicy missingCallbackPolicy, boolean legacy) {
    public ExtensionMetadata {
        if (id == null) throw new IllegalArgumentException("Extension id cannot be null");
        if (kind == null) throw new IllegalArgumentException("Extension kind cannot be null");
        title = title == null || title.isBlank() ? id.toString() : title;
        description = description == null ? "" : description;
        icon = icon == null ? "" : icon;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        eventInterests = eventInterests == null ? Set.of() : Set.copyOf(eventInterests);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        examples = examples == null ? List.of() : examples.stream().map(Map::copyOf).toList();
        missingCallbackPolicy = missingCallbackPolicy == null ? MissingCallbackPolicy.REJECT : missingCallbackPolicy;
    }

    public static ExtensionMetadata legacy(ResourceLocation id, ExtensionKind kind) {
        MissingCallbackPolicy policy = switch (kind) {
            case CONDITION, SELECTOR -> MissingCallbackPolicy.FALSE;
            case VALUE, COUNTER, CHALLENGE_MEASURE -> MissingCallbackPolicy.ZERO;
            default -> MissingCallbackPolicy.NO_OP;
        };
        return new ExtensionMetadata(id, kind, id.toString(), "Legacy registration without editor metadata",
            "", List.of(), Set.of("player"), Set.of("manual"), Set.of(), List.of(), policy, true);
    }
}
