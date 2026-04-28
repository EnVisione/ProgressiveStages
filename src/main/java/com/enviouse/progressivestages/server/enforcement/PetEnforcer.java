package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

import java.util.Optional;

/**
 * Enforces {@code [pets] locked_taming / locked_breeding / locked_commanding}.
 *
 * <p>Semantics:
 * <ul>
 *   <li><b>Taming</b>: the target is a wild tameable that isn't owned. Gate on {@code locked_taming}.</li>
 *   <li><b>Breeding</b>: an already-tamed animal being fed its breeding food. Gate on {@code locked_breeding}.</li>
 *   <li><b>Commanding</b>: an already-tamed animal owned by the player, right-clicked to sit / stand / follow / etc.
 *       Gate on {@code locked_commanding}. A pet owned by someone else falls back to the breeding/taming slot.</li>
 * </ul>
 */
public final class PetEnforcer {

    private PetEnforcer() {}

    public static boolean canInteract(ServerPlayer player, EntityType<?> type, Entity target) {
        if (!StageConfig.isBlockPetInteract()) return true;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;

        PetInteractionKind kind = classify(player, target);
        return !isBlockedFor(player, kind, type);
    }

    public static void notifyLocked(ServerPlayer player, EntityType<?> type, Entity target) {
        PetInteractionKind kind = classify(player, target);
        Optional<StageId> required = primary(player, kind, type);
        required.ifPresent(stage -> ItemEnforcer.notifyLockedWithCooldown(player, stage, kind.label()));
    }

    /**
     * Multi-stage aware: an interaction is blocked if ANY of the slot's gating stages
     * is missing. Falls through to the next slot only if the current slot's gating set
     * is empty (preserves original "fall back to taming/breeding" semantics).
     */
    private static boolean isBlockedFor(ServerPlayer player, PetInteractionKind kind, EntityType<?> type) {
        LockRegistry reg = LockRegistry.getInstance();
        return switch (kind) {
            case COMMANDING -> blockedAtFirstPresent(player,
                reg.getRequiredStagesForPetCommanding(type),
                reg.getRequiredStagesForPetBreeding(type),
                reg.getRequiredStagesForPetTaming(type));
            case BREEDING -> blockedAtFirstPresent(player,
                reg.getRequiredStagesForPetBreeding(type),
                reg.getRequiredStagesForPetTaming(type));
            case TAMING -> blockedAtFirstPresent(player,
                reg.getRequiredStagesForPetTaming(type),
                reg.getRequiredStagesForPetBreeding(type));
        };
    }

    private static Optional<StageId> primary(ServerPlayer player, PetInteractionKind kind, EntityType<?> type) {
        LockRegistry reg = LockRegistry.getInstance();
        return switch (kind) {
            case COMMANDING -> firstMissingFromFirstPresent(player,
                reg.getRequiredStagesForPetCommanding(type),
                reg.getRequiredStagesForPetBreeding(type),
                reg.getRequiredStagesForPetTaming(type));
            case BREEDING -> firstMissingFromFirstPresent(player,
                reg.getRequiredStagesForPetBreeding(type),
                reg.getRequiredStagesForPetTaming(type));
            case TAMING -> firstMissingFromFirstPresent(player,
                reg.getRequiredStagesForPetTaming(type),
                reg.getRequiredStagesForPetBreeding(type));
        };
    }

    @SafeVarargs
    private static boolean blockedAtFirstPresent(ServerPlayer player, java.util.Set<StageId>... slots) {
        StageManager sm = StageManager.getInstance();
        for (java.util.Set<StageId> slot : slots) {
            if (slot != null && !slot.isEmpty()) {
                for (StageId s : slot) if (!sm.hasStage(player, s)) return true;
                return false; // first present slot decides — none missing means allowed
            }
        }
        return false;
    }

    @SafeVarargs
    private static Optional<StageId> firstMissingFromFirstPresent(ServerPlayer player, java.util.Set<StageId>... slots) {
        StageManager sm = StageManager.getInstance();
        for (java.util.Set<StageId> slot : slots) {
            if (slot != null && !slot.isEmpty()) {
                for (StageId s : slot) if (!sm.hasStage(player, s)) return Optional.of(s);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Work out which slot applies. Tamed+owned-by-me → COMMANDING. Tamed but owned by another
     * player → BREEDING (the only meaningful thing a non-owner does is feed it). Wild tameable
     * or any other creature → TAMING.
     */
    private static PetInteractionKind classify(ServerPlayer player, Entity target) {
        if (target instanceof TamableAnimal tame && tame.isTame()) {
            if (tame.getOwnerUUID() != null && tame.getOwnerUUID().equals(player.getUUID())) {
                return PetInteractionKind.COMMANDING;
            }
            return PetInteractionKind.BREEDING;
        }
        if (target instanceof AbstractHorse horse && horse.isTamed()) {
            if (horse.getOwnerUUID() != null && horse.getOwnerUUID().equals(player.getUUID())) {
                return PetInteractionKind.COMMANDING;
            }
            return PetInteractionKind.BREEDING;
        }
        return PetInteractionKind.TAMING;
    }

    private enum PetInteractionKind {
        TAMING,
        BREEDING,
        COMMANDING;

        /**
         * Resolved at call time so users can edit the type label in the config without
         * needing a JVM restart (the enum constants would otherwise freeze the value at
         * class-load).
         */
        String label() {
            return switch (this) {
                case TAMING     -> StageConfig.getMsgTypeLabelPetTaming();
                case BREEDING   -> StageConfig.getMsgTypeLabelPetBreeding();
                case COMMANDING -> StageConfig.getMsgTypeLabelPetCommanding();
            };
        }
    }
}
