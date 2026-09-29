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

    public record Why(String category, String action, ResourceLocation target, String winner,
                      String effect, boolean blocked, String explanation) {}

    public record Challenge(ResourceLocation id, String status, int step, int attempts,
                            List<String> budgets, String explanation) {}

    public record ModifierField(String label, String value, String registry, String selector) {}

    public record ModifierPreview(String kind, ResourceLocation id, List<ModifierField> fields) {
        public ModifierPreview {
            fields = List.copyOf(fields);
        }
    }

    public record History(long timestamp, String direction, boolean committed, String explanation) {}

    public record StageData(List<Rule> rules, List<ResourceLocation> unlockSample, int unlockTotal,
                            boolean purchasable, int costXp, String costSummary, boolean canPurchase,
                            boolean purchaseBypassesTriggers,
                            NetworkHandler.AcquisitionInfo acquisition, long receivedAtNanos,
                            List<Why> why, List<Challenge> challenges, List<ModifierPreview> modifiers,
                            List<History> history) {
        public static final StageData EMPTY = new StageData(List.of(), List.of(), 0, false, 0, "", false,
            false, NetworkHandler.AcquisitionInfo.UNKNOWN, 0L, List.of(), List.of(), List.of(), List.of());

        public StageData {
            rules = List.copyOf(rules);
            unlockSample = List.copyOf(unlockSample);
            why = List.copyOf(why);
            challenges = List.copyOf(challenges);
            modifiers = List.copyOf(modifiers);
            history = List.copyOf(history);
        }

        public boolean hasTriggers() { return !rules.isEmpty(); }

        public long remainingCooldownMillis() {
            if (acquisition == null) return 0L;
            long elapsed = Math.max(0L, (System.nanoTime() - receivedAtNanos) / 1_000_000L);
            return Math.max(0L, acquisition.cooldownRemainingMillis() - elapsed);
        }

        /** Overall completion toward unlocking via triggers (best rule), or -1 if no triggers. */
        public float percent() {
            if (rules.isEmpty()) return -1f;
            float best = 0f;
            for (Rule r : rules) best = Math.max(best, r.fraction());
            return Math.min(1f, best);
        }
    }

    private static final Map<StageId, StageData> DATA = new HashMap<>();
    private static long acceptedDefinitionRevision = Long.MIN_VALUE;
    private static long acceptedProgressSequence;

    private ClientTriggerProgress() {}

    public static void onDefinitionsAccepted(long definitionRevision) {
        if (acceptedDefinitionRevision == definitionRevision) return;
        DATA.clear();
        acceptedDefinitionRevision = definitionRevision;
        acceptedProgressSequence = 0L;
    }

    public static void acceptResponse(long definitionRevision, long sequence,
                                      List<NetworkHandler.StageProgress> stages) {
        if (!acceptSnapshot(definitionRevision, sequence, stages)) return;
        if (supportsExplicitOpening()) com.enviouse.progressivestages.client.gui.StageTreeScreen.refreshIfOpen();
        else com.enviouse.progressivestages.client.gui.StageTreeScreen.open();
    }

    static boolean acceptSnapshot(long definitionRevision, long sequence,
                                  List<NetworkHandler.StageProgress> stages) {
        if (definitionRevision != ClientStageCache.definitionSnapshotRevision()
                || definitionRevision != acceptedDefinitionRevision
                || sequence <= acceptedProgressSequence || stages == null) return false;
        accept(stages);
        acceptedProgressSequence = sequence;
        return true;
    }

    private static boolean supportsExplicitOpening() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(NetworkHandler.OpenStageGuiPayload.TYPE);
    }

    private static void accept(List<NetworkHandler.StageProgress> stages) {
        DATA.clear();
        if (stages == null) return;
        long receivedAtNanos = System.nanoTime();
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
            NetworkHandler.CostInfo cost = sp.cost();
            DATA.put(id, new StageData(rules, List.copyOf(sp.unlockSample()), sp.unlockTotal(),
                cost.purchasable(), cost.costXp(), cost.summary(), cost.canPurchase(),
                cost.bypassesTriggers(),
                sp.acquisition() == null ? NetworkHandler.AcquisitionInfo.UNKNOWN : sp.acquisition(),
                receivedAtNanos,
                sp.why().stream().map(line -> new Why(line.category(), line.action(), line.target(),
                    line.winner(), line.effect(), line.blocked(), line.explanation())).toList(),
                sp.challenges().stream().map(line -> new Challenge(line.id(), line.status(), line.step(),
                    line.attempts(), line.budgets(), line.explanation())).toList(),
                sp.modifiers().stream().map(line -> new ModifierPreview(line.kind(), line.id(),
                    line.fields().stream().map(field -> new ModifierField(field.label(), field.value(),
                        field.registry(), field.selector())).toList())).toList(),
                sp.history().stream().map(line -> new History(line.timestamp(), line.direction(),
                    line.committed(), line.explanation())).toList()));
        }
    }

    /** v2.4: ask the server to buy a purchasable stage. */
    public static void requestPurchase(StageId stageId) {
        if (Minecraft.getInstance().player == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new NetworkHandler.RequestPurchasePayload(stageId.getResourceLocation()));
    }

    public static StageData get(StageId stageId) {
        return DATA.getOrDefault(stageId, StageData.EMPTY);
    }

    public static boolean hasData() {
        return !DATA.isEmpty();
    }

    public static void clear() {
        DATA.clear();
        acceptedDefinitionRevision = Long.MIN_VALUE;
        acceptedProgressSequence = 0L;
    }

    public static void refreshFromServer() {
        // Legacy responses also open the screen and cannot safely refresh after it closes.
        if (Minecraft.getInstance().player == null || !supportsExplicitOpening()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(NetworkHandler.RequestStageGuiPayload.INSTANCE);
    }

    /** request current gui data and open the screen. */
    public static void requestFromServer() {
        if (Minecraft.getInstance().player == null) return;
        if (supportsExplicitOpening()) com.enviouse.progressivestages.client.gui.StageTreeScreen.open();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(NetworkHandler.RequestStageGuiPayload.INSTANCE);
    }
}
