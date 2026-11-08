package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 山海：在 GUI 中绘制带有 {@link UiTextEffects} 标记的原版文字。 */
public final class UiFormattedText {
    private UiFormattedText() {}

    /** 输入可以是翻译组件，也可以是已按视觉顺序排列的一行文字。 */
    public static int draw(GuiGraphics graphics, Font font, Component text, float x, float y,
                           int argb, boolean shadow, UiPreferences preferences) {
        return draw(graphics, font, text.getVisualOrderText(), x, y, argb, shadow, preferences);
    }

    /** 逐字位移和描边只影响绘制。返回值仍按原版字宽计算。 */
    public static int draw(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y,
                           int argb, boolean shadow, UiPreferences preferences) {
        List<Glyph> glyphs = sample(text, argb, PageTransition.nowMs(), preferences);
        if (glyphs == null) return graphics.drawString(font, text, x, y, argb, shadow);
        if (glyphs.stream().noneMatch(Glyph::positioned)) {
            return graphics.drawString(font, sequence(glyphs), x, y, argb, shadow);
        }

        var pose = graphics.pose().last().pose();
        var buffers = graphics.bufferSource();
        float[] positions = new float[glyphs.size()];
        float cursor = x;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            positions[i] = cursor + glyph.x();
            cursor += font.getSplitter().stringWidth(glyph.sequence());
            if (glyph.outline()) {
                FormattedCharSequence outline = FormattedCharSequence.codepoint(glyph.codePoint(),
                        glyph.style().withColor(0x000000));
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dy != 0) {
                            font.drawInBatch(outline, positions[i] + dx, y + glyph.y() + dy, argb, false,
                                    pose, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            font.drawInBatch(glyph.sequence(), positions[i], y + glyph.y(), argb, shadow,
                    pose, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        graphics.flush();
        return (int) (cursor + (shadow ? 1 : 0));
    }

    /** 生成当前颜色帧，可传给原版 tooltip 或其他只接受 FormattedCharSequence 的接口。 */
    public static FormattedCharSequence colorize(FormattedCharSequence text, int argb,
                                                  long timeMillis, UiPreferences preferences) {
        List<Glyph> glyphs = sample(text, argb, timeMillis, preferences);
        return glyphs == null ? text : sequence(glyphs);
    }

    private static List<Glyph> sample(FormattedCharSequence text, int argb, long timeMillis,
                                      UiPreferences preferences) {
        boolean[] hasEffect = {false};
        text.accept((index, style, codePoint) -> {
            if (UiTextEffects.format(style) == null) return true;
            hasEffect[0] = true;
            return false;
        });
        if (!hasEffect[0]) return null;
        List<Glyph> glyphs = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new Glyph(index, codePoint, style, 0, 0, false));
            return true;
        });
        boolean marked = false;
        for (int from = 0; from < glyphs.size();) {
            String marker = glyphs.get(from).style().getInsertion();
            int to = from + 1;
            while (to < glyphs.size() && Objects.equals(marker, glyphs.get(to).style().getInsertion())) to++;
            UiTextEffects.Format format = UiTextEffects.format(glyphs.get(from).style());
            if (format != null) {
                marked = true;
                for (int i = from; i < to; i++) {
                    Glyph glyph = glyphs.get(i);
                    Style style = glyph.style().withInsertion(null);
                    if (preferences.highContrast()) {
                        glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), style, 0, 0, false));
                        continue;
                    }
                    int rgb = style.getColor() == null ? argb & 0xffffff : style.getColor().getValue();
                    UiTextEffects.Glyph sample = UiTextEffects.sample(format, rgb, i - from, to - from,
                            glyph.codePoint(), timeMillis, preferences.reducedMotion());
                    if (sample.rgb() != rgb) style = style.withColor(sample.rgb());
                    if (sample.obfuscated()) style = style.withObfuscated(true);
                    glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), style,
                            sample.x(), sample.y(), sample.outline()));
                }
            }
            from = to;
        }
        return marked ? glyphs : null;
    }

    private static FormattedCharSequence sequence(List<Glyph> glyphs) {
        return sink -> {
            for (Glyph glyph : glyphs) {
                if (!sink.accept(glyph.index(), glyph.style(), glyph.codePoint())) return false;
            }
            return true;
        };
    }

    private record Glyph(int index, int codePoint, Style style, float x, float y, boolean outline) {
        boolean positioned() {
            return x != 0 || y != 0 || outline;
        }

        FormattedCharSequence sequence() {
            return FormattedCharSequence.codepoint(codePoint, style);
        }
    }
}
