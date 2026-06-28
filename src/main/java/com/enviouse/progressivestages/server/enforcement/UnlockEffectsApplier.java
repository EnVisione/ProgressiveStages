package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.UnlockEffects;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * v2.4: plays a stage's optional {@code [unlock]} presentation (toast / title / subtitle / sound /
 * particles) to a player when the stage is granted. Every part is skipped when its field is empty,
 * so a stage with no {@code [unlock]} section does nothing extra.
 */
public final class UnlockEffectsApplier {

    private UnlockEffectsApplier() {}

    public static void apply(ServerPlayer player, StageDefinition def) {
        UnlockEffects fx = def.getUnlock();
        if (fx == null) return;

        if (fx.hasTitle()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(TextUtil.parseColorCodes(fx.title())));
            if (!fx.subtitle().isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(TextUtil.parseColorCodes(fx.subtitle())));
            }
        }

        if (fx.hasSound()) {
            ResourceLocation sid = ResourceLocation.tryParse(fx.sound());
            if (sid != null) {
                SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(sid);
                if (se != null) player.playNotifySound(se, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        if (fx.hasParticle() && player.level() instanceof ServerLevel sl) {
            ResourceLocation pid = ResourceLocation.tryParse(fx.particle());
            if (pid != null) {
                var type = BuiltInRegistries.PARTICLE_TYPE.get(pid);
                if (type instanceof ParticleOptions opts) {
                    sl.sendParticles(opts, player.getX(), player.getY() + 1.0, player.getZ(),
                        30, 0.5, 0.6, 0.5, 0.02);
                }
            }
        }

        if (fx.hasToast()) {
            String icon = def.getIcon().map(ResourceLocation::toString).orElse("");
            NetworkHandler.sendUnlockToast(player, def.getDisplayName(), fx.toast(), icon);
        }
    }
}
