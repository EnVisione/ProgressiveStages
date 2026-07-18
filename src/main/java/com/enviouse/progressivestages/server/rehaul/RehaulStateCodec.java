package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeSessionView;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeSnapshot;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeStatus;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionStateStore;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.counter.CounterKey;
import com.enviouse.progressivestages.common.rehaul.counter.CounterSnapshot;
import com.enviouse.progressivestages.common.rehaul.counter.CounterWindow;
import com.enviouse.progressivestages.common.rehaul.counter.ResetPolicy;
import com.enviouse.progressivestages.common.rehaul.counter.WindowKind;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleDirection;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleTransactionEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TemporaryRuleEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TransitionHistoryEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RehaulStateCodec {

    private RehaulStateCodec() {}

    static CompoundTag encode(RehaulRuntime runtime) {
        CompoundTag root = new CompoundTag();
        root.put("conditions", conditions(runtime.conditionStates().snapshot()));
        root.put("temporary", temporary(runtime.temporary().snapshot()));
        root.put("lifecycle", lifecycle(runtime.lifecycle().snapshot()));
        root.putLong("transaction_id", runtime.lifecycle().transactionId());
        root.put("counters", counters(runtime.counters().snapshot()));
        root.put("challenges", challenges(runtime.challenges().snapshot()));
        root.put("variables", variables(runtime.variables().persistentSnapshot()));
        root.put("stage_states", stringMap(runtime.stageStates().snapshot()));
        root.put("history", history(runtime.transitionHistory().entries()));
        return root;
    }

    static void decode(CompoundTag root, RehaulRuntime runtime) {
        if (root == null || root.isEmpty()) return;
        runtime.conditionStates().restore(readConditions(root.getList("conditions", Tag.TAG_COMPOUND)));
        runtime.temporary().restore(readTemporary(root.getList("temporary", Tag.TAG_COMPOUND)));
        runtime.lifecycle().restore(readLifecycle(root.getList("lifecycle", Tag.TAG_COMPOUND)),
            root.getLong("transaction_id"));
        runtime.counters().restore(readCounters(root.getCompound("counters")));
        runtime.challenges().restore(readChallenges(root.getList("challenges", Tag.TAG_COMPOUND)));
        runtime.variables().restore(readVariables(root.getList("variables", Tag.TAG_COMPOUND)));
        runtime.stageStates().restore(readStringMap(root.getList("stage_states", Tag.TAG_COMPOUND)));
        runtime.transitionHistory().restore(readHistory(root.getList("history", Tag.TAG_COMPOUND)));
    }

    private static ListTag conditions(Map<String, ConditionStateStore.SequenceSnapshot> values) {
        ListTag list = new ListTag();
        values.forEach((key, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            tag.putInt("index", value.index());
            tag.putLong("started_at", value.startedAt());
            list.add(tag);
        });
        return list;
    }

    private static Map<String, ConditionStateStore.SequenceSnapshot> readConditions(ListTag list) {
        Map<String, ConditionStateStore.SequenceSnapshot> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (!tag.getString("key").isBlank()) result.put(tag.getString("key"),
                new ConditionStateStore.SequenceSnapshot(tag.getInt("index"), tag.getLong("started_at")));
        }
        return result;
    }

    private static ListTag temporary(List<TemporaryRuleEngine.TemporaryStateSnapshot> values) {
        ListTag list = new ListTag();
        for (TemporaryRuleEngine.TemporaryStateSnapshot value : values) {
            CompoundTag tag = new CompoundTag();
            tag.putString("subject", value.subject());
            tag.putString("rule", value.rule().toString());
            tag.putBoolean("active", value.active());
            tag.putBoolean("latched", value.latched());
            tag.putBoolean("last_matched", value.lastMatched());
            tag.putLong("active_since", value.activeSince());
            tag.putLong("inactive_since", value.inactiveSince());
            tag.putLong("match_since", value.matchSince());
            tag.putLong("unmatched_since", value.unmatchedSince());
            tag.putLong("expires_at", value.expiresAt());
            tag.putLong("cooldown_until", value.cooldownUntil());
            list.add(tag);
        }
        return list;
    }

    private static List<TemporaryRuleEngine.TemporaryStateSnapshot> readTemporary(ListTag list) {
        List<TemporaryRuleEngine.TemporaryStateSnapshot> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            ResourceLocation rule = ResourceLocation.tryParse(tag.getString("rule"));
            if (rule == null || tag.getString("subject").isBlank()) continue;
            result.add(new TemporaryRuleEngine.TemporaryStateSnapshot(tag.getString("subject"), rule,
                tag.getBoolean("active"), tag.getBoolean("latched"), tag.getBoolean("last_matched"),
                tag.getLong("active_since"), tag.getLong("inactive_since"), tag.getLong("match_since"),
                tag.getLong("unmatched_since"), tag.getLong("expires_at"), tag.getLong("cooldown_until")));
        }
        return result;
    }

    private static ListTag lifecycle(List<LifecycleTransactionEngine.LifecycleStateSnapshot> values) {
        ListTag list = new ListTag();
        for (LifecycleTransactionEngine.LifecycleStateSnapshot value : values) {
            CompoundTag tag = new CompoundTag();
            tag.putString("subject", value.subject());
            tag.putString("rule", value.rule().toString());
            tag.putBoolean("last_matched", value.lastMatched());
            tag.putBoolean("committed_once", value.committedOnce());
            tag.putLong("match_since", value.matchSince());
            tag.putLong("last_committed_at", value.lastCommittedAt());
            tag.putLong("cooldown_until", value.cooldownUntil());
            tag.putBoolean("armed", value.armed());
            list.add(tag);
        }
        return list;
    }

    private static List<LifecycleTransactionEngine.LifecycleStateSnapshot> readLifecycle(ListTag list) {
        List<LifecycleTransactionEngine.LifecycleStateSnapshot> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            ResourceLocation rule = ResourceLocation.tryParse(tag.getString("rule"));
            if (rule == null || tag.getString("subject").isBlank()) continue;
            result.add(new LifecycleTransactionEngine.LifecycleStateSnapshot(tag.getString("subject"), rule,
                tag.getBoolean("last_matched"), tag.getBoolean("committed_once"), tag.getLong("match_since"),
                tag.getLong("last_committed_at"), tag.getLong("cooldown_until"), tag.getBoolean("armed")));
        }
        return result;
    }

    private static CompoundTag counters(CounterSnapshot snapshot) {
        CompoundTag result = new CompoundTag();
        ListTag totals = new ListTag();
        snapshot.totals().forEach((key, amount) -> {
            CompoundTag entry = counterKey(key);
            entry.putDouble("amount", amount);
            totals.add(entry);
        });
        result.put("totals", totals);
        ListTag rolling = new ListTag();
        snapshot.rolling().forEach((key, amounts) -> {
            CompoundTag entry = counterKey(key);
            ListTag samples = new ListTag();
            for (CounterSnapshot.TimedAmount amount : amounts) {
                CompoundTag sample = new CompoundTag();
                sample.putLong("timestamp", amount.timestamp());
                sample.putDouble("amount", amount.amount());
                samples.add(sample);
            }
            entry.put("samples", samples);
            rolling.add(entry);
        });
        result.put("rolling", rolling);
        return result;
    }

    private static CounterSnapshot readCounters(CompoundTag root) {
        Map<CounterKey, Double> totals = new LinkedHashMap<>();
        ListTag totalList = root.getList("totals", Tag.TAG_COMPOUND);
        for (int index = 0; index < totalList.size(); index++) {
            CompoundTag tag = totalList.getCompound(index);
            CounterKey key = readCounterKey(tag);
            if (key != null && Double.isFinite(tag.getDouble("amount"))) totals.put(key, tag.getDouble("amount"));
        }
        Map<CounterKey, List<CounterSnapshot.TimedAmount>> rolling = new LinkedHashMap<>();
        ListTag rollingList = root.getList("rolling", Tag.TAG_COMPOUND);
        for (int index = 0; index < rollingList.size(); index++) {
            CompoundTag tag = rollingList.getCompound(index);
            CounterKey key = readCounterKey(tag);
            if (key == null) continue;
            List<CounterSnapshot.TimedAmount> samples = new ArrayList<>();
            ListTag sampleList = tag.getList("samples", Tag.TAG_COMPOUND);
            for (int sampleIndex = 0; sampleIndex < sampleList.size(); sampleIndex++) {
                CompoundTag sample = sampleList.getCompound(sampleIndex);
                double amount = sample.getDouble("amount");
                if (Double.isFinite(amount)) samples.add(new CounterSnapshot.TimedAmount(
                    sample.getLong("timestamp"), amount));
            }
            rolling.put(key, samples);
        }
        return new CounterSnapshot(totals, rolling);
    }

    private static CompoundTag counterKey(CounterKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString("subject", key.subject());
        tag.putString("scope", key.scope().name());
        tag.putString("metric", key.metric().toString());
        tag.putString("window", key.window().kind().name());
        tag.putLong("duration", key.window().durationMillis());
        tag.putString("session", key.window().sessionId());
        tag.putString("reset", key.window().resetPolicy().name());
        tag.putBoolean("pause_offline", key.window().pauseOffline());
        return tag;
    }

    private static CounterKey readCounterKey(CompoundTag tag) {
        try {
            ResourceLocation metric = ResourceLocation.tryParse(tag.getString("metric"));
            if (metric == null || tag.getString("subject").isBlank()) return null;
            CounterWindow window = new CounterWindow(WindowKind.valueOf(tag.getString("window")),
                tag.getLong("duration"), tag.getString("session"),
                ResetPolicy.valueOf(tag.getString("reset")), tag.getBoolean("pause_offline"));
            return new CounterKey(tag.getString("subject"), SubjectScope.valueOf(tag.getString("scope")), metric, window);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static ListTag challenges(ChallengeSnapshot snapshot) {
        ListTag list = new ListTag();
        for (ChallengeSessionView value : snapshot.sessions()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("subject", value.subject());
            tag.putString("challenge", value.challenge().toString());
            tag.putString("status", value.status().name());
            tag.putLong("started_at", value.startedAt());
            tag.putLong("ended_at", value.endedAt());
            tag.putInt("step", value.currentStep());
            tag.putInt("attempts", value.attempts());
            ListTag budgets = new ListTag();
            value.budgetValues().forEach((id, amount) -> {
                CompoundTag budget = new CompoundTag();
                budget.putString("id", id.toString());
                budget.putDouble("amount", amount);
                budgets.add(budget);
            });
            tag.put("budgets", budgets);
            list.add(tag);
        }
        return list;
    }

    private static ChallengeSnapshot readChallenges(ListTag list) {
        List<ChallengeSessionView> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            try {
                CompoundTag tag = list.getCompound(index);
                ResourceLocation challenge = ResourceLocation.tryParse(tag.getString("challenge"));
                if (challenge == null || tag.getString("subject").isBlank()) continue;
                Map<ResourceLocation, Double> budgets = new LinkedHashMap<>();
                ListTag budgetList = tag.getList("budgets", Tag.TAG_COMPOUND);
                for (int budgetIndex = 0; budgetIndex < budgetList.size(); budgetIndex++) {
                    CompoundTag budget = budgetList.getCompound(budgetIndex);
                    ResourceLocation id = ResourceLocation.tryParse(budget.getString("id"));
                    double amount = budget.getDouble("amount");
                    if (id != null && Double.isFinite(amount)) budgets.put(id, amount);
                }
                result.add(new ChallengeSessionView(tag.getString("subject"), challenge,
                    ChallengeStatus.valueOf(tag.getString("status")), tag.getLong("started_at"),
                    tag.getLong("ended_at"), tag.getInt("step"), tag.getInt("attempts"), budgets, ""));
            } catch (RuntimeException ignored) {}
        }
        return new ChallengeSnapshot(result);
    }

    private static ListTag variables(Map<String, Object> values) {
        ListTag list = new ListTag();
        values.forEach((key, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            if (value instanceof Boolean bool) {
                tag.putString("type", "boolean");
                tag.putBoolean("boolean", bool);
            } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                tag.putString("type", "integer");
                tag.putLong("integer", ((Number) value).longValue());
            } else if (value instanceof Number number) {
                tag.putString("type", "decimal");
                tag.putDouble("decimal", number.doubleValue());
            } else {
                tag.putString("type", "string");
                tag.putString("string", String.valueOf(value));
            }
            list.add(tag);
        });
        return list;
    }

    private static Map<String, Object> readVariables(ListTag list) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (tag.getString("key").isBlank()) continue;
            Object value = switch (tag.getString("type")) {
                case "boolean" -> tag.getBoolean("boolean");
                case "integer" -> tag.getLong("integer");
                case "decimal" -> tag.getDouble("decimal");
                default -> tag.getString("string");
            };
            result.put(tag.getString("key"), value);
        }
        return result;
    }

    private static ListTag stringMap(Map<String, String> values) {
        ListTag list = new ListTag();
        values.forEach((key, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            tag.putString("value", value);
            list.add(tag);
        });
        return list;
    }

    private static Map<String, String> readStringMap(ListTag list) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            if (!tag.getString("key").isBlank()) result.put(tag.getString("key"), tag.getString("value"));
        }
        return result;
    }

    private static ListTag history(List<TransitionHistoryEntry> values) {
        ListTag list = new ListTag();
        for (TransitionHistoryEntry value : values) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("transaction", value.transactionId());
            tag.putLong("timestamp", value.timestamp());
            tag.putString("subject", value.subject());
            tag.putString("rule", value.rule().toString());
            tag.putString("stage", value.stage().toString());
            tag.putString("direction", value.direction().name());
            tag.putBoolean("committed", value.committed());
            tag.putString("explanation", value.explanation());
            list.add(tag);
        }
        return list;
    }

    private static List<TransitionHistoryEntry> readHistory(ListTag list) {
        List<TransitionHistoryEntry> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            try {
                CompoundTag tag = list.getCompound(index);
                ResourceLocation rule = ResourceLocation.tryParse(tag.getString("rule"));
                StageId stage = StageId.tryParse(tag.getString("stage"));
                if (rule == null || stage == null) continue;
                result.add(new TransitionHistoryEntry(tag.getLong("transaction"), tag.getLong("timestamp"),
                    tag.getString("subject"), rule, stage, LifecycleDirection.valueOf(tag.getString("direction")),
                    tag.getBoolean("committed"), tag.getString("explanation")));
            } catch (RuntimeException ignored) {}
        }
        return result;
    }
}
