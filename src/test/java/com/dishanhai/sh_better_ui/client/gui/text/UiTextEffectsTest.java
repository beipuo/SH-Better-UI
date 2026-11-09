package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证内联兼容、注册隔离与元色板的行为。 */
class UiTextEffectsTest {
    @Test
    void parsesMultipleLegacySpansAndRestoresTheOuterStyle() {
        Component parsed = UiTextEffects.parse("A {fire}B <ice>C</> D{/} &$全-§cE&r F {{golden}}G{{/}}");
        List<String> runs = new ArrayList<>();
        parsed.visit((style, text) -> {
            if (!text.isEmpty()) runs.add(text + ":" + (UiTextEffects.format(style) != null));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertEquals("A B C D E F G", parsed.getString());
        assertEquals(List.of("A :false", "B :true", "C:true", " D:true", " :false", "E:true",
                " F :false", "G:true"), runs);
        var frame = UiFormattedText.colorize(parsed.getVisualOrderText(), 0xffffffff, 0,
                com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT);
        frame.accept((index, style, codePoint) -> {
            if (codePoint == 'E') assertEquals(0xff5555, style.getColor().getValue());
            return true;
        });
    }

    @Test
    void guideMeKeepsDifferentEffectsOnIdenticalWrappedText() {
        var layout = new GuideMeTextEffects.Layout(UiTextEffects.parse("{fire}同{/} {ice}同{/}"));
        assertEquals("同 同", layout.text());
        var firstText = new String("同");
        var secondText = new String("同");
        layout.prepareRun(firstText);
        var first = GuideMeTextEffects.takeRun(firstText);
        layout.prepareRun(secondText);
        var second = GuideMeTextEffects.takeRun(secondText);
        assertNotNull(first);
        assertNotNull(second);
        var argument = GuideMeTextEffects.beginDraw(secondText, second);
        try {
            assertSame(first, GuideMeTextEffects.applyTo(first));
            var base = Component.literal(argument).withStyle(Style.EMPTY.withBold(true));
            var styled = GuideMeTextEffects.applyTo(base);
            assertEquals("同", styled.getString());
            assertTrue(styled.getStyle().isBold());
            assertNotEquals(first.getSiblings().getFirst().getStyle().getInsertion(),
                    styled.getSiblings().getFirst().getStyle().getInsertion());
        } finally {
            GuideMeTextEffects.endDraw();
        }
    }

    @Test
    void preservesPlainTextAndKeepsFormattingWithinTheEffectSpan() {
        Style original = Style.EMPTY.withBold(true).withColor(0x123456);
        Component parsed = UiTextEffects.parse("R&D-tools &~$山海朝霞-星😀", original);
        Component body = parsed.getSiblings().getFirst();

        assertEquals("R&D-tools 星😀", parsed.getString());
        assertEquals(original, parsed.getStyle());
        assertTrue(body.getStyle().isBold());
        assertEquals(original.getColor(), body.getStyle().getColor());
        assertTrue(body.getStyle().getInsertion().startsWith("shbui:fcs:"));
        assertNull(UiTextEffects.format(parsed.getStyle()));
        assertNotNull(UiTextEffects.format(body.getStyle()));
        assertEquals("A &$unknown-text", UiTextEffects.parse("A &$unknown-text").getString());
        assertEquals("星河", UiTextEffects.parse("&$山海青岚星河").getString());
        assertEquals("&$golden-", UiTextEffects.parse("&$golden-").getString());
        assertEquals("&$全随机", UiTextEffects.parse("&$全随机").getString());
    }

    @Test
    void legacyInsertionsAndNewMarkersSampleTheSameBuiltInEffects() {
        Style oldStyle = Style.EMPTY.withInsertion("fcs:ultimate|@|#");
        Style newStyle = UiTextEffects.parse("&@#$ultimate-Text").getStyle();
        assertEquals(sample(newStyle, 830, false), sample(oldStyle, 830, false));

        UiTextEffects.Glyph fixed = sample(oldStyle, 830, true);
        assertEquals(fixed, sample(oldStyle, 1900, true));
        assertEquals(0f, fixed.x());
        assertEquals(0f, fixed.y());
        assertTrue(fixed.outline());
    }

    @Test
    void normalizesLocalizedSegmentsAndKeepsTheirInheritedStyles() {
        Component plain = Component.literal("R&D-tools");
        assertSame(plain, UiTextEffects.normalize(plain));
        Component argument = Component.literal("player").withStyle(style -> style.withItalic(true));
        Component translated = Component.translatableWithFallback("sh_better_ui.test.text_missing",
                "&$rainbow-Hello %s!", argument).withStyle(style -> style.withBold(true));

        Component normalized = UiTextEffects.normalize(translated);
        assertEquals("Hello player!", normalized.getString());
        assertNotNull(UiTextEffects.format(normalized.getSiblings().getFirst().getStyle()));
        assertTrue(normalized.getSiblings().get(1).getStyle().isBold());
        assertTrue(normalized.getSiblings().get(1).getStyle().isItalic());
        assertNull(UiTextEffects.format(normalized.getSiblings().get(1).getStyle()));
    }

    @Test
    void externalPalettesAreNamespacedAndDefensivelyCopied() {
        ResourceLocation id = ResourceLocation.tryParse("test_ui:coral-sky");
        int[] stops = {0xFF3344, 0x3355FF};
        UiTextEffects.registerPalette(id, stops);
        stops[1] = 0;

        Style inline = UiTextEffects.parse("&$test_ui:coral-sky-Text").getStyle();
        Style typed = UiTextEffects.apply(Component.literal("Text"), id).getStyle();
        assertEquals(0x3355FF, sample(typed, 0, false).rgb());
        assertEquals(sample(typed, 0, false), sample(inline, 0, false));
        assertThrows(IllegalArgumentException.class, () -> UiTextEffects.registerPalette(id, 0xFF0000));
        assertThrows(IllegalArgumentException.class,
                () -> UiTextEffects.registerPalette(ResourceLocation.tryParse("sh_better_ui:golden"), 0));
    }

    @Test
    void registersAnimationVariantsAndRemovesOnlyExternalStyles() {
        var base = ResourceLocation.tryParse("test_ui:variants");
        var ids = UiTextEffects.registerPaletteVariants(base, false, new int[]{0xff0000, 0x00ff00},
                UiTextEffects.PaletteAnimation.STATIC, UiTextEffects.PaletteAnimation.WOBBLE);
        try {
            assertEquals(2, ids.size());
            assertTrue(UiTextEffects.styleNames().contains(ids.getFirst().toString()));
            assertEquals(0x00ff00, UiTextEffects.paletteColor(ids.getFirst().toString(), 0, 1, 0,
                    UiTextEffects.Timing.DEFAULT));
            assertThrows(IllegalArgumentException.class, () -> UiTextEffects.registerPaletteVariants(
                    base, false, new int[]{0x112233}, UiTextEffects.PaletteAnimation.SCAN,
                    UiTextEffects.PaletteAnimation.STATIC));
            assertFalse(UiTextEffects.styleNames().contains(base + "_scan"));
        } finally {
            ids.forEach(UiTextEffects::unregister);
        }
        assertFalse(UiTextEffects.styleNames().contains(ids.getFirst().toString()));
        assertFalse(UiTextEffects.unregister(ResourceLocation.tryParse("sh_better_ui:rainbow")));
    }

    @Test
    void customEffectReceivesUnicodeAndHonorsReducedMotion() {
        ResourceLocation id = ResourceLocation.tryParse("test_ui:sample_effect");
        AtomicReference<UiTextEffects.GlyphContext> received = new AtomicReference<>();
        UiTextEffects.registerEffect(id, context -> {
            received.set(context);
            return new UiTextEffects.Glyph(0x123456, 2, -1, true, true);
        });
        Style style = UiTextEffects.apply(Component.literal("A😀"), id).getStyle();

        assertEquals(new UiTextEffects.Glyph(0x123456, 2, -1, true, true), sample(style, 57, false));
        assertEquals(0x1F600, received.get().codePoint());
        assertEquals(1, received.get().index());
        assertEquals(2, received.get().length());
        assertEquals(57, received.get().timeMillis());
        assertEquals(new UiTextEffects.Glyph(0x123456, 0, 0, true, false), sample(style, 57, true));
        assertTrue(received.get().reducedMotion());
    }

    @Test
    void allModeHasNoImplicitMotionAndRandomShufflesEachRound() {
        Style all = UiTextEffects.parse("&$全-Text").getStyle();
        Style random = UiTextEffects.parse("&$随机-Text").getStyle();
        boolean sawRandomOffset = false;
        for (long time = 0; time < 40_000; time += 100) {
            UiTextEffects.Glyph fixed = sample(all, time, false);
            assertEquals(0f, fixed.x());
            assertEquals(0f, fixed.y());
            UiTextEffects.Glyph varying = sample(random, time, false);
            sawRandomOffset |= varying.x() != 0 || varying.y() != 0;
        }
        assertTrue(sawRandomOffset);
        Style chaos = UiTextEffects.parse("&$全随机-Text").getStyle();
        assertNotEquals(sample(chaos, 0, false).rgb(), sample(chaos, 480, false).rgb());
        assertSame(TextPalettes.randomFrame(27), TextPalettes.randomFrame(27));

        int count = 9;
        for (int cycle = -2; cycle < 3; cycle++) {
            int start = cycle * count;
            Set<Integer> order = new HashSet<>();
            for (int slot = start; slot < start + count; slot++) order.add(TextPalettes.pickPreset(slot, count));
            assertEquals(count, order.size());
            assertNotEquals(TextPalettes.pickPreset(start - 1, count), TextPalettes.pickPreset(start, count));
        }
    }

    private static UiTextEffects.Glyph sample(Style style, long time, boolean reducedMotion) {
        return UiTextEffects.sample(UiTextEffects.format(style), 0xABCDEF, 1, 2, 0x1F600, time, reducedMotion);
    }
}
