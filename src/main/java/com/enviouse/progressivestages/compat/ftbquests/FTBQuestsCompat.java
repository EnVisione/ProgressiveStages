package com.enviouse.progressivestages.compat.ftbquests;

import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * FTB Quests integration module.
 *
 * <p>Integration points used (from FTB source analysis):
 * <ul>
 *   <li>{@code StageHelper.INSTANCE.setProvider()} - registers our stage backend</li>
 *   <li>{@code StageTask.checkStages(ServerPlayer)} - documented hook for triggering re-evaluation</li>
 * </ul>
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Debounces stage check requests to avoid spam on bulk operations</li>
 *   <li>Coalesces multiple events into single recheck per player per tick</li>
 *   <li>Lifecycle-safe: clears state on server stop, validates server state</li>
 *   <li>Re-entrancy safe: skips recursive rechecks, re-queues for next tick</li>
 *   <li>Work budget: processes max N players per tick to prevent lag spikes</li>
 *   <li>Handles both individual StageChangeEvent and bulk StagesBulkChangedEvent</li>
 * </ul>
 *
 * <p>Note: FTB Quests' StageTask has a 20-tick fallback poll. This integration
 * provides instant updates; ProgressiveStages does not poll.
 */
public class FTBQuestsCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;
    private static boolean ftbQuestsAvailable = false;
    private static boolean serverRunning = false;

    // Debounce: collect players needing recheck, process once per tick
    // Only accessed from server thread, no synchronization needed
    private static final Set<UUID> pendingRechecks = new HashSet<>();

    /**
     * Get the maximum players to process per tick (from config).
     */
    private static int getMaxRechecksPerTick() {
        return StageConfig.getFtbRecheckBudget();
    }

    /**
     * Initialize FTB Quests compatibility.
     * Should be called during ServerStartingEvent.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Check for FTB Quests
        ftbQuestsAvailable = ModList.get().isLoaded("ftbquests");
        if (!ftbQuestsAvailable) {
            LOGGER.debug("[ProgressiveStages] FTB Quests not found, skipping integration");
            return;
        }

        // Check for FTB Library (required for stage provider)
        if (!ModList.get().isLoaded("ftblibrary")) {
            LOGGER.warn("[ProgressiveStages] FTB Library not found, FTB Quests integration disabled");
            ftbQuestsAvailable = false;
            return;
        }

        // Check config - allow pack devs to disable FTB integration
        if (!StageConfig.isFtbQuestsIntegrationEnabled()) {
            LOGGER.info("[ProgressiveStages] FTB Quests integration disabled by config");
            return;
        }

        // Register lifecycle event handlers for ongoing events (stage changes, server tick, stopping)
        NeoForge.EVENT_BUS.register(FTBQuestsCompat.class);

        // Register our stage provider NOW (since we're in ServerStartingEvent)
        try {
            LOGGER.info("[ProgressiveStages] Attempting to register FTB Library stage provider...");
            FtbQuestsHooks.registerStageProvider();
            serverRunning = true;

            if (FtbQuestsHooks.isProviderRegistered()) {
                LOGGER.info("[ProgressiveStages] FTB Quests integration active - provider registered successfully");
            } else {
                LOGGER.warn("[ProgressiveStages] FTB Quests integration: provider registration returned but not confirmed active");
            }
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Failed to activate FTB Quests integration: {}", e.getMessage());
            LOGGER.error("[ProgressiveStages] Full exception:", e);
        }

        LOGGER.info("[ProgressiveStages] FTB Quests integration initialized");
    }

    /**
     * Called when server is starting.
     * This is a backup in case init() wasn't called at the right time.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Provider registration is now done in init() - this is just a fallback
        if (!serverRunning && ftbQuestsAvailable && StageConfig.isFtbQuestsIntegrationEnabled()) {
            if (!FtbQuestsHooks.isProviderRegistered()) {
                LOGGER.info("[ProgressiveStages] Late provider registration in ServerStartingEvent...");
                try {
                    FtbQuestsHooks.registerStageProvider();
                    serverRunning = true;
                    if (FtbQuestsHooks.isProviderRegistered()) {
                        LOGGER.info("[ProgressiveStages] Late registration successful");
                    }
                } catch (Exception e) {
                    LOGGER.error("[ProgressiveStages] Late registration failed: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Called when server is stopping.
     * Clears all pending state to prevent stale data on restart.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        serverRunning = false;
        lateRegistrationAttempted = false;
        pendingRechecks.clear();
        FtbQuestsHooks.clearRecheckGuards();
        // Clear previous provider to prevent restoring stale references on next startup
        FtbQuestsHooks.clearPreviousProvider();
        LOGGER.debug("[ProgressiveStages] FTB Quests integration deactivated, cleared all state");
    }

    // Track if we've attempted late registration
    private static boolean lateRegistrationAttempted = false;

    /**
     * Called when a single stage changes.
     * Schedules a debounced recheck for the affected player.
     */
    @SubscribeEvent
    public static void onStageChanged(StageChangeEvent event) {
        if (!ftbQuestsAvailable || !serverRunning) return;
        if (!StageConfig.isFtbQuestsIntegrationEnabled()) return;

        // Late registration fallback: if provider wasn't registered at server start
        // (e.g., FTB Quests loaded after us), try again now
        attemptLateRegistrationIfNeeded();

        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        // Add to pending rechecks (will be processed on next server tick)
        pendingRechecks.add(player.getUUID());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ProgressiveStages] Scheduled FTB Quests recheck for {} (stage: {}, type: {})",
                player.getName().getString(), event.getStageId(), event.getChangeType());
        }
    }

    /**
     * Attempt late registration of FTB provider if not already registered.
     * This handles cases where FTB Quests loads after ProgressiveStages.
     */
    private static void attemptLateRegistrationIfNeeded() {
        if (lateRegistrationAttempted || FtbQuestsHooks.isProviderRegistered()) {
            return;
        }

        lateRegistrationAttempted = true;
        LOGGER.info("[ProgressiveStages] Attempting late FTB provider registration (FTB Quests may have loaded after us)...");

        try {
            FtbQuestsHooks.registerStageProvider();
            if (FtbQuestsHooks.isProviderRegistered()) {
                LOGGER.info("[ProgressiveStages] Late FTB provider registration successful!");
            } else {
                LOGGER.warn("[ProgressiveStages] Late FTB provider registration did not succeed");
            }
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Late FTB provider registration failed: {}", e.getMessage());
        }
    }

    /**
     * Called when multiple stages change at once (login, team join, reload).
     * Schedules a single recheck instead of N individual rechecks.
     */
    @SubscribeEvent
    public static void onStagesBulkChanged(StagesBulkChangedEvent event) {
        if (!ftbQuestsAvailable || !serverRunning) return;
        if (!StageConfig.isFtbQuestsIntegrationEnabled()) return;

        // Late registration fallback
        attemptLateRegistrationIfNeeded();

        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        // Add to pending rechecks (single entry regardless of how many stages changed)
        pendingRechecks.add(player.getUUID());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[ProgressiveStages] Scheduled FTB Quests recheck for {} (bulk change: {}, {} stages)",
                player.getName().getString(), event.getReason(), event.getCurrentStages().size());
        }
    }

    /**
     * Process pending rechecks once per server tick.
     *
     * <p>Features:
     * <ul>
     *   <li>Coalesces multiple stage changes into single recheck per player</li>
     *   <li>Work budget: processes max N players per tick (from config) to prevent lag</li>
     *   <li>Re-queues players if recheck was skipped due to re-entrancy</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ftbQuestsAvailable || !serverRunning) return;
        if (!StageConfig.isFtbQuestsIntegrationEnabled()) return;
        if (pendingRechecks.isEmpty()) return;

        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) {
            pendingRechecks.clear();
            return;
        }

        // Process up to max players this tick (from config)
        int maxRechecks = getMaxRechecksPerTick();
        int processed = 0;
        Set<UUID> toRequeue = new HashSet<>();

        Iterator<UUID> iterator = pendingRechecks.iterator();
        while (iterator.hasNext() && processed < maxRechecks) {
            UUID playerId = iterator.next();
            iterator.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.hasDisconnected()) {
                // Try to perform recheck
                boolean success = FtbQuestsHooks.requestStageRecheck(player);

                if (!success && FtbQuestsHooks.isRecheckInProgress(playerId)) {
                    // Recheck was skipped due to re-entrancy, re-queue for next tick
                    toRequeue.add(playerId);
                }

                processed++;
            }
        }

        // Re-add any that need to be re-queued
        pendingRechecks.addAll(toRequeue);

        // Log if we hit the budget and have more to process
        if (!pendingRechecks.isEmpty() && processed >= maxRechecks) {
            LOGGER.debug("[ProgressiveStages] Hit recheck budget ({}/tick), {} remaining for next tick",
                maxRechecks, pendingRechecks.size());
        }
    }

    /**
     * Check if FTB Quests integration is active.
     */
    public static boolean isEnabled() {
        return initialized && ftbQuestsAvailable && serverRunning && StageConfig.isFtbQuestsIntegrationEnabled();
    }

    /**
     * Force an immediate recheck for a player (bypass debounce).
     * Use sparingly - prefer letting events trigger debounced rechecks.
     */
    public static void forceRecheck(ServerPlayer player) {
        if (ftbQuestsAvailable && serverRunning && player != null && !player.hasDisconnected()) {
            if (StageConfig.isFtbQuestsIntegrationEnabled()) {
                FtbQuestsHooks.requestStageRecheck(player);
            }
        }
    }

    /**
     * Get the number of pending rechecks (for debug/metrics).
     */
    public static int getPendingCount() {
        return pendingRechecks.size();
    }
}

