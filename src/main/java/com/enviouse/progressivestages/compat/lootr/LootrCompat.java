package com.enviouse.progressivestages.compat.lootr;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Lootr compat entry point. Lootr's own Mixin-into-LootTable path already runs
 * <em>after</em> our Global Loot Modifier, so locked items are filtered out of every
 * LootTable fill before Lootr sees the loot — including Lootr's per-player rolls.
 * This module adds one extra integration: a ServiceLoader-registered
 * {@code ILootrFilterProvider} that uses Lootr's own player-aware context to filter,
 * catching any edge case where Lootr fills loot via a custom filler that bypasses
 * the standard LootTable path.
 *
 * <p>See {@link LootrStageFilterProvider} for the actual registration — Lootr's
 * {@code LootrServiceRegistry} loads providers via {@link java.util.ServiceLoader}
 * on startup, so our provider is picked up automatically when both mods are present.
 */
public final class LootrCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LootrCompat() {}

    public static void init() {
        // No runtime event subscription — Lootr picks up the filter via
        // META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider.
        LOGGER.info("[ProgressiveStages] Lootr compat active — loot gating runs at both GLM and Lootr-filter layers. "
            + "You can use 'tag:lootr:chests' / 'tag:lootr:barrels' / 'tag:lootr:shulkers' in any [blocks] or [screens] list.");
    }
}
