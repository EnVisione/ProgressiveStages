package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side holder for the per-stage GUI snapshot the server pushes when the player opens the
 * stage-tree GUI (keybind or {@code /stage gui}): each stage's live {@code [[triggers]]} progress
 * plus a preview of the items it unlocks. Decoupled from the network record types so the
 * {@code StageTreeScreen} reads a clean client model.
 */
public final class ClientTriggerProgress {

    public record Cond(String label, int current, int threshold, boolean satisfied) {
        public float fraction() {
            if (threshold <= 0) return satisfied ? 1f : 0f;
            return Math.min(1f, (float) current / (float) threshold);
        }
    }

    public record Rule(String mode, String description, boolean satisfied, List<Cond> conditions) {
        /** This rule's completion fraction (any_of = best condition, all_of = average). */
        public float fraction() {
            if (conditions.isEmpty()) return satisfied ? 1f : 0f;
            if ("any_of".equals(mode)) {
                float best = 0f;
                for (Cond c : conditions) best = Math.max(best, c.fraction());
                return best;
            }
            float sum = 0f;
            for (Cond c : conditions) sum += c.fraction();
            return sum / conditions.size();
        }
    }

    public record StageData(List<Rule> rules, List<ResourceLocation> unlockSample, int unlockTotal) {
        public static final StageData EMPTY = new StageData(List.of(), List.of(), 0);

        public boolean hasTriggers() { return !rules.isEmpty(); }

        /** Overall completion toward unlocking via triggers (best rule), or -1 if no triggers. */
        public float percent() {
            if (rules.isEmpty()) return -1f;
            float best = 0f;
            for (Rule r : rules) best = Math.max(best, r.fraction());
            return Math.min(1f, best);
        }
    }

    private static final Map<StageId, StageData> DATA = new HashMap<>();

    private ClientTriggerProgress() {}

    /** Store the latest snapshot and open (or refresh) the stage-tree screen. */
    public static void acceptAndOpen(List<NetworkHandler.StageProgress> stages) {
        accept(stages);
        com.enviouse.progressivestages.client.gui.StageTreeScreen.open();
    }

    public static void accept(List<NetworkHandler.StageProgress> stages) {
        DATA.clear();
        if (stages == null) return;
        for (NetworkHandler.StageProgress sp : stages) {
            StageId id = StageId.fromResourceLocation(sp.stageId());
            List<Rule> rules = new ArrayList<>();
            for (NetworkHandler.RuleLine rl : sp.rules()) {
                List<Cond> conds = new ArrayList<>();
                for (NetworkHandler.CondLine cl : rl.conditions()) {
                    conds.add(new Cond(cl.label(), cl.current(), cl.threshold(), cl.satisfied()));
                }
                rules.add(new Rule(rl.mode(), rl.description(), rl.satisfied(), conds));
            }
            DATA.put(id, new StageData(rules, List.copyOf(sp.unlockSample()), sp.unlockTotal()));
        }
    }

    public static StageData get(StageId stageId) {
        return DATA.getOrDefault(stageId, StageData.EMPTY);
    }

    public static boolean hasData() {
        return !DATA.isEmpty();
    }

    public static void clear() {
        DATA.clear();
    }

    /** Ask the server for a fresh snapshot (and open the screen on arrival). */
    public static void requestFromServer() {
        if (Minecraft.getInstance().player == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(NetworkHandler.RequestStageGuiPayload.INSTANCE);
    }
}
