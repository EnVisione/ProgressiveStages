package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.RevokeRule;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.team.TeamProvider;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.4: enforces {@code [revoke]} rules and temporary-stage expiry (regression). Records the
 * grant time of temporary stages, revokes on death / when maintained XP drops below the threshold /
 * when a temporary stage's real-time duration elapses, and honors per-stage {@code revoke_cascade}.
 */
public final class StageRegressionHandler {

    private static final java.util.Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();

    private StageRegressionHandler() {}

    public static void resetRuntimeState() { lastCheck.clear(); }

    /** Record/clear the grant time of temporary stages as they're granted/revoked. */
    @SubscribeEvent
    public static void onStageChange(StageChangeEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || player.server == null) return;
        StageDefinition def = StageOrder.getInstance().getStageDefinition(event.getStageId()).orElse(null);
        if (def == null) return;
        StageRegressionData data = StageRegressionData.get(player.server);
        // Server-scoped stages live under SERVER_TEAM, so their grant time must be keyed there too —
        // otherwise each team that sees the stage (via the SERVER_TEAM union) would record its own
        // clock and the stage would expire at a different wall-clock time per team.
        UUID key = def.isServerScope() ? StageManager.SERVER_TEAM : event.getTeamId();
        if (event.getChangeType() == com.enviouse.progressivestages.common.api.StageChangeType.GRANTED) {
            // v3.0: record the grant time for EVERY stage (not only temporary ones) so the
            // stage_held_for trigger and temporary-expiry both have a timestamp to read.
            data.markGranted(key, event.getStageId(), System.currentTimeMillis());
        } else if (event.getChangeType() == com.enviouse.progressivestages.common.api.StageChangeType.REVOKED) {
            data.clear(key, event.getStageId());
        }
    }

    /** Revoke {@code on_death} stages when a player dies. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<StageId> owned = StageManager.getInstance().getStages(player);
        if (owned.isEmpty()) return;
        for (StageId id : new ArrayList<>(owned)) {
            StageDefinition def = StageOrder.getInstance().getStageDefinition(id).orElse(null);
            if (def != null && def.getRevoke().onDeath()) {
                StageManager.getInstance().revokeStageWithCause(player, id, StageCause.REGRESSION);
            }
        }
    }

    /** Poll: expire temporary stages and revoke XP-maintained stages whose XP dropped below the threshold. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long gameTime = player.level().getGameTime();
        int interval = Math.max(1, StageConfig.getTriggerPollInterval());
        Long last = lastCheck.get(player.getUUID());
        if (last != null && gameTime - last < interval) return;
        lastCheck.put(player.getUUID(), gameTime);
        checkPlayer(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) checkPlayer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) lastCheck.remove(player.getUUID());
    }

    private static void checkPlayer(ServerPlayer player) {
        if (player.server == null) return;
        Set<StageId> owned = StageManager.getInstance().getStages(player);
        if (owned.isEmpty()) return;
        UUID teamId = TeamProvider.getInstance().getTeamId(player);
        StageRegressionData data = StageRegressionData.get(player.server);
        long now = System.currentTimeMillis();

        for (StageId id : new ArrayList<>(owned)) {
            StageDefinition def = StageOrder.getInstance().getStageDefinition(id).orElse(null);
            if (def == null) continue;

            // Temporary stage expiry (real wall-clock, runs while offline). Server-scoped stages
            // are keyed under SERVER_TEAM so every team shares one synchronized expiry clock.
            if (def.isTemporary()) {
                UUID grantKey = def.isServerScope() ? StageManager.SERVER_TEAM : teamId;
                long grantTime = data.getGrantTime(grantKey, id);
                if (grantTime <= 0) {
                    // No record (e.g. granted before this feature, or by command) — start the clock now.
                    data.markGranted(grantKey, id, now);
                } else if (now - grantTime >= def.getDurationMillis()) {
                    StageManager.getInstance().revokeStageWithCause(player, id, StageCause.REGRESSION);
                    continue;
                }
            }

            // XP-maintained: hold the stage only while total XP stays >= threshold.
            RevokeRule r = def.getRevoke();
            if (r.maintainsXp() && player.totalExperience < r.xpBelow()) {
                StageManager.getInstance().revokeStageWithCause(player, id, StageCause.REGRESSION);
            }
        }
    }
}
