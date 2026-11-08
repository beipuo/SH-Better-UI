package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证混合视觉序列、Unicode 码点及偏好回退。 */
class UiFormattedTextTest {
    @Test
    void effectsStayInsideTheMarkedRunAndKeepUnicodeAndNativeStyle() {
        var effect = ResourceLocation.tryParse("test:formatted_text_unicode");
        var seen = new ArrayList<UiTextEffects.GlyphContext>();
        UiTextEffects.registerEffect(effect, context -> {
            seen.add(context);
            return new UiTextEffects.Glyph(0x44ddbb, 0, 1, false, false);
        });
        Component marked = UiTextEffects.apply(Component.literal("山🌊").withStyle(Style.EMPTY.withBold(true)), effect);
        var original = Component.empty()
                .append(Component.literal("前").withStyle(Style.EMPTY.withInsertion("vanilla insertion")))
                .append(marked).append(Component.literal("後")).getVisualOrderText();

        var frame = UiFormattedText.colorize(original, 0xffffffff, 350, UiPreferences.DEFAULT);
        var glyphs = collect(frame);
        assertEquals(List.of((int) '前', (int) '山', 0x1f30a, (int) '後'),
                glyphs.stream().map(StyledGlyph::codePoint).toList());
        assertEquals(List.of(0, 1), seen.stream().map(UiTextEffects.GlyphContext::index).toList());
        assertTrue(seen.stream().allMatch(context -> context.length() == 2 && context.timeMillis() == 350));
        assertEquals("vanilla insertion", glyphs.getFirst().style().getInsertion());
        assertTrue(glyphs.get(1).style().isBold());
        assertEquals(0x44ddbb, glyphs.get(2).style().getColor().getValue());
        assertNull(glyphs.get(2).style().getInsertion());
        assertNull(glyphs.getLast().style().getColor());
        assertFalse(frame.accept((index, style, codePoint) -> false));
    }

    @Test
    void highContrastKeepsTheNativeColorAndPlainTextPassesThrough() {
        var plain = FormattedCharSequence.forward("plain", Style.EMPTY);
        assertSame(plain, UiFormattedText.colorize(plain, 0xffffffff, 0, UiPreferences.DEFAULT));
        var plainComponent = Component.literal("plain").getVisualOrderText();
        assertSame(plainComponent, UiFormattedText.colorize(plainComponent, 0xffffffff, 0, UiPreferences.DEFAULT));

        var marked = FormattedCharSequence.forward("山", Style.EMPTY.withColor(0xaabbcc)
                .withInsertion("fcs:shanhai_dawn|~"));
        var frame = UiFormattedText.colorize(marked, 0xffffffff, 250, new UiPreferences(true, false, true));
        Style style = collect(frame).getFirst().style();
        assertEquals(0xaabbcc, style.getColor().getValue());
        assertNull(style.getInsertion());
    }

    private static List<StyledGlyph> collect(FormattedCharSequence text) {
        var glyphs = new ArrayList<StyledGlyph>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new StyledGlyph(codePoint, style));
            return true;
        });
        return glyphs;
    }

    private record StyledGlyph(int codePoint, Style style) {}
}
