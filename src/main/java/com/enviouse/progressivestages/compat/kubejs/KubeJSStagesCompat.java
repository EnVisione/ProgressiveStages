package com.enviouse.progressivestages.compat.kubejs;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.mojang.logging.LogUtils;
import dev.latvian.mods.kubejs.stages.StageCreationEvent;
import dev.latvian.mods.kubejs.stages.Stages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * KubeJS compat: lets scripts treat ProgressiveStages stages as first-class KubeJS
 * stages. After this module is active:
 *
 * <pre>
 *   ServerEvents.loaded(event =&gt; {
 *       event.server.players.forEach(p =&gt; {
 *           if (!p.stages.has("diamond_age")) {
 *               p.stages.add("iron_age")
 *           }
 *       })
 *   })
 *
 *   PlayerEvents.stageAdded("diamond_age", event =&gt; {
 *       event.player.tell("Welcome to the Diamond Age!")
 *   })
 * </pre>
 *
 * <p>Under the hood we implement KubeJS's {@link Stages} interface and delegate every
 * read to {@link StageManager#getStages(ServerPlayer)} and every write to
 * {@link StageManager#grantStage} / {@link StageManager#revokeStage}. KubeJS handles
 * the network sync (fires {@code STAGE_ADDED} / {@code STAGE_REMOVED} to scripts) via
 * its default implementations in {@link Stages#add} / {@link Stages#remove}.
 */
public final class KubeJSStagesCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private KubeJSStagesCompat() {}

    public static void init() {
        NeoForge.EVENT_BUS.register(KubeJSStagesCompat.class);
        LOGGER.info("[ProgressiveStages] KubeJS compat active — scripts can read/grant stages via player.stages.");
    }

    @SubscribeEvent
    public static void onStageCreation(StageCreationEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return; // Client-side stages handled by KubeJS's own sync
        event.setPlayerStages(new ProgressiveStagesBridge(player));
    }

    /**
     * Adapter that fulfills KubeJS's {@link Stages} contract by delegating to our
     * {@link StageManager}. {@code addNoUpdate} / {@code removeNoUpdate} are the
     * low-level hooks KubeJS wraps with its own event firing, so we don't need to
     * fire {@code STAGE_ADDED} / {@code STAGE_REMOVED} here — KubeJS does that.
     */
    private static final class ProgressiveStagesBridge implements Stages {
        private final Player player;

        ProgressiveStagesBridge(Player player) {
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public boolean addNoUpdate(String stage) {
            if (!(player instanceof ServerPlayer sp)) return false;
            StageId id = safeParse(stage);
            if (id == null) return false;
            if (StageManager.getInstance().hasStage(sp, id)) return false;
            StageManager.getInstance().grantStage(sp, id);
            return true;
        }

        @Override
        public boolean removeNoUpdate(String stage) {
            if (!(player instanceof ServerPlayer sp)) return false;
            StageId id = safeParse(stage);
            if (id == null) return false;
            if (!StageManager.getInstance().hasStage(sp, id)) return false;
            StageManager.getInstance().revokeStage(sp, id);
            return true;
        }

        @Override
        public Collection<String> getAll() {
            if (!(player instanceof ServerPlayer sp)) return new ArrayList<>();
            Set<StageId> stages = StageManager.getInstance().getStages(sp);
            return stages.stream().map(StageId::toString).collect(Collectors.toCollection(ArrayList::new));
        }

        private static StageId safeParse(String stage) {
            try {
                return StageId.parse(stage);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
