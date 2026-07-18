package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * v2.5: loads stage definitions shipped INSIDE datapacks, from
 * {@code data/<namespace>/progressivestages/stages/*.toml}.
 *
 * <p>Runs as a reloadable-resource listener, so datapack stages are (re)read both at world load and
 * on {@code /reload}. They're handed to {@link StageFileLoader}, which merges them with the
 * config-folder stages — a config file with the same stage id always wins, so datapacks provide
 * defaults a pack/server can override locally without editing the datapack.
 */
public final class DatapackStageLoader extends SimplePreparableReloadListener<Map<StageId, StageDefinition>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "progressivestages/stages";
    private static final Pattern STAGE_HEADER = Pattern.compile("(?m)^\\s*\\[stage]\\s*(?:#.*)?$");

    @Override
    protected Map<StageId, StageDefinition> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> found =
            resourceManager.listResources(DIR, rl -> rl.getPath().endsWith(".toml"));
        Map<ResourceLocation, String> contents = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try (var stream = entry.getValue().open()) {
                contents.put(entry.getKey(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception ex) {
                LOGGER.warn("[ProgressiveStages] Failed to read datapack stage source {}: {}",
                    entry.getKey(), ex.getMessage());
            }
        }

        Map<StageId, StageDefinition> out = new LinkedHashMap<>();
        Set<ResourceLocation> consumed = new HashSet<>();
        for (Map.Entry<ResourceLocation, String> entry : contents.entrySet()) {
            ResourceLocation identityId = entry.getKey();
            if (!identityId.getPath().endsWith("/stage.toml")) continue;
            String base = identityId.getPath().substring(0, identityId.getPath().length() - "stage.toml".length());
            ResourceLocation rulesId = ResourceLocation.fromNamespaceAndPath(identityId.getNamespace(), base + "rules.toml");
            ResourceLocation progressionId = ResourceLocation.fromNamespaceAndPath(
                identityId.getNamespace(), base + "progression.toml");
            StageFileParser.ParseResult parsed = StagePackageParser.parseContents(
                "datapack:" + identityId,
                identityId.toString(),
                entry.getValue(),
                rulesId.toString(),
                contents.get(rulesId),
                progressionId.toString(),
                contents.get(progressionId));
            consumed.add(identityId);
            if (contents.containsKey(rulesId)) consumed.add(rulesId);
            if (contents.containsKey(progressionId)) consumed.add(progressionId);
            if (parsed.isSuccess()) putStage(out, parsed.getStageDefinition(), identityId);
            else LOGGER.warn("[ProgressiveStages] Failed to load datapack stage package {}: {}",
                identityId, parsed.getErrorMessage());
        }

        for (Map.Entry<ResourceLocation, String> entry : contents.entrySet()) {
            if (consumed.contains(entry.getKey()) || !STAGE_HEADER.matcher(entry.getValue()).find()) continue;
            String path = entry.getKey().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            StageFileParser.ParseResult parsed = StageFileParser.parseText(
                entry.getValue(), fileName, "datapack:" + entry.getKey(), false);
            if (parsed.isSuccess()) putStage(out, parsed.getStageDefinition(), entry.getKey());
            else LOGGER.warn("[ProgressiveStages] Failed to load datapack stage {}: {}",
                entry.getKey(), parsed.getErrorMessage());
        }
        return out;
    }

    private static void putStage(Map<StageId, StageDefinition> stages, StageDefinition stage,
                                 ResourceLocation source) {
        if (stages.put(stage.getId(), stage) != null) {
            LOGGER.warn("[ProgressiveStages] Duplicate datapack stage id {} from {}", stage.getId(), source);
        }
    }

    @Override
    protected void apply(Map<StageId, StageDefinition> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        StageFileLoader.getInstance().setDatapackStages(prepared);
    }
}
