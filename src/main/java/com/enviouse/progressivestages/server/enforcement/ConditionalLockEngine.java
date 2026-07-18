package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.compat.ScriptHooks;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates temporary and triggered rules and resolves their priorities.
 */
public final class ConditionalLockEngine {

    public record Decision(ConditionalRule.Effect effect, int priority, ResourceLocation ruleId,
                           StageId ownerStage) {}

    private record ContextSnapshot(long gameTime, ResourceLocation dimension, ResourceLocation biome,
                                   Holder<Biome> biomeHolder, Set<ResourceLocation> structures) {}

    private static volatile Map<ResourceLocation, ConditionalRule> rulesById = Map.of();
    private static volatile Map<ConditionalRule.TargetType, List<ConditionalRule>> rulesByTarget = Map.of();
    private static volatile Map<ConditionalRule.TriggerType, List<ConditionalRule>> rulesByTrigger = Map.of();
    private static volatile List<PrefixEntry> structureContextSelectors = List.of();
    private static volatile List<PrefixEntry> structureTargetSelectors = List.of();

    private static final Map<UUID, Map<ResourceLocation, Long>> activeUntil = new ConcurrentHashMap<>();
    private static final Map<UUID, ContextSnapshot> contextCache = new ConcurrentHashMap<>();

    private ConditionalLockEngine() {}

    public static void rebuild(Collection<StageDefinition> stages) {
        Map<ResourceLocation, ConditionalRule> byId = new LinkedHashMap<>();
        EnumMap<ConditionalRule.TargetType, List<ConditionalRule>> byTarget =
            new EnumMap<>(ConditionalRule.TargetType.class);
        EnumMap<ConditionalRule.TriggerType, List<ConditionalRule>> byTrigger =
            new EnumMap<>(ConditionalRule.TriggerType.class);
        List<PrefixEntry> contextStructures = new ArrayList<>();
        List<PrefixEntry> targetStructures = new ArrayList<>();

        if (stages != null) {
            for (StageDefinition stage : stages) {
                for (ConditionalRule rule : stage.getConditionalRules()) {
                    ConditionalRule previous = byId.putIfAbsent(rule.id(), rule);
                    if (previous != null) {
                        throw new IllegalArgumentException("Duplicate conditional rule id. " + rule.id());
                    }
                    for (ConditionalRule.TargetType type : rule.targets().types()) {
                        byTarget.computeIfAbsent(type, ignored -> new ArrayList<>()).add(rule);
                    }
                    if (rule.isTriggered()) {
                        byTrigger.computeIfAbsent(rule.triggerType(), ignored -> new ArrayList<>()).add(rule);
                    }
                    contextStructures.addAll(rule.context().structures());
                    targetStructures.addAll(rule.targets().included(ConditionalRule.TargetType.STRUCTURE));
                }
            }
        }

        byTarget.replaceAll((key, value) -> List.copyOf(value));
        byTrigger.replaceAll((key, value) -> List.copyOf(value));
        rulesById = Collections.unmodifiableMap(byId);
        rulesByTarget = Collections.unmodifiableMap(byTarget);
        rulesByTrigger = Collections.unmodifiableMap(byTrigger);
        structureContextSelectors = List.copyOf(contextStructures);
        structureTargetSelectors = List.copyOf(targetStructures);
        activeUntil.values().forEach(map -> map.keySet().removeIf(id -> !byId.containsKey(id)));
        contextCache.clear();
    }

    public static void resetRuntimeState() {
        activeUntil.clear();
        contextCache.clear();
        rebuild(List.of());
    }

    public static boolean hasRules(ConditionalRule.TargetType type) {
        List<ConditionalRule> rules = rulesByTarget.get(type);
        return rules != null && !rules.isEmpty()
            || com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().rules()
                .hasRules(compiledCategory(type));
    }

    public static Set<ResourceLocation> ruleIds() {
        return rulesById.keySet();
    }

    public static Optional<ConditionalRule> findRule(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        ResourceLocation exact = ResourceLocation.tryParse(value.trim().toLowerCase(java.util.Locale.ROOT));
        if (exact != null && rulesById.containsKey(exact)) return Optional.of(rulesById.get(exact));
        String suffix = "/" + value.trim().toLowerCase(java.util.Locale.ROOT);
        ConditionalRule found = null;
        for (ConditionalRule rule : rulesById.values()) {
            if (!rule.id().getPath().endsWith(suffix)) continue;
            if (found != null) return Optional.empty();
            found = rule;
        }
        return Optional.ofNullable(found);
    }

