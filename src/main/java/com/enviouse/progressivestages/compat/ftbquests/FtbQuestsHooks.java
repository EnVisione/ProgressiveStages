package com.enviouse.progressivestages.compat.ftbquests;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Isolated reflection adapter for FTB Quests/Library integration.
 *
 * <p>This class encapsulates ALL reflection calls to FTB mods, making it easy to:
 * <ul>
 *   <li>Update when FTB APIs change</li>
 *   <li>Fail gracefully if methods don't exist</li>
 *   <li>Log warnings once instead of spamming</li>
 * </ul>
 *
 * <p>Integration points from FTB source:
 * <ul>
 *   <li>{@code dev.ftb.mods.ftblibrary.integration.stages.StageHelper.INSTANCE.setProvider(Provider)}</li>
 *   <li>{@code dev.ftb.mods.ftbquests.quest.task.StageTask.checkStages(ServerPlayer)}</li>
 * </ul>
 *
 * <p>Safety features:
 * <ul>
 *   <li>Re-entrancy guard: prevents recursive stage rechecks</li>
 *   <li>Idempotent provider registration</li>
 *   <li>Single-warning-per-error pattern</li>
 * </ul>
 */
public final class FtbQuestsHooks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROVIDER_ID = "ProgressiveStagesStageProvider";

    // Cached reflection handles
    private static Method checkStagesMethod = null;
    private static boolean checkStagesWarned = false;
    private static boolean providerRegistered = false;
    private static Object ourProviderInstance = null;

    // Store previous provider for potential restore (dev environments)
    private static Object previousProvider = null;
    private static Object stageHelperInstanceCache = null;
    private static Method setProviderMethodCache = null;
    private static Class<?> providerInterfaceCache = null;

    // Re-entrancy guard: tracks players currently being rechecked
    // Stage rechecks can trigger quest rewards → more stages → more rechecks
    private static final Set<UUID> recheckInProgress = new HashSet<>();

    private FtbQuestsHooks() {}

    /**
     * Register ProgressiveStages as the FTB Library stage provider.
     *
     * <p>This method is idempotent - calling it multiple times is safe.
     * It will only register if the current provider is not already ours.
     *
     * <p>FTB Library's StageHelper.Provider interface:
     * <ul>
     *   <li>{@code boolean has(ServerPlayer player, String stage)}</li>
     *   <li>{@code void add(ServerPlayer player, String stage)}</li>
     *   <li>{@code void remove(ServerPlayer player, String stage)}</li>
     * </ul>
     */
    public static void registerStageProvider() {
        if (providerRegistered) {
            LOGGER.debug("[ProgressiveStages] Stage provider already registered, skipping");
            return;
        }

        LOGGER.info("[ProgressiveStages] Starting FTB Library stage provider registration...");

        try {
            // Get StageHelper class and INSTANCE field
            LOGGER.debug("[ProgressiveStages] Looking for StageHelper class...");
            Class<?> stageHelperClass = Class.forName("dev.ftb.mods.ftblibrary.integration.stages.StageHelper");
            LOGGER.debug("[ProgressiveStages] Found StageHelper class: {}", stageHelperClass.getName());

            stageHelperInstanceCache = stageHelperClass.getField("INSTANCE").get(null);
            LOGGER.debug("[ProgressiveStages] Got StageHelper.INSTANCE: {}", stageHelperInstanceCache);

            // Check current provider and store it for potential restore
            Method getProvider = stageHelperInstanceCache.getClass().getMethod("getProvider");
            Object currentProvider = getProvider.invoke(stageHelperInstanceCache);
            LOGGER.debug("[ProgressiveStages] Current provider: {}", currentProvider);

            // Check if current provider is already ours
            if (currentProvider != null) {
                String currentProviderName = currentProvider.toString();
                if (PROVIDER_ID.equals(currentProviderName)) {
                    LOGGER.debug("[ProgressiveStages] Stage provider already set to ProgressiveStages");
                    providerRegistered = true;
                    return;
                }
                // Store previous provider for potential restore
                previousProvider = currentProvider;
                LOGGER.info("[ProgressiveStages] Replacing existing stage provider: {}", currentProviderName);
            }

            // Get Provider interface and cache it
            LOGGER.debug("[ProgressiveStages] Looking for StageProvider interface...");
            providerInterfaceCache = Class.forName("dev.ftb.mods.ftblibrary.integration.stages.StageProvider");
            LOGGER.debug("[ProgressiveStages] Found StageProvider interface: {}", providerInterfaceCache.getName());

            // Create dynamic proxy implementing StageProvider
            ourProviderInstance = java.lang.reflect.Proxy.newProxyInstance(
                FtbQuestsHooks.class.getClassLoader(),
                new Class<?>[] { providerInterfaceCache },
                (proxy, method, args) -> handleProviderMethod(method.getName(), args)
            );
            LOGGER.debug("[ProgressiveStages] Created provider proxy: {}", ourProviderInstance);

            // Cache setProviderImpl method for potential restore (method is setProviderImpl, not setProvider)
            setProviderMethodCache = stageHelperInstanceCache.getClass().getMethod("setProviderImpl", providerInterfaceCache);
            setProviderMethodCache.invoke(stageHelperInstanceCache, ourProviderInstance);

            providerRegistered = true;
            LOGGER.info("[ProgressiveStages] Successfully registered as FTB Library stage provider");

        } catch (ClassNotFoundException e) {
            // FTB Library not loaded - this is normal if the mod isn't installed
            LOGGER.info("[ProgressiveStages] FTB Library StageHelper not found (class: {}) - stage provider not registered. This is normal if FTB Library is not installed.", e.getMessage());
            // Soft-disable: ProgressiveStages core features continue working
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            // FTB Library API changed - log clearly so users can report
            LOGGER.error("[ProgressiveStages] FTB Library API has changed and is incompatible.");
            LOGGER.error("[ProgressiveStages] Stage provider registration failed: {}", e.getMessage());
            LOGGER.error("[ProgressiveStages] FTB Quests stage tasks will NOT work with ProgressiveStages.");
            LOGGER.error("[ProgressiveStages] Please update ProgressiveStages or disable FTB integration in config.");
            LOGGER.error("[ProgressiveStages] Core features (EMI locks, item enforcement) will continue working.");
            // Soft-disable: don't crash, core features continue
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Unexpected error during FTB Library stage provider registration.");
            LOGGER.error("[ProgressiveStages] FTB Quests integration will be disabled. Core features remain active.");
            LOGGER.error("[ProgressiveStages] Error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            LOGGER.debug("[ProgressiveStages] Full stack trace:", e);
            // Soft-disable: don't crash, core features continue
        }
    }

    /**
     * Handle StageProvider interface method calls via proxy.
     * Interface methods: has(Player, String), add(ServerPlayer, String), remove(ServerPlayer, String), sync(ServerPlayer), getName()
     */
    private static Object handleProviderMethod(String methodName, Object[] args) {
        try {
            switch (methodName) {
                case "has" -> {
                    // has(Player player, String stage) - note: Player, not ServerPlayer
                    if (args != null && args.length == 2 && args[0] instanceof net.minecraft.world.entity.player.Player player && args[1] instanceof String stage) {
                        return hasStage(player, stage);
                    }
                    return false;
                }
                case "add" -> {
                    if (args != null && args.length == 2 && args[0] instanceof ServerPlayer player && args[1] instanceof String stage) {
                        addStage(player, stage);
                    }
                    return null;
                }
                case "remove" -> {
                    if (args != null && args.length == 2 && args[0] instanceof ServerPlayer player && args[1] instanceof String stage) {
                        removeStage(player, stage);
                    }
                    return null;
                }
                case "sync" -> {
                    // sync(ServerPlayer player) - default empty implementation in StageProvider
                    if (args != null && args.length == 1 && args[0] instanceof ServerPlayer player) {
                        syncStages(player);
                    }
                    return null;
                }
                case "getName" -> {
                    return PROVIDER_ID; // "ProgressiveStagesStageProvider"
                }
                // Object methods
                case "toString" -> { return PROVIDER_ID; }
                case "hashCode" -> { return System.identityHashCode(FtbQuestsHooks.class); }
                case "equals" -> { return args != null && args.length == 1 && args[0] == FtbQuestsHooks.class; }
                default -> {
                    LOGGER.debug("[ProgressiveStages] Unknown StageProvider method called: {}", methodName);
                    return null;
                }
            }
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] Error in stage provider method {}: {}", methodName, e.getMessage());
            return methodName.equals("has") ? false : null;
        }
    }

    private static boolean hasStage(net.minecraft.world.entity.player.Player player, String stage) {
        if (stage == null || stage.isEmpty()) return false;
        StageId stageId = StageId.parse(stage);

        boolean has;
        if (player instanceof ServerPlayer serverPlayer) {
            // Server-side: use the authoritative API
            has = ProgressiveStagesAPI.hasStage(serverPlayer, stageId);
        } else {
            // Client-side: use the client cache
            has = com.enviouse.progressivestages.client.ClientStageCache.hasStage(stageId);
        }

        LOGGER.debug("[ProgressiveStages] FTB Provider has('{}', '{}') = {}", player.getName().getString(), stage, has);
        return has;
    }

    private static void syncStages(ServerPlayer player) {
        // Sync stages to the client
        var stages = com.enviouse.progressivestages.common.stage.StageManager.getInstance().getStages(player);
        com.enviouse.progressivestages.common.network.NetworkHandler.sendStageSync(player, stages);
        LOGGER.debug("[ProgressiveStages] FTB Provider sync() called for {}", player.getName().getString());
    }

    private static void addStage(ServerPlayer player, String stage) {
        if (stage == null || stage.isEmpty() || stage.isBlank()) {
            LOGGER.warn("[ProgressiveStages] FTB Provider add() called with null/empty stage");
            return;
        }

        LOGGER.info("[ProgressiveStages] FTB Provider add() called - raw stage ID: '{}', player: {}", stage, player.getName().getString());

        // Normalize the stage ID (handles case differences, whitespace, etc.)
        StageId stageId;
        try {
            stageId = StageId.parse(stage.trim());
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] FTB Provider add() - invalid stage ID format '{}': {}", stage, e.getMessage());
            return;
        }

        LOGGER.info("[ProgressiveStages] FTB Provider add() - normalized to: '{}'", stageId);
        ProgressiveStagesAPI.grantStage(player, stageId, StageCause.QUEST_REWARD);
        LOGGER.info("[ProgressiveStages] FTB Provider add() completed - stage '{}' granted to {}", stageId, player.getName().getString());
    }

    private static void removeStage(ServerPlayer player, String stage) {
        if (stage == null || stage.isEmpty() || stage.isBlank()) {
            LOGGER.warn("[ProgressiveStages] FTB Provider remove() called with null/empty stage");
            return;
        }

        LOGGER.info("[ProgressiveStages] FTB Provider remove() called - raw stage ID: '{}', player: {}", stage, player.getName().getString());

        // Normalize the stage ID
        StageId stageId;
        try {
            stageId = StageId.parse(stage.trim());
        } catch (Exception e) {
            LOGGER.error("[ProgressiveStages] FTB Provider remove() - invalid stage ID format '{}': {}", stage, e.getMessage());
            return;
        }

        LOGGER.info("[ProgressiveStages] FTB Provider remove() - normalized to: '{}'", stageId);
        ProgressiveStagesAPI.revokeStage(player, stageId, StageCause.QUEST_REWARD);
        LOGGER.info("[ProgressiveStages] FTB Provider remove() completed - stage '{}' revoked from {}", stageId, player.getName().getString());
    }

    /**
     * Request FTB Quests to re-evaluate stage tasks for a player.
     *
     * <p>Calls {@code StageTask.checkStages(ServerPlayer)} which is documented as
     * "hook for FTB XMod Compat to call into".
     *
     * <p>Has a re-entrancy guard: if a recheck is already in progress for this player
     * (e.g., quest reward triggered another stage grant), the new recheck is skipped.
     * The caller should re-queue for next tick if needed.
     *
     * @param player The player to recheck
     * @return true if recheck was performed, false if skipped due to re-entrancy
     */
    public static boolean requestStageRecheck(ServerPlayer player) {
        if (player == null) return false;

        UUID playerId = player.getUUID();

        // Re-entrancy guard: skip if already rechecking this player
        if (recheckInProgress.contains(playerId)) {
            LOGGER.debug("[ProgressiveStages] Skipping recursive stage recheck for {} (already in progress)",
                player.getName().getString());
            return false;
        }

        // Lazy-load the method reference
        if (checkStagesMethod == null && !checkStagesWarned) {
            try {
                Class<?> stageTaskClass = Class.forName("dev.ftb.mods.ftbquests.quest.task.StageTask");
                checkStagesMethod = stageTaskClass.getMethod("checkStages", ServerPlayer.class);
                LOGGER.debug("[ProgressiveStages] Found StageTask.checkStages method");
            } catch (ClassNotFoundException e) {
                checkStagesWarned = true;
                LOGGER.debug("[ProgressiveStages] FTB Quests StageTask class not found");
            } catch (NoSuchMethodException e) {
                checkStagesWarned = true;
                LOGGER.warn("[ProgressiveStages] StageTask.checkStages method not found - FTB Quests API may have changed");
            }
        }

        if (checkStagesMethod == null) return false;

        try {
            // Mark recheck in progress
            recheckInProgress.add(playerId);

            checkStagesMethod.invoke(null, player);
            LOGGER.debug("[ProgressiveStages] Triggered stage recheck for {}", player.getName().getString());
            return true;
        } catch (Exception e) {
            if (!checkStagesWarned) {
                checkStagesWarned = true;
                LOGGER.error("[ProgressiveStages] Error calling StageTask.checkStages: {}", e.getMessage());
            }
            return false;
        } finally {
            // Always clear the guard
            recheckInProgress.remove(playerId);
        }
    }

    /**
     * Check if a recheck is currently in progress for a player.
     * Used by FTBQuestsCompat to know if it should re-queue.
     */
    public static boolean isRecheckInProgress(UUID playerId) {
        return recheckInProgress.contains(playerId);
    }

    /**
     * Clear all re-entrancy guards. Call on server stop.
     */
    public static void clearRecheckGuards() {
        recheckInProgress.clear();
    }

    /**
     * Check if the stage provider was successfully registered.
     */
    public static boolean isProviderRegistered() {
        return providerRegistered;
    }

    /**
     * Restore the previous stage provider (if one was replaced).
     * Call this when FTB integration is disabled at runtime.
     *
     * <p>This is primarily useful for dev environments where config may change.
     * In production, it's safer to just restart the server.
     *
     * @return true if previous provider was restored, false if none to restore
     */
    public static boolean restorePreviousProvider() {
        if (!providerRegistered) {
            LOGGER.debug("[ProgressiveStages] Provider not registered, nothing to restore");
            return false;
        }

        if (previousProvider == null) {
            LOGGER.debug("[ProgressiveStages] No previous provider to restore");
            return false;
        }

        if (stageHelperInstanceCache == null || setProviderMethodCache == null) {
            LOGGER.warn("[ProgressiveStages] Cannot restore provider - cached references not available");
            return false;
        }

        // Safety check: verify the previous provider's class is still valid/loadable
        try {
            Class<?> providerClass = previousProvider.getClass();
            // Try to invoke toString to verify the object is still usable
            String providerName = previousProvider.toString();
            if (providerName == null) {
                LOGGER.warn("[ProgressiveStages] Previous provider appears invalid (null toString)");
                previousProvider = null;
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("[ProgressiveStages] Previous provider is no longer valid: {}", e.getMessage());
            previousProvider = null;
            return false;
        }

        try {
            setProviderMethodCache.invoke(stageHelperInstanceCache, previousProvider);
            providerRegistered = false;
            LOGGER.info("[ProgressiveStages] Restored previous stage provider: {}", previousProvider);
            // Clear the previous provider reference after successful restore
            previousProvider = null;
            return true;
        } catch (Exception e) {
            // Restore failed - log once and leave current provider untouched
            LOGGER.error("[ProgressiveStages] Failed to restore previous provider, keeping current: {}", e.getMessage());
            // Don't clear previousProvider in case of failure - might succeed on retry
            return false;
        }
    }

    /**
     * Check if a previous provider was stored (can be restored).
     */
    public static boolean hasPreviousProvider() {
        return previousProvider != null;
    }

    /**
     * Clear the previous provider reference (e.g., on server stop).
     * This prevents attempting to restore a stale provider on next startup.
     */
    public static void clearPreviousProvider() {
        previousProvider = null;
    }
}

