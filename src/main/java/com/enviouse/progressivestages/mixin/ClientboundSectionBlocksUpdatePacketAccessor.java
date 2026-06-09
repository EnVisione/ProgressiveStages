package com.enviouse.progressivestages.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v2.0.3: expose private fields of {@link ClientboundSectionBlocksUpdatePacket}
 * so the send-site rewriter can rebuild a per-player variant when any state
 * needs to be spoofed.
 */
@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface ClientboundSectionBlocksUpdatePacketAccessor {
    @Accessor("sectionPos")
    SectionPos progressivestages$getSectionPos();
}