    public static Decision resolve(ServerPlayer player, ConditionalRule.TargetType type,
                                   ResourceLocation id, Holder<?> holder, boolean staticBlocked) {
        Decision winner = staticBlocked
            ? new Decision(ConditionalRule.Effect.LOCK, 0, null, null) : null;
        List<ConditionalRule> rules = rulesByTarget.get(type);
        if (player == null || type == null || id == null) return winner;

        long now = System.currentTimeMillis();
        if (rules != null) {
            for (ConditionalRule rule : rules) {
                if (!stageStateMatches(player, rule)) continue;
                if (rule.isTriggered() && !isTimerActive(player.getUUID(), rule.id(), now)) continue;
                if (!contextMatches(player, rule.context())) continue;
                if (!targetMatches(rule, type, id, holder)) continue;
                winner = choose(winner, new Decision(rule.effect(), rule.priority(), rule.id(), rule.ownerStage()));
            }
        }
        var compiled = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().rules()
            .resolve(player, compiledCategory(type), compiledAction(type), id, holder).orElse(null);
        if (compiled != null && compiled.winningEffect() != null && compiled.winningRule() != null) {
            var rule = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().rules()
                .findRule(compiled.winningRule()).orElse(null);
            if (rule != null && (compiled.winningEffect() == com.enviouse.progressivestages.common.rehaul.RuleEffect.LOCK
                    || compiled.winningEffect() == com.enviouse.progressivestages.common.rehaul.RuleEffect.DENY
                    || compiled.winningEffect() == com.enviouse.progressivestages.common.rehaul.RuleEffect.ALLOW
                    || compiled.winningEffect() == com.enviouse.progressivestages.common.rehaul.RuleEffect.UNLOCK)) {
                ConditionalRule.Effect effect = compiled.blocked()
                    ? ConditionalRule.Effect.LOCK : ConditionalRule.Effect.UNLOCK;
                winner = choose(winner, new Decision(effect, rule.priority(), rule.id(), rule.ownerStage()));
            }
        }
        return winner;
    }

    private static String compiledCategory(ConditionalRule.TargetType type) {
        if (type == null) return "";
        return switch (type) {
            case ITEM -> "items";
            case BLOCK -> "blocks";
            case FLUID -> "fluids";
            case ENTITY -> "entities";
            case RECIPE -> "recipes";
            case DIMENSION -> "dimensions";
            case STRUCTURE -> "structures";
            case ABILITY -> "abilities";
        };
    }

    private static String compiledAction(ConditionalRule.TargetType type) {
        if (type == null) return "";
        return switch (type) {
            case ITEM -> "use";
            case BLOCK, FLUID, ENTITY -> "interact";
            case RECIPE -> "craft";
            case DIMENSION, STRUCTURE -> "enter";
            case ABILITY -> "perform";
        };
    }

    public static boolean isBlocked(ServerPlayer player, ConditionalRule.TargetType type,
                                    ResourceLocation id, Holder<?> holder, boolean staticBlocked) {
        Decision decision = resolve(player, type, id, holder, staticBlocked);
        return decision != null && decision.effect() == ConditionalRule.Effect.LOCK;
    }

    public static Decision choose(Decision current, Decision candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        if (candidate.priority() > current.priority()) return candidate;
        if (candidate.priority() < current.priority()) return current;
        if (candidate.effect() == current.effect()) return current;
        return candidate.effect() == ConditionalRule.Effect.LOCK ? candidate : current;
    }

    public static boolean activate(ServerPlayer player, String ruleId, long durationMillis) {
        Optional<ConditionalRule> found = findRule(ruleId);
        return found.isPresent() && activate(player, found.get(), durationMillis, true);
    }

    public static boolean activate(ServerPlayer player, ResourceLocation ruleId, long durationMillis) {
        ConditionalRule rule = rulesById.get(ruleId);
        return rule != null && activate(player, rule, durationMillis, true);
    }

    private static boolean activate(ServerPlayer player, ConditionalRule rule, long durationMillis,
                                    boolean enforceContext) {
        if (player == null || rule == null || !rule.isTriggered()) return false;
        if (!stageStateMatches(player, rule)) return false;
        if (enforceContext && !contextMatches(player, rule.context())) return false;
        long duration = Math.max(1L, durationMillis > 0L ? durationMillis : rule.durationMillis());
        long now = System.currentTimeMillis();
        long expiry = duration >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + duration;
        Map<ResourceLocation, Long> playerRules = activeUntil.computeIfAbsent(
            player.getUUID(), ignored -> new ConcurrentHashMap<>());
        Long current = playerRules.get(rule.id());
        if (current != null && current > now && !rule.refreshDuration()) return false;
        playerRules.put(rule.id(), expiry);
        contextCache.remove(player.getUUID());
        return true;
    }

