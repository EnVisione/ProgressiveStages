package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.structure.StructureAccessDecision;
import com.enviouse.progressivestages.common.api.structure.StructureAccessRequest;
import com.enviouse.progressivestages.common.api.structure.StructureContextProvider;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
