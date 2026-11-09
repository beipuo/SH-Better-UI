// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.render;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 山海：兼容 Forge 发布包的 SRG 名称和 NeoForge 的官方名称。 */
public final class LyricGlyphAccess {
    private LyricGlyphAccess() {}

    public static MethodHandle fontSet() throws ReflectiveOperationException {
        return method(Font.class, "getFontSet", "m_92863_", ResourceLocation.class);
    }

    public static MethodHandle method(Class<?> owner, String name, String srg, Class<?>... arguments)
            throws ReflectiveOperationException {
        Method method;
        try {
            method = owner.getDeclaredMethod(name, arguments);
        } catch (NoSuchMethodException ignored) {
            method = owner.getDeclaredMethod(srg, arguments);
        }
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    }

    public static Field field(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 当前平台使用另一个映射名称。
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + names[0]);
    }

    static VarHandle floatField(MethodHandles.Lookup lookup, Class<?> owner, String name, String srg)
            throws ReflectiveOperationException {
        try {
            return lookup.findVarHandle(owner, name, float.class);
        } catch (NoSuchFieldException ignored) {
            return lookup.findVarHandle(owner, srg, float.class);
        }
    }
}
