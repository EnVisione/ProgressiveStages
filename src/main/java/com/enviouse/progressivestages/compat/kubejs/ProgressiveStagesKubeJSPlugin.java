package com.enviouse.progressivestages.compat.kubejs;

import com.enviouse.progressivestages.common.compat.ScriptHooks;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * v2.5: the ProgressiveStages KubeJS plugin (discovered via {@code kubejs.plugins.txt}).
 *
 * <p>Binds the global {@code ProgressiveStages} object ({@link PSKubeBindings}) into every script
 * context so packs can react to stage grants/revokes, register fully custom {@code script:} trigger
 * conditions, and grant/revoke/query stages imperatively. The {@link ScriptHooks} registry is reset
 * at the start of each SERVER-script reload so {@code /reload} doesn't stack duplicate handlers.
 */
public class ProgressiveStagesKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // Server scripts own the lifecycle/condition registrations; clear them once per server reload.
        if (bindings.type() == ScriptType.SERVER) {
            ScriptHooks.reset();
        }
        bindings.add("ProgressiveStages", new PSKubeBindings());
    }
}
