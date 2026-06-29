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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @Override
    protected Map<StageId, StageDefinition> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<StageId, StageDefinition> out = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> found =
            resourceManager.listResources(DIR, rl -> rl.getPath().endsWith(".toml"));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation rl = e.getKey();
            String path = rl.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            try (InputStream in = e.getValue().open()) {
                StageFileParser.parse(in, fileName).ifPresent(def -> {
                    if (out.put(def.getId(), def) != null) {
                        LOGGER.warn("[ProgressiveStages] Duplicate datapack stage id {} (from {})", def.getId(), rl);
                    }
                });
            } catch (Exception ex) {
                LOGGER.warn("[ProgressiveStages] Failed to load datapack stage {}: {}", rl, ex.getMessage());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<StageId, StageDefinition> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        StageFileLoader.getInstance().setDatapackStages(prepared);
    }
}
