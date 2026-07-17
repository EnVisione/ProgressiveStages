package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;

/**
 * Enforces {@code [[mobs.replacements]]} — when a gated mob would spawn, substitute
 * a different mob at the same position. Runs from {@code FinalizeSpawnEvent} with
 * lower priority than {@link MobSpawnEnforcer} so a configured replacement takes
 * precedence over a plain cancel.
 *
 * <p>Flow: if the spawning mob matches a replacement's {@code target} PrefixEntry
 * <em>and</em> the nearest player lacks the replacement's required stage, we cancel
 * the original spawn and enqueue the replacement mob at the same coords. The
 * replacement is {@code finalizeSpawn}ed itself so it picks up biome-appropriate
 * equipment/brain setup.
 */
public final class MobReplacementEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> finalizingReplacement =
        ThreadLocal.withInitial(() -> false);

    private MobReplacementEnforcer() {}

    /** Returns true when the caller must cancel the original spawn. */
    public static boolean tryReplace(Mob mob, ServerLevelAccessor levelAccess,
                                     double x, double y, double z, MobSpawnType spawnType) {
        if (finalizingReplacement.get()) return false;
        if (!StageConfig.isBlockMobReplacements()) return false;
        if (!(levelAccess.getLevel() instanceof ServerLevel level)) return false;

        EntityType<?> originalType = mob.getType();
        ResourceLocation originalId = BuiltInRegistries.ENTITY_TYPE.getKey(originalType);
        if (originalId == null) return false;

        double radius = StageConfig.getMobSpawnCheckRadius();

        for (LockRegistry.MobReplacementEntry entry : LockRegistry.getInstance().getMobReplacements()) {
            if (!entry.target.matches(originalId, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(originalType),
                    net.minecraft.core.registries.Registries.ENTITY_TYPE)) {
                continue;
            }
            // Player-stage gate.
            // Note: a single MobReplacementEntry has exactly one required stage by design
            // (the entry was registered against the owning stage), so single-stage check is
            // semantically correct here. If multiple replacement entries with different
            // stages match the same target, the FIRST applicable replacement wins (preserves
            // insertion order semantics).
            if (!NearestPlayerCheck.nearestPlayerLacks(level, x, y, z, radius, entry.requiredStage)) {
                continue;
            }

            // Found a replacement that applies — try to spawn the new mob.
            EntityType<?> replacementType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(entry.replaceWith).orElse(null);
            if (replacementType == null) {
                LOGGER.warn("[ProgressiveStages] Replacement entity not found: {}", entry.replaceWith);
                return true;
            }
            Entity replacement = replacementType.create(level);
            if (replacement == null) return true;
            replacement.moveTo(x, y, z, mob.getYRot(), mob.getXRot());

            // Invalid replacements cancel the original spawn.
            if (replacement instanceof Mob replacementMob) {
                if (!replacementMob.checkSpawnRules(level, spawnType)
                        || !replacementMob.checkSpawnObstruction(level)) {
                    replacement.discard();
                    return true;
                }
                finalizingReplacement.set(true);
                try {
                    net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(replacementMob, level,
                        level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                        spawnType, null);
                } finally {
                    finalizingReplacement.remove();
                }
                if (replacementMob.isSpawnCancelled()) {
                    replacement.discard();
                    return true;
                }
            }
            level.addFreshEntity(replacement);
            return true;
        }
        return false;
    }
}
