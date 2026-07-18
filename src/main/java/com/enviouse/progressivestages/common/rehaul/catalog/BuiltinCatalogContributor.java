package com.enviouse.progressivestages.common.rehaul.catalog;

import com.enviouse.progressivestages.server.loader.StageFileLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BuiltinCatalogContributor implements CatalogContributor {

    private static final ResourceLocation ID = catalog("builtin");
    private static final ResourceLocation PREFIX_ID = capability("prefix/id");
    private static final ResourceLocation PREFIX_MOD = capability("prefix/mod");
    private static final ResourceLocation PREFIX_NAME = capability("prefix/name");
    private static final ResourceLocation PREFIX_TAG = capability("prefix/tag");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void contribute(CatalogBuildContext context, CatalogCollector collector) {
        Map<String, String> modNames = addMods(collector);
        Map<ResourceLocation, Registry<?>> registries = new LinkedHashMap<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            registries.put(registry.key().location(), registry);
        }
        if (context.server() != null) {
            context.server().registryAccess().registries().forEach(entry ->
                registries.put(entry.key().location(), entry.value()));
        }
        for (Map.Entry<ResourceLocation, Registry<?>> entry : registries.entrySet()) {
            addRegistry(entry.getKey(), entry.getValue(), modNames, collector,
                BuiltInRegistries.REGISTRY.containsKey(entry.getKey()) ? "static_registry" : "dynamic_registry");
        }
        if (context.server() != null) {
            addRecipes(context, collector, modNames);
            addAdvancements(context, collector, modNames);
            addReloadResources(context, collector, modNames);
            addScoreboard(context, collector);
            addStages(collector);
            addExtensions(collector);
        }
    }

    private static Map<String, String> addMods(CatalogCollector collector) {
        Map<String, String> names = new LinkedHashMap<>();
        for (var mod : ModList.get().getMods()) {
            names.put(mod.getModId(), mod.getDisplayName());
            CatalogEntry entry = new CatalogEntry(catalog("mods"), mod.getModId(), null, mod.getModId(),
                mod.getDisplayName(), "", "loaded_mod", mod.getModId(), mod.getDisplayName(), List.of(),
                Set.of(capability("loaded")), Map.of(
                    "version", mod.getVersion().toString(),
                    "description", mod.getDescription()));
            collector.add(catalog("mods"), entry);
        }
        return Map.copyOf(names);
    }

    private static <T> void addRegistry(ResourceLocation registryId, Registry<T> registry,
                                        Map<String, String> modNames, CatalogCollector collector,
                                        String sourceType) {
        ResourceLocation genericCatalog = catalog("registry/" + registryId.getNamespace() + "/" + registryId.getPath());
        ResourceLocation alias = alias(registryId);
        Map<ResourceLocation, List<ResourceLocation>> tags = tagsByEntry(registry);
        Set<ResourceLocation> capabilities = new LinkedHashSet<>(Set.of(PREFIX_ID, PREFIX_MOD, PREFIX_NAME));
        if (!tags.isEmpty()) capabilities.add(PREFIX_TAG);

        CatalogEntry registryEntry = new CatalogEntry(catalog("registries"), registryId.toString(), null,
            registryId.getNamespace(), registryId.toString(), "", sourceType, registryId.getNamespace(),
            modNames.getOrDefault(registryId.getNamespace(), ""), List.of(), Set.copyOf(capabilities),
            Map.of("entry_count", registry.size()));
        collector.add(catalog("registries"), registryEntry);

        for (ResourceLocation key : registry.keySet()) {
            T value = registry.get(key);
            CatalogEntry entry = new CatalogEntry(genericCatalog, key.toString(), registryId,
                key.getNamespace(), key.toString(), translationKey(value), sourceType,
                key.getNamespace(), modNames.getOrDefault(key.getNamespace(), ""),
                tags.getOrDefault(key, List.of()), Set.copyOf(capabilities), Map.of());
            collector.add(genericCatalog, entry);
            if (alias != null) {
                collector.add(alias, new CatalogEntry(alias, entry.key(), registryId, entry.namespace(),
                    entry.label(), entry.translationKey(), entry.sourceType(), entry.modId(), entry.modName(),
                    entry.tags(), entry.capabilities(), entry.metadata()));
            }
        }
    }

    private static <T> Map<ResourceLocation, List<ResourceLocation>> tagsByEntry(Registry<T> registry) {
        Map<ResourceLocation, List<ResourceLocation>> output = new HashMap<>();
        registry.getTags().forEach(pair -> {
            ResourceLocation tag = pair.getFirst().location();
            for (Holder<T> holder : pair.getSecond()) {
                holder.unwrapKey().ifPresent(key -> output
                    .computeIfAbsent(key.location(), ignored -> new ArrayList<>()).add(tag));
            }
        });
        output.replaceAll((key, value) -> value.stream().distinct().sorted().toList());
        return Map.copyOf(output);
    }

    private static void addRecipes(CatalogBuildContext context, CatalogCollector collector,
                                   Map<String, String> modNames) {
        ResourceLocation catalog = catalog("recipes");
        context.server().getRecipeManager().getRecipes().forEach(holder -> {
            ResourceLocation id = holder.id();
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            collector.add(catalog, new CatalogEntry(catalog, id.toString(), Registries.RECIPE.location(),
                id.getNamespace(), id.toString(), "", "recipe", id.getNamespace(),
                modNames.getOrDefault(id.getNamespace(), ""), List.of(),
                Set.of(PREFIX_ID, PREFIX_MOD, PREFIX_NAME), Map.of("recipe_type", type.toString())));
        });
    }

    private static void addAdvancements(CatalogBuildContext context, CatalogCollector collector,
                                        Map<String, String> modNames) {
        ResourceLocation catalog = catalog("advancements");
        context.server().getAdvancements().getAllAdvancements().forEach(holder -> {
            ResourceLocation id = holder.id();
            collector.add(catalog, new CatalogEntry(catalog, id.toString(), null, id.getNamespace(),
                id.toString(), "", "advancement", id.getNamespace(),
                modNames.getOrDefault(id.getNamespace(), ""), List.of(),
                Set.of(PREFIX_ID, PREFIX_MOD, PREFIX_NAME), Map.of()));
        });
    }

    private static void addScoreboard(CatalogBuildContext context, CatalogCollector collector) {
        ResourceLocation catalog = catalog("scoreboard_objectives");
        for (String name : context.server().getScoreboard().getObjectiveNames()) {
            collector.add(catalog, new CatalogEntry(catalog, name, null, "", name, "", "scoreboard",
                "", "", List.of(), Set.of(), Map.of()));
        }
    }

    private static void addReloadResources(CatalogBuildContext context, CatalogCollector collector,
                                           Map<String, String> modNames) {
        addReloadResourceType(context, collector, modNames, "loot_table", "loot_tables");
        addReloadResourceType(context, collector, modNames, "predicate", "predicates");
        addReloadResourceType(context, collector, modNames, "function", "functions");
    }

    private static void addReloadResourceType(CatalogBuildContext context, CatalogCollector collector,
                                              Map<String, String> modNames, String folder, String catalogPath) {
        ResourceLocation catalog = catalog(catalogPath);
        context.server().getResourceManager().listResources(folder, id -> id.getPath().endsWith(".json"))
            .keySet().forEach(resource -> {
                String prefix = folder + "/";
                String path = resource.getPath().startsWith(prefix)
                    ? resource.getPath().substring(prefix.length()) : resource.getPath();
                if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), path);
                collector.add(catalog, new CatalogEntry(catalog, id.toString(), null, id.getNamespace(),
                    id.toString(), "", "reload_resource", id.getNamespace(),
                    modNames.getOrDefault(id.getNamespace(), ""), List.of(),
                    Set.of(PREFIX_ID, PREFIX_MOD, PREFIX_NAME), Map.of("folder", folder)));
            });
    }

    private static void addExtensions(CatalogCollector collector) {
        for (var metadata : com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadataRegistry
                .get().snapshot().registrations()) {
            ResourceLocation catalog = catalog(switch (metadata.kind()) {
                case CONDITION -> "conditions";
                case VALUE -> "values";
                case ACTION, REWARD -> "actions";
                case COUNTER -> "counters";
                case EVENT -> "events";
                case SELECTOR -> "selectors";
                case CONTEXT -> "contexts";
                case POLICY -> "policies";
                case MODIFIER -> "modifiers";
                case CHALLENGE_MEASURE -> "challenge_measures";
                case CATALOG -> "catalog_providers";
                case VIEWER_PRESENTATION -> "viewer_presentations";
                case DISPLAY -> "displays";
            });
            collector.add(catalog, new CatalogEntry(catalog, metadata.id().toString(), null,
                metadata.id().getNamespace(), metadata.title(), "", "extension", metadata.id().getNamespace(),
                "", List.of(), Set.copyOf(metadata.capabilities()), Map.of(
                    "description", metadata.description(),
                    "kind", metadata.kind().name().toLowerCase(java.util.Locale.ROOT),
                    "legacy", metadata.legacy(),
                    "argument_count", metadata.arguments().size())));
        }
    }

    private static void addStages(CatalogCollector collector) {
        ResourceLocation catalog = catalog("stages");
        for (var stage : StageFileLoader.getInstance().getAllStages()) {
            ResourceLocation id = stage.getId().getResourceLocation();
            collector.add(catalog, new CatalogEntry(catalog, id.toString(), null, id.getNamespace(),
                stage.getDisplayName(), "", "stage", id.getNamespace(), "", List.of(),
                Set.of(PREFIX_ID, PREFIX_MOD, PREFIX_NAME), Map.of(
                    "description", stage.getDescription(),
                    "priority", stage.getPriority(),
                    "category", stage.getCategory())));
        }
    }

    private static String translationKey(Object value) {
        if (value instanceof Item item) return item.getDescriptionId();
        if (value instanceof Block block) return block.getDescriptionId();
        if (value instanceof EntityType<?> entity) return entity.getDescriptionId();
        if (value instanceof MobEffect effect) return effect.getDescriptionId();
        return "";
    }

    private static ResourceLocation alias(ResourceLocation registryId) {
        if (registryId.equals(Registries.ITEM.location())) return catalog("items");
        if (registryId.equals(Registries.BLOCK.location())) return catalog("blocks");
        if (registryId.equals(Registries.FLUID.location())) return catalog("fluids");
        if (registryId.equals(Registries.ENTITY_TYPE.location())) return catalog("entities");
        if (registryId.equals(Registries.MOB_EFFECT.location())) return catalog("effects");
        if (registryId.equals(Registries.POTION.location())) return catalog("potions");
        if (registryId.equals(Registries.ATTRIBUTE.location())) return catalog("attributes");
        if (registryId.equals(Registries.SOUND_EVENT.location())) return catalog("sounds");
        if (registryId.equals(Registries.PARTICLE_TYPE.location())) return catalog("particles");
        if (registryId.equals(Registries.VILLAGER_PROFESSION.location())) return catalog("professions");
        if (registryId.equals(Registries.VILLAGER_TYPE.location())) return catalog("villager_types");
        if (registryId.equals(Registries.MENU.location())) return catalog("menus");
        if (registryId.equals(Registries.RECIPE_TYPE.location())) return catalog("recipe_types");
        if (registryId.equals(Registries.RECIPE_SERIALIZER.location())) return catalog("recipe_serializers");
        if (registryId.equals(Registries.CREATIVE_MODE_TAB.location())) return catalog("creative_tabs");
        if (registryId.equals(Registries.BIOME.location())) return catalog("biomes");
        if (registryId.equals(Registries.STRUCTURE.location())) return catalog("structures");
        if (registryId.equals(Registries.DIMENSION.location())) return catalog("dimensions");
        if (registryId.equals(Registries.DIMENSION_TYPE.location())) return catalog("dimension_types");
        if (registryId.equals(Registries.ENCHANTMENT.location())) return catalog("enchantments");
        if (registryId.equals(Registries.DAMAGE_TYPE.location())) return catalog("damage_types");
        if (registryId.equals(Registries.CONFIGURED_FEATURE.location())) return catalog("configured_features");
        if (registryId.equals(Registries.PLACED_FEATURE.location())) return catalog("placed_features");
        return null;
    }

    private static ResourceLocation catalog(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }

    private static ResourceLocation capability(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }
}
