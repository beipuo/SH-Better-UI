package com.dishanhai.sh_better_ui.compat.kubejs;

import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import com.dishanhai.sh_better_ui.compat.kubejs.client.KubeJsUiApi;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

/** 山海：KubeJS 6 客户端绑定，由带 client 标记的插件资源发现。 */
public final class ShBetterUiKubeJsPlugin extends KubeJSPlugin {
    private KubeJsUiApi clientApi;

    @Override
    public synchronized void registerBindings(BindingsEvent event) {
        if (event.getType() != ScriptType.CLIENT) return;
        if (clientApi != null) clientApi.close();
        clientApi = new KubeJsUiApi();
        event.add("SHUI", clientApi);
        if (VanillaTextEffects.handlesLegacy()) {
            event.add("ShanhaiText", clientApi.text());
            event.add("ShanhaiStyleRegistry", clientApi.text());
        }
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        if (type != ScriptType.CLIENT) return;
        filter.allow("com.dishanhai.sh_better_ui.client.");
        filter.allow("com.dishanhai.sh_better_ui.config.");
        filter.allow("com.dishanhai.sh_better_ui.compat.kubejs.client.");
    }
}
