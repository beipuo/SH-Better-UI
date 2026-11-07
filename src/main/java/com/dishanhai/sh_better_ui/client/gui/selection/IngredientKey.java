package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** 山海：活跃候选共用形态键，释放候选后允许其 NBT／组件身份一起回收。 */
final class IngredientKey {
    private static final WeakHashMap<Identity, WeakReference<IngredientKey>> KEYS = new WeakHashMap<>();
    private static long nextKey;

    private final Identity identity;
    private final String value;

    private IngredientKey(Identity identity) {
        this.identity = identity;
        value = "sh_better_ui:selection/" + nextKey++;
    }

    static synchronized IngredientKey of(Kind kind, ResourceLocation id, Object data) {
        var identity = new Identity(kind, id, data);
        var reference = KEYS.get(identity);
        var existing = reference == null ? null : reference.get();
        if (existing != null) return existing;
        var key = new IngredientKey(identity);
        KEYS.remove(identity);
        KEYS.put(key.identity, new WeakReference<>(key));
        return key;
    }

    String value() { return value; }

    private record Identity(Kind kind, ResourceLocation id, Object data) {}
}
