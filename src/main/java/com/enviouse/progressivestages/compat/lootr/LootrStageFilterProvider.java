package com.enviouse.progressivestages.compat.lootr;

import noobanidus.mods.lootr.common.api.filter.ILootrFilter;
import noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider;

import java.util.List;

/**
 * Loaded by Lootr's {@code LootrServiceRegistry} via {@link java.util.ServiceLoader}.
 * Registered through {@code META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider}.
 *
 * <p>When Lootr isn't on the classpath the ServiceLoader lookup inside Lootr never runs,
 * so this class stays dormant — it only class-loads when Lootr asks for providers.
 */
public final class LootrStageFilterProvider implements ILootrFilterProvider {

    private static final List<ILootrFilter> FILTERS = List.of(new LootrStageFilter());

    @Override
    public List<ILootrFilter> getFilters() {
        return FILTERS;
    }
}
