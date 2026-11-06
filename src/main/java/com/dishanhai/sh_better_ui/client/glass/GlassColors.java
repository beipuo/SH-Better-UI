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

    public static final GlassColors CLEAR = new GlassColors(
            0xFF276B8B, 0xFF1C2B35, 0xFF194A61, 0xFF3B5363,
            0xFF4D6471, 0xFF75818A, 0xFF276C50,
            0xA6FFFFFF, 0x66F4F8FF, 0x99F4F8FF,
            0xA6FFFFFF, 0x4DF4F8FF, 0xCCF4F8FF,
            0x8098ACB8, 0xB3F4F8FF, 0xDCF4F8FF,
            0xA6FFFFFF, 0xCCF4F8FF, 0x99FFFFFF);

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
}