    public static boolean clear(ServerPlayer player, String ruleId) {
        Optional<ConditionalRule> found = findRule(ruleId);
        if (player == null || found.isEmpty()) return false;
        Map<ResourceLocation, Long> playerRules = activeUntil.get(player.getUUID());
        return playerRules != null && playerRules.remove(found.get().id()) != null;
    }

    public static int clearAll(ServerPlayer player) {
        if (player == null) return 0;
        Map<ResourceLocation, Long> removed = activeUntil.remove(player.getUUID());
        return removed != null ? removed.size() : 0;
    }

    public static Map<ResourceLocation, Long> activeRules(ServerPlayer player) {
        if (player == null) return Map.of();
        long now = System.currentTimeMillis();
        Map<ResourceLocation, Long> values = activeUntil.get(player.getUUID());
        if (values == null || values.isEmpty()) return Map.of();
        values.entrySet().removeIf(entry -> entry.getValue() <= now || !rulesById.containsKey(entry.getKey()));
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Set<ResourceLocation> structureTargetIds(Registry<Structure> registry) {
        if (registry == null || structureTargetSelectors.isEmpty()) return Set.of();
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (ResourceLocation id : registry.keySet()) {
            if (matchesIdOnly(structureTargetSelectors, id)) out.add(id);
        }
        return Set.copyOf(out);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity();
        if (victim instanceof ServerPlayer player && source instanceof LivingEntity opponent) {
            activateMatching(player, ConditionalRule.TriggerType.HURT, opponent.getType());
            activateMatching(player, ConditionalRule.TriggerType.COMBAT, opponent.getType());
        }
        if (source instanceof ServerPlayer player && victim != player) {
            activateMatching(player, ConditionalRule.TriggerType.ATTACK, victim.getType());
            activateMatching(player, ConditionalRule.TriggerType.COMBAT, victim.getType());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player) {
            activateMatching(player, ConditionalRule.TriggerType.KILL, event.getEntity().getType());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        activeUntil.remove(id);
        contextCache.remove(id);
    }

    private static void activateMatching(ServerPlayer player, ConditionalRule.TriggerType type,
                                         EntityType<?> opponent) {
        List<ConditionalRule> rules = rulesByTrigger.get(type);
        if (rules == null || rules.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(opponent);
        Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(opponent);
        for (ConditionalRule rule : rules) {
            if (!rule.triggerEntities().isEmpty()
                    && !matches(rule.triggerEntities(), ConditionalRule.TargetType.ENTITY, id, holder)) continue;
            activate(player, rule, rule.durationMillis(), true);
        }
    }

    private static boolean stageStateMatches(ServerPlayer player, ConditionalRule rule) {
        boolean owned = StageManager.getInstance().hasStage(player, rule.ownerStage());
        return switch (rule.stageState()) {
            case OWNED -> owned;
            case MISSING -> !owned;
            case ALWAYS -> true;
        };
    }

    private static boolean targetMatches(ConditionalRule rule, ConditionalRule.TargetType type,
                                         ResourceLocation id, Holder<?> holder) {
        if (!matches(rule.targets().included(type), type, id, holder)) return false;
        return !matches(rule.targets().excluded(type), type, id, holder);
    }

    private static boolean matches(List<PrefixEntry> selectors, ConditionalRule.TargetType type,
                                   ResourceLocation id, Holder<?> holder) {
        if (selectors == null || selectors.isEmpty() || id == null) return false;
        for (PrefixEntry selector : selectors) {
            if (matches(selector, type, id, holder)) return true;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matches(PrefixEntry selector, ConditionalRule.TargetType type,
                                   ResourceLocation id, Holder<?> holder) {
        ResourceKey registryKey = switch (type) {
            case ITEM -> Registries.ITEM;
            case BLOCK -> Registries.BLOCK;
            case FLUID -> Registries.FLUID;
            case ENTITY -> Registries.ENTITY_TYPE;
            default -> null;
        };
        return registryKey != null
            ? selector.matches(id, (Holder) holder, registryKey)
            : selector.matchesIdOnly(id);
    }

    private static boolean contextMatches(ServerPlayer player, ConditionalRule.Context context) {
        if (context == null || context.isEmpty()) return true;
        ContextSnapshot snapshot = snapshot(player);
        List<Boolean> results = new ArrayList<>();
        if (!context.dimensions().isEmpty()) {
            results.add(matchesIdOnly(context.dimensions(), snapshot.dimension()));
        }
        if (!context.structures().isEmpty()) {
            boolean inside = false;
            for (ResourceLocation structure : snapshot.structures()) {
                if (matchesIdOnly(context.structures(), structure)) {
                    inside = true;
                    break;
                }
            }
            results.add(inside);
        }
        if (!context.biomes().isEmpty()) {
            results.add(matchesBiome(context.biomes(), snapshot.biome(), snapshot.biomeHolder()));
        }
        if (context.minY() != null) results.add(player.getY() >= context.minY());
        if (context.maxY() != null) results.add(player.getY() <= context.maxY());
        if (context.minHealth() != null) results.add(player.getHealth() >= context.minHealth());
        if (context.maxHealth() != null) results.add(player.getHealth() <= context.maxHealth());
        if (!context.requiredStages().isEmpty()) {
            results.add(context.requiredStages().stream().allMatch(stage -> StageManager.getInstance().hasStage(player, stage)));
        }
        if (!context.missingStages().isEmpty()) {
            results.add(context.missingStages().stream().allMatch(stage -> !StageManager.getInstance().hasStage(player, stage)));
        }
        if (!context.effects().isEmpty()) {
            boolean all = true;
            for (ResourceLocation effectId : context.effects()) {
                var effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
                if (effect == null || !player.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect))) {
                    all = false;
                    break;
                }
            }
            results.add(all);
        }
        if (context.sneaking() != null) results.add(player.isShiftKeyDown() == context.sneaking());
        if (context.sprinting() != null) results.add(player.isSprinting() == context.sprinting());
        if (context.swimming() != null) results.add(player.isSwimming() == context.swimming());
        if (context.riding() != null) results.add(player.isPassenger() == context.riding());
        if (context.onGround() != null) results.add(player.onGround() == context.onGround());
        if (!context.scriptCondition().isEmpty()) {
            results.add(ScriptHooks.evalCondition(context.scriptCondition(), player));
        }
        if (results.isEmpty()) return true;
        return context.mode() == ConditionalRule.ContextMode.ALL
            ? results.stream().allMatch(Boolean::booleanValue)
            : results.stream().anyMatch(Boolean::booleanValue);
    }

    private static ContextSnapshot snapshot(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        ContextSnapshot cached = contextCache.get(player.getUUID());
        if (cached != null && cached.gameTime() == gameTime) return cached;
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(ResourceKey::location)
            .orElse(ResourceLocation.withDefaultNamespace("plains"));
        Set<ResourceLocation> structures = currentStructures(player);
        ContextSnapshot created = new ContextSnapshot(gameTime, player.level().dimension().location(),
            biomeId, biomeHolder, structures);
        contextCache.put(player.getUUID(), created);
        return created;
    }

    private static Set<ResourceLocation> currentStructures(ServerPlayer player) {
        if (structureContextSelectors.isEmpty() || !(player.level() instanceof ServerLevel level)) return Set.of();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> found = new LinkedHashSet<>();
        for (ResourceLocation id : registry.keySet()) {
            if (!matchesIdOnly(structureContextSelectors, id)) continue;
            Structure structure = registry.get(id);
            if (structure == null) continue;
            StructureStart start = level.structureManager().getStructureAt(player.blockPosition(), structure);
            if (start != StructureStart.INVALID_START && start.getBoundingBox().isInside(player.blockPosition())) {
                found.add(id);
            }
        }
        return Set.copyOf(found);
    }

    private static boolean matchesIdOnly(List<PrefixEntry> selectors, ResourceLocation id) {
        if (id == null) return false;
        for (PrefixEntry selector : selectors) if (selector.matchesIdOnly(id)) return true;
        return false;
    }

    private static boolean matchesBiome(List<PrefixEntry> selectors, ResourceLocation id,
                                        Holder<Biome> holder) {
        if (id == null) return false;
        for (PrefixEntry selector : selectors) {
            if (selector.matches(id, holder, Registries.BIOME)) return true;
        }
        return false;
    }

    private static boolean isTimerActive(UUID player, ResourceLocation rule, long now) {
        Map<ResourceLocation, Long> values = activeUntil.get(player);
        if (values == null) return false;
        Long expiry = values.get(rule);
        if (expiry == null) return false;
        if (expiry > now) return true;
        values.remove(rule);
        if (values.isEmpty()) activeUntil.remove(player);
        return false;
    }
}
