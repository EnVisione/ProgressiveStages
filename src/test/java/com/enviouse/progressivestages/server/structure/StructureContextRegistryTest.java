package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureAccessRequest;
import com.enviouse.progressivestages.common.api.structure.StructureContextProvider;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import com.enviouse.progressivestages.common.api.structure.StructureBounds;
import com.enviouse.progressivestages.common.api.structure.StructureCleanupPolicy;
import com.enviouse.progressivestages.common.api.structure.StructureInstanceKey;
import com.enviouse.progressivestages.common.api.structure.StructureOwnershipScope;
import com.enviouse.progressivestages.common.api.structure.StructureSessionAvailability;
import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureContextRegistryTest {

    @Test
    void duplicateProviderIdsAreRejectedAndUnregisterIsIdempotent() {
        StructureContextRegistry registry = StructureContextRegistry.getInstance();
        ResourceLocation id = ResourceLocation.parse("test:duplicate_provider");
        registry.unregister(id);
        StructureContextProvider provider = new EmptyProvider();

        registry.register(id, provider);

        assertThrows(IllegalArgumentException.class, () -> registry.register(id, provider));
        assertTrue(registry.statuses().stream().anyMatch(status -> status.id().equals(id)));
        assertTrue(registry.unregister(id));
        assertTrue(registry.statuses().stream().noneMatch(status -> status.id().equals(id)));
    }

    @Test
    void directLookupRejectsASpecForAnotherSession() {
        StructureContextRegistry registry = StructureContextRegistry.getInstance();
        ResourceLocation id = ResourceLocation.parse("test:direct_lookup");
        StructureSessionId requested = StructureSessionId.random();
        StructureSessionSpec supplied = spec(id, StructureSessionId.random());
        registry.unregister(id);
        registry.register(id, new SessionProvider(supplied));

        assertTrue(registry.session(id, requested).isEmpty());
        assertEquals(supplied, registry.session(id, supplied.sessionId()).orElseThrow());

        assertTrue(registry.unregister(id));
    }

    private static StructureSessionSpec spec(ResourceLocation providerId,
                                              StructureSessionId sessionId) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("minecraft:overworld"));
        return new StructureSessionSpec(providerId, sessionId,
            new StructureInstanceKey(dimension, ResourceLocation.parse("minecraft:stronghold"),
                BlockPos.ZERO), new StructureBounds(0, 0, 0, 16, 80, 16), UUID.randomUUID(),
            StructureOwnershipScope.PLAYER, StageId.parse("test:access"), Optional.empty(), false,
            StructureCleanupPolicy.REVOKE_ACCESS_ON_COMPLETED_EXIT,
            StructureSessionAvailability.AVAILABLE);
    }

    private static final class EmptyProvider implements StructureContextProvider {
        @Override
        public StructureAccessDecision evaluate(StructureAccessRequest request) {
            return StructureAccessDecision.pass();
        }

        @Override
        public Collection<StructureSessionSpec> sessionsFor(ServerPlayer player) {
            return List.of();
        }

        @Override
        public Optional<StructureSessionSpec> session(StructureSessionId sessionId) {
            return Optional.empty();
        }
    }

    private record SessionProvider(StructureSessionSpec spec) implements StructureContextProvider {
        @Override
        public StructureAccessDecision evaluate(StructureAccessRequest request) {
            return StructureAccessDecision.pass();
        }

        @Override
        public Collection<StructureSessionSpec> sessionsFor(ServerPlayer player) {
            return List.of(spec);
        }

        @Override
        public Optional<StructureSessionSpec> session(StructureSessionId sessionId) {
            return Optional.of(spec);
        }
    }
}
