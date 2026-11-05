package com.dishanhai.sh_better_ui;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 山海：共同入口必须能在禁止客户端及可选 LDLib 类的 ClassLoader 中实例化。 */
class CommonEntryIsolationTest {
    @Test
    void entryLoadsWithoutClientOrLdlibClasses() throws Exception {
        String entry = "com.dishanhai.sh_better_ui.ShBetterUi";
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.")
                        || name.startsWith("com.dishanhai.sh_better_ui.client.")
                        || name.startsWith("com.dishanhai.sh_better_ui.compat.")
                        || name.startsWith("com.lowdragmc.")) {
                    throw new ClassNotFoundException("Server isolation: " + name);
                }
                if (!name.equals(entry)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException ex) {
                            throw new ClassNotFoundException(name, ex);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        assertNotNull(Class.forName(entry, true, loader).getConstructor().newInstance());
    }
}
