package com.dishanhai.sh_better_ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 山海：两个加载器的共同入口不能引用客户端或可选 LDLib 类。 */
class CommonEntryIsolationTest {
    @Test
    void entryDoesNotReferenceClientOrLdlibClasses() throws Exception {
        try (var input = ShBetterUi.class.getResourceAsStream("ShBetterUi.class")) {
            assertNotNull(input);
            var entry = new ClassReader(input);
            char[] buffer = new char[entry.getMaxStringLength()];
            for (int i = 1; i < entry.getItemCount(); i++) {
                int item = entry.getItem(i);
                if (item == 0 || entry.readByte(item - 1) != 7) continue; // CONSTANT_Class
                String name = entry.readUTF8(item, buffer);
                assertFalse(name.startsWith("net/minecraft/client/")
                        || name.startsWith("com/dishanhai/sh_better_ui/client/")
                        || name.startsWith("com/dishanhai/sh_better_ui/compat/")
                        || name.startsWith("com/lowdragmc/"), name);
            }
        }
    }
}
