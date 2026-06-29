package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * v3.0: shared client-side helper for the in-world "Requires &lt;stage&gt;" overlays (Jade + WTHIT).
 * Returns the comma-joined names of the gating stages the local player is still missing for a block
 * or entity, or empty when it isn't locked for them. Reads the synced {@link ClientLockCache}
 * (which already honors creative bypass) and {@link ClientStageCache} for owned-stage filtering.
 */
public final class StageLockTooltip {

    private StageLockTooltip() {}

    /** Missing-stage label for a block (via its item form's lock), or empty if accessible/ungated. */
    public static Optional<String> blockRequirement(Block block) {
        if (block == null) return Optional.empty();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId == null) return Optional.empty();
        return label(ClientLockCache.getRequiredStagesForItem(itemId),
            () -> ClientLockCache.playerOwnsAllStagesFor(itemId));
    }

    /** Missing-stage label for an entity type, or empty if accessible/ungated. */
    public static Optional<String> entityRequirement(EntityType<?> type) {
        if (type == null) return Optional.empty();
        ResourceLocation eid = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (eid == null) return Optional.empty();
        return label(ClientLockCache.getRequiredStagesForEntity(eid),
            () -> ClientLockCache.playerOwnsAllStagesForEntity(eid));
    }

    private static Optional<String> label(Set<StageId> gating, BooleanSupplier ownsAll) {
        if (gating.isEmpty() || ownsAll.getAsBoolean()) return Optional.empty();
        List<String> missing = new ArrayList<>();
        for (StageId s : gating) {
            if (!ClientStageCache.hasStage(s)) missing.add(ClientStageCache.getDisplayName(s));
        }
        return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing));
    }
}
