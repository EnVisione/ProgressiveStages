package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

/**
 * v2.5: gates opening a villager's trade GUI behind a stage via {@code [professions].locked}.
 *
 * <p>Whereas {@code [trades]} hides individual offers by their RESULT item, this gates by the
 * villager's PROFESSION — a player lacking the stage simply can't trade with (e.g.) a weaponsmith
 * at all. Wandering traders have no profession and are unaffected (use {@code [trades]} for those).
 * The feature is entirely opt-in: with no {@code [professions]} locks declared, every check is a
 * cheap no-op.
 */
public final class VillagerProfessionEnforcer {

    private VillagerProfessionEnforcer() {}

    /** Resolve a villager's profession id, or null for nitwit/none (which never open a trade GUI). */
    private static ResourceLocation professionId(Villager villager) {
        var prof = villager.getVillagerData().getProfession();
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
    }

    /** True if the player may open this villager's trades (owns the gating stage, or none gates it). */
    public static boolean canTradeWith(ServerPlayer player, Villager villager) {
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        ResourceLocation profId = professionId(villager);
        return !LockRegistry.getInstance().isProfessionBlockedFor(player, profId);
    }

    public static void notifyLocked(ServerPlayer player, Villager villager) {
        ResourceLocation profId = professionId(villager);
        Optional<StageId> stage = LockRegistry.getInstance().primaryRestrictingStageForProfession(player, profId);
        stage.ifPresent(s -> ItemEnforcer.notifyLockedWithCooldown(player, s, professionLabel(profId)));
    }

    /** Human-ish label for the chat notice, e.g. "weaponsmith trades". */
    private static String professionLabel(ResourceLocation profId) {
        String path = profId != null ? profId.getPath() : "villager";
        return path + " trades";
    }
}
