package com.enviouse.progressivestages.compat.curios;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.EnchantEnforcer;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Curios soft-dep compat. Performed via reflection so ProgressiveStages has no hard
 * dependency on the Curios API jar — if the Curios API shape changes between versions
 * we degrade gracefully instead of crashing the server.
 *
 * <p>What this does:
 * <ol>
 *   <li>Periodically scans the player's curios inventory and ejects any stack whose
 *       underlying item is locked for them (matches ItemEnforcer's scan semantics,
 *       extended into curio slots which {@code InventoryScanner} can't reach).</li>
 *   <li>Also strips locked enchantments from curio-slot items via the same path as
 *       the main-inventory scan.</li>
 *   <li>Enforces {@code [curios].locked_slots} — any stack living in a locked slot
 *       identifier is ejected when the owner lacks the stage.</li>
 * </ol>
 *
 * <p>Everything is guarded by {@code try/catch Throwable} and the work-list is built
 * from reflection on {@code top.theillusivec4.curios.api.CuriosApi}. If reflection
 * fails at any step, the compat silently no-ops — item-level locking from
 * {@code [items]} still applies because Curios items are normal {@link Item}s.
 */
public final class CuriosCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Method getCuriosInventoryMethod;
    private static Method getCuriosMethod;
    private static Method getSlotsMethod;
    private static Method getStackInSlotMethod;
    private static Method setStackInSlotMethod;
    private static Method getSlotIdentifierMethod;
    private static boolean apiAvailable;

    private CuriosCompat() {}

    public static void init() {
        resolveApi();
        if (!apiAvailable) {
            LOGGER.info("[ProgressiveStages] Curios detected but its API couldn't be resolved — falling back "
                + "to item-level locking only. [items] locks still apply to curio-capable items.");
            return;
        }
        NeoForge.EVENT_BUS.register(CuriosCompat.class);
        LOGGER.info("[ProgressiveStages] Curios compat active — scanning curio slots each tick sweep.");
    }

    private static void resolveApi() {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            // The method signature changed across Curios versions; try the two common shapes.
            try {
                getCuriosInventoryMethod = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            } catch (NoSuchMethodException e) {
                getCuriosInventoryMethod = api.getMethod("getCuriosHelper");
            }

            // ICuriosItemHandler → Map<String, ICurioStacksHandler> getCurios()
            Class<?> itemHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICuriosItemHandler");
            getCuriosMethod = itemHandler.getMethod("getCurios");

            Class<?> stacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            getSlotsMethod = stacksHandler.getMethod("getSlots");
            // ICurioStacksHandler → IDynamicStackHandler getStacks()
            // IDynamicStackHandler extends IItemHandler
            Method getStacks = stacksHandler.getMethod("getStacks");
            Class<?> iItemHandler = Class.forName("net.neoforged.neoforge.items.IItemHandler");
            getStackInSlotMethod = iItemHandler.getMethod("getStackInSlot", int.class);
            Class<?> dynamicStacks = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            setStackInSlotMethod = dynamicStacks.getMethod("setStackInSlot", int.class, ItemStack.class);

            apiAvailable = true;
        } catch (Throwable t) {
            apiAvailable = false;
            LOGGER.debug("[ProgressiveStages] Curios API not resolvable: {}", t.toString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!apiAvailable) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Run at the same cadence as the main inventory scan to avoid per-tick cost.
        int scanFrequency = StageConfig.getInventoryScanFrequency();
        if (scanFrequency <= 0) return;
        long gameTime = player.level().getGameTime();
        if (gameTime % Math.max(scanFrequency, 1) != 0) return;

        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        try {
            scanAndEject(player);
        } catch (Throwable t) {
            // One-shot log — then disable further scans to avoid log spam.
            LOGGER.warn("[ProgressiveStages] Curios scan failed; disabling compat: {}", t.toString());
            apiAvailable = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void scanAndEject(ServerPlayer player) throws Exception {
        // CuriosApi.getCuriosInventory(player) returns Optional<ICuriosItemHandler> in modern versions
        Object result = getCuriosInventoryMethod.invoke(null, player);
        Object inventory = unwrapOptional(result);
        if (inventory == null) return;

        Map<String, Object> curios = (Map<String, Object>) getCuriosMethod.invoke(inventory);
        if (curios == null || curios.isEmpty()) return;

        LockRegistry registry = LockRegistry.getInstance();

        for (Map.Entry<String, Object> slotEntry : curios.entrySet()) {
            String slotId = slotEntry.getKey();
            Object stacksHandler = slotEntry.getValue();
            if (stacksHandler == null) continue;

            // v2.0: multi-stage slot gate. Slot is blocked if the player lacks ANY stage gating it.
            boolean slotBlocked = registry.isCurioSlotBlockedFor(player, slotId);
            Optional<StageId> slotPrimaryStage = registry.primaryRestrictingStageForCurioSlot(player, slotId);

            int slots = (int) getSlotsMethod.invoke(stacksHandler);
            Object stacksWrapper = stacksHandler.getClass().getMethod("getStacks").invoke(stacksHandler);
            if (stacksWrapper == null) continue;

            for (int i = 0; i < slots; i++) {
                ItemStack stack = (ItemStack) getStackInSlotMethod.invoke(stacksWrapper, i);
                if (stack == null || stack.isEmpty()) continue;

                boolean ejected = false;
                if (slotBlocked) {
                    ejected = true;
                } else if (registry.isItemBlockedFor(player, stack.getItem())) {
                    // v2.0: multi-stage item gate.
                    ejected = true;
                }

                if (ejected) {
                    ItemStack dropped = stack.copy();
                    setStackInSlotMethod.invoke(stacksWrapper, i, ItemStack.EMPTY);
                    if (!player.getInventory().add(dropped)) {
                        player.drop(dropped, false);
                    }
                    if (slotBlocked && slotPrimaryStage.isPresent()) {
                        ItemEnforcer.notifyLockedWithCooldown(player, slotPrimaryStage.get(), "Curio slot '" + slotId + "'");
                    } else {
                        ItemEnforcer.notifyLockedWithCooldown(player, stack.getItem());
                    }
                    continue;
                }

                // Enchant strip on curio-held items.
                if (EnchantEnforcer.stripLockedEnchants(player, stack)) {
                    setStackInSlotMethod.invoke(stacksWrapper, i, stack);
                }
            }
        }
    }

    private static Object unwrapOptional(Object o) {
        if (o == null) return null;
        if (o instanceof java.util.Optional<?> opt) return opt.orElse(null);
        // Older Curios versions returned LazyOptional — resolve via reflection.
        try {
            Method resolve = o.getClass().getMethod("resolve");
            Object r = resolve.invoke(o);
            if (r instanceof java.util.Optional<?> opt) return opt.orElse(null);
            return r;
        } catch (Throwable ignored) {
            return o; // Hope it's already the handler.
        }
    }
}
