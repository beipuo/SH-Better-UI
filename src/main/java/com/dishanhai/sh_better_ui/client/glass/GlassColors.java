package com.dishanhai.sh_better_ui.client.glass;

/** 山海：共用深色与中性浅色玻璃的语义色；仅决定显示颜色，不读取业务配置。 */
public final class GlassColors {
    public static final GlassColors DARK = new GlassColors(
            GlassPalette.ACCENT, GlassPalette.TEXT, GlassPalette.TEXT_HEAD, GlassPalette.TEXT_MUTED,
            GlassPalette.TEXT_DIM, GlassPalette.TEXT_OFF, GlassPalette.OK,
            GlassPalette.BUTTON_EDGE, GlassPalette.BUTTON_FILL, GlassPalette.BUTTON_FILL_HOVER,
            GlassPalette.DISPLAY_EDGE, GlassPalette.DISPLAY_FILL, GlassPalette.PANEL_FILL,
            GlassPalette.ROW_EDGE, GlassPalette.ROW_FILL, GlassPalette.ROW_FILL_HOVER,
            GlassPalette.ROOT_EDGE, GlassPalette.CELL_FILL, GlassPalette.LIT);

    /** 山海：柔和浅灰；根板承托深色文字，内层薄叠色，避免按钮和高光叠成亮白板。 */
    public static final GlassColors CLEAR = new GlassColors(
            0xFF276B8B, 0xFF1C2B35, 0xFF194A61, 0xFF3B5363,
            0xFF4D6471, 0xFF75818A, 0xFF276C50,
            0x70D7E1E6, 0x40C1CCD2, 0x66CBD5DB,
            0x407C8E98, 0x18A9B6BE, 0xBBC5CDD2,
            0x507C8E98, 0x20B5C1C8, 0x40C5D0D6,
            0x80DAE3E8, 0x20B5C1C8, 0x24FFFFFF);

    /** 山海：低亮度中性烟灰；内层只做轻微分区，避免多层控件叠成白板。 */
    public static final GlassColors SMOKE = new GlassColors(
            0xFF86B5BF, 0xFFD3D8DB, 0xFFE0E4E6, 0xFFADB6BC,
            0xFF96A1A8, 0xFF7C878E, 0xFF8EB59B,
            0x40A8B0B4, 0x30454C52, 0x5050585E,
            0x30A8B0B4, 0x2420272B, 0xCC24282C,
            0x30A8B0B4, 0x2420272B, 0x40454C52,
            0x40A8B0B4, 0x2420272B, 0x10FFFFFF);

    public final int ACCENT, TEXT, TEXT_HEAD, TEXT_MUTED, TEXT_DIM, TEXT_OFF, OK;
    public final int BUTTON_EDGE, BUTTON_FILL, BUTTON_FILL_HOVER;
    public final int DISPLAY_EDGE, DISPLAY_FILL, PANEL_FILL;
    public final int ROW_EDGE, ROW_FILL, ROW_FILL_HOVER;
    public final int ROOT_EDGE, CELL_FILL, LIT;

    private GlassColors(int accent, int text, int textHead, int textMuted, int textDim, int textOff, int ok,
                        int buttonEdge, int buttonFill, int buttonFillHover,
                        int displayEdge, int displayFill, int panelFill,
                        int rowEdge, int rowFill, int rowFillHover,
                        int rootEdge, int cellFill, int lit) {
        ACCENT = accent; TEXT = text; TEXT_HEAD = textHead; TEXT_MUTED = textMuted;
        TEXT_DIM = textDim; TEXT_OFF = textOff; OK = ok;
        BUTTON_EDGE = buttonEdge; BUTTON_FILL = buttonFill; BUTTON_FILL_HOVER = buttonFillHover;
        DISPLAY_EDGE = displayEdge; DISPLAY_FILL = displayFill; PANEL_FILL = panelFill;
        ROW_EDGE = rowEdge; ROW_FILL = rowFill; ROW_FILL_HOVER = rowFillHover;
        ROOT_EDGE = rootEdge; CELL_FILL = cellFill; LIT = lit;
    }

    public static GlassColors select(boolean clear) { return clear ? CLEAR : DARK; }

    /** 山海：可复用的深色染色玻璃，结构色随基底变化，正文和交易语义保持可读。 */
    public static GlassColors tintedDark(int accent, int surface) {
        int rgb = surface & 0xFFFFFF;
        int tint = accent & 0xFFFFFF;
        return new GlassColors(0xFF000000 | tint, DARK.TEXT, 0xFF000000 | mix(tint, 0xFFFFFF, 0.65f),
                DARK.TEXT_MUTED, DARK.TEXT_DIM, DARK.TEXT_OFF, DARK.OK,
                0x70000000 | tint, 0xB3000000 | mix(rgb, tint, 0.12f), 0xDC000000 | mix(rgb, tint, 0.26f),
                0x50000000 | tint, 0x24000000 | rgb, 0xA6000000 | rgb,
                0x50000000 | tint, 0x99000000 | rgb, 0xCC000000 | mix(rgb, tint, 0.2f),
                0x90000000 | tint, 0x99000000 | rgb, 0x18FFFFFF);
    }

    private static int mix(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = from >> shift & 255, b = to >> shift & 255;
            result |= Math.round(a + (b - a) * amount) << shift;
        }
        return result;
    }
}
