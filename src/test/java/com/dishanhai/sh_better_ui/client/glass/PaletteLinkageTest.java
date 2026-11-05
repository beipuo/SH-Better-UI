package com.dishanhai.sh_better_ui.client.glass;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：更新独立库的颜色必须立即影响消费端，禁止将色板内联进主模组。 */
class PaletteLinkageTest {
    @Test
    void publicPaletteTokensHaveNoCompileTimeConstantValue() throws IOException {
        try (var input = GlassPalette.class.getResourceAsStream("GlassPalette.class")) {
            assertNotNull(input);
            int[] tokens = {0};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    if ((access & Opcodes.ACC_PUBLIC) != 0 && descriptor.equals("I")) {
                        tokens[0]++;
                        assertNull(value, name + " must be read from the external library at runtime");
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(tokens[0] > 20);
        }
    }
}
