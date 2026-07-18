package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.rehaul.modifier.AttributeOperation;
import com.enviouse.progressivestages.common.rehaul.modifier.CompiledModifier;
import com.enviouse.progressivestages.common.rehaul.modifier.ContextualItem;
import com.enviouse.progressivestages.common.rehaul.modifier.ItemContext;
import com.enviouse.progressivestages.common.rehaul.modifier.ModifierResolver;
import com.enviouse.progressivestages.common.rehaul.modifier.ResolvedModifier;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.rehaul.MinecraftConditionContextFactory;
import com.enviouse.progressivestages.server.rehaul.RehaulRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContextualModifierApplier {

    private static final Map<UUID, Map<ResourceLocation, ResourceLocation>> applied = new ConcurrentHashMap<>();

    private ContextualModifierApplier() {}

    public static void reconcile(ServerPlayer player) {
        RehaulRuntime runtime = RehaulRuntime.get();
        List<CompiledModifier> rules = runtime.snapshot().stages().values().stream()
            .flatMap(stage -> stage.progression().modifiers().stream()).toList();
        if (rules.isEmpty() && !applied.containsKey(player.getUUID())) return;
        ModifierResolver resolver = new ModifierResolver(SelectorMatcherRegistry.get(), runtime.conditionEvaluator());
        var condition = MinecraftConditionContextFactory.create(player, runtime, Set.of("inventory"));
        List<ResolvedModifier> desired = resolver.resolve(rules, inventory(player),
            StageManager.getInstance().getStages(player), condition);
        Map<ResourceLocation, ResourceLocation> previous = applied.getOrDefault(player.getUUID(), Map.of());
        Map<ResourceLocation, ResourceLocation> next = new HashMap<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : previous.entrySet()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(entry.getValue()).orElse(null);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(entry.getKey());
        }
        for (ResolvedModifier modifier : desired) {
            for (int index = 0; index < modifier.attributes().size(); index++) {
                var change = modifier.attributes().get(index);
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(change.attribute()).orElse(null);
                AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
                if (instance == null) continue;
                ResourceLocation modifierId = modifierId(modifier.stableId(), change.attribute(), index);
                double amount = Math.max(change.minimum(), Math.min(change.maximum(), change.amount()));
                AttributeModifier.Operation operation = switch (change.operation()) {
                    case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
                    case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                };
                instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
                next.put(modifierId, change.attribute());
            }
            for (var effect : modifier.effects()) {
                var value = BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect()).orElse(null);
                if (value != null) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(value,
                    effect.durationTicks(), effect.amplifier(), false, effect.particles(), effect.icon()));
            }
        }
        if (next.isEmpty()) applied.remove(player.getUUID());
        else applied.put(player.getUUID(), Map.copyOf(next));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    public static double transform(ServerPlayer player, ResourceLocation type, double value) {
        RehaulRuntime runtime = RehaulRuntime.get();
        List<CompiledModifier> rules = runtime.snapshot().stages().values().stream()
            .flatMap(stage -> stage.progression().modifiers().stream()).toList();
        ModifierResolver resolver = new ModifierResolver(SelectorMatcherRegistry.get(), runtime.conditionEvaluator());
        var condition = MinecraftConditionContextFactory.create(player, runtime, Set.of(type.toString()));
        List<ResolvedModifier> modifiers = resolver.resolve(rules, inventory(player),
            StageManager.getInstance().getStages(player), condition);
        double result = value;
        for (ResolvedModifier modifier : modifiers) {
            for (var transform : modifier.transforms()) if (transform.type().equals(type)) result = transform.apply(result);
        }
        ItemStack selected = player.getMainHandItem();
        if (!selected.isEmpty()) {
            var affinity = runtime.affinity(player, target(selected)).orElse(null);
            if (affinity != null) {
                for (var transform : affinity.transforms()) if (transform.type().equals(type)) result = transform.apply(result);
            }
        }
        return result;
    }

    public static List<ResolvedModifier> preview(ServerPlayer player) {
        RehaulRuntime runtime = RehaulRuntime.get();
        List<CompiledModifier> rules = runtime.snapshot().stages().values().stream()
            .flatMap(stage -> stage.progression().modifiers().stream()).toList();
        ModifierResolver resolver = new ModifierResolver(SelectorMatcherRegistry.get(), runtime.conditionEvaluator());
        return resolver.resolve(rules, inventory(player), StageManager.getInstance().getStages(player),
            MinecraftConditionContextFactory.create(player, runtime, Set.of("preview")));
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        Map<ResourceLocation, ResourceLocation> previous = applied.remove(player.getUUID());
        if (previous == null) return;
        previous.forEach((modifier, attributeId) -> BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).ifPresent(attribute -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(modifier);
        }));
    }

    public static void reset() { applied.clear(); }

    private static List<ContextualItem> inventory(ServerPlayer player) {
        List<ContextualItem> output = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            Set<ItemContext> contexts = new LinkedHashSet<>();
            contexts.add(ItemContext.INVENTORY);
            if (slot < 9) contexts.add(ItemContext.HOTBAR);
            if (slot == inventory.selected) {
                contexts.add(ItemContext.SELECTED_HOTBAR);
                contexts.add(ItemContext.MAIN_HAND);
                contexts.add(ItemContext.EITHER_HAND);
            }
            if (slot == 40) {
                contexts.add(ItemContext.OFF_HAND);
                contexts.add(ItemContext.EITHER_HAND);
            }
            if (slot >= 36 && slot <= 39) contexts.add(ItemContext.EQUIPMENT);
            output.add(new ContextualItem(target(stack), stack.getCount(), contexts));
        }
        return List.copyOf(output);
    }

    public static SelectorTarget target(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        BuiltInRegistries.ITEM.getHolder(id).ifPresent(holder ->
            holder.tags().forEach(tag -> tags.add(tag.location())));
        Map<String, Object> properties = Map.of("rarity", stack.getRarity().name().toLowerCase(java.util.Locale.ROOT));
        return new SelectorTarget(id, BuiltInRegistries.ITEM.key().location(), tags, properties);
    }

    private static ResourceLocation modifierId(ResourceLocation base, ResourceLocation attribute, int index) {
        try {
            String raw = base + "|" + attribute + "|" + index;
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
            return ResourceLocation.fromNamespaceAndPath("progressivestages", "contextual/" + hash);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
