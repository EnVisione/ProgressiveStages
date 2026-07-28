package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public final class LootPlayerResolver {

    private LootPlayerResolver() {}

    public static ServerPlayer resolve(LootContext context) {
        ServerPlayer player = resolveResponsiblePlayer(context);
        if (player != null) return player;

        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null && context.getLevel() instanceof ServerLevel level) {
            return NearestPlayerCheck.findNearest(level, origin.x, origin.y, origin.z,
                StageConfig.getMobSpawnCheckRadius());
        }
        return null;
    }

    public static ServerPlayer resolveResponsiblePlayer(LootContext context) {
        ServerPlayer player = resolveEntity(context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER));
        if (player != null) return player;

        player = resolveEntity(context.getParamOrNull(LootContextParams.THIS_ENTITY));
        if (player != null) return player;

        player = resolveParam(context, LootContextParams.ATTACKING_ENTITY);
        if (player != null) return player;

        player = resolveParam(context, LootContextParams.DIRECT_ATTACKING_ENTITY);
        if (player != null) return player;
        return null;
    }

    private static ServerPlayer resolveParam(LootContext context, LootContextParam<Entity> parameter) {
        return resolveEntity(context.getParamOrNull(parameter));
    }

    private static ServerPlayer resolveEntity(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
