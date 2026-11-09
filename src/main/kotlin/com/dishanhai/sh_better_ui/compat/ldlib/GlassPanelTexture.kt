package com.dishanhai.sh_better_ui.compat.ldlib

import com.dishanhai.sh_better_ui.client.glass.GlassPalette
import com.dishanhai.sh_better_ui.client.glass.GlassSurface
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import net.minecraft.client.gui.GuiGraphics

/**
 * 配方修改器的玻璃底图，替换原版 GuiTextures.BACKGROUND / DISPLAY / BUTTON。
 *
 * 原有深色材质常态不跑后处理：玻璃感来自半透明分层填色 + 左上受光亮边 + 根板外缘柔影。
 * 浅色面板调用 GlassSurface 的局部背景模糊；已在面板里的按钮使用 clearButtonOverlay 避免重复采样。
 * 每个实例独立持有 hover 弹簧，所以每个按钮要各自 `button()` 一份，不要共用。
 */
class GlassPanelTexture private constructor(private val style: Style) : IGuiTexture {

    enum class Style { ROOT, PANEL, DISPLAY, PAGE, BUTTON, CLEAR_PANEL, CLEAR_BUTTON, CLEAR_BUTTON_OVERLAY,
        SMOKE_PANEL, SMOKE_BUTTON_OVERLAY }

    private val hover = Spring(Spring.HOVER)

    override fun draw(graphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Float, y: Float, width: Int, height: Int) {
        if (width < 3 || height < 3) return
        val left = x.toInt()
        val top = y.toInt()
        when (style) {
            Style.ROOT -> {
                shadow(graphics, left, top, width, height)
                plate(graphics, left, top, width, height, GlassPalette.ROOT_EDGE, GlassPalette.ROOT_FILL, true)
            }
            Style.PANEL ->
                plate(graphics, left, top, width, height, GlassPalette.PANEL_EDGE, GlassPalette.PANEL_FILL, true)
            Style.DISPLAY ->
                plate(graphics, left, top, width, height, GlassPalette.DISPLAY_EDGE, GlassPalette.DISPLAY_FILL, false)
            Style.PAGE ->
                plate(graphics, left, top, width, height, GlassPalette.DISPLAY_EDGE, GlassPalette.PAGE_FILL, false)
            Style.BUTTON -> button(graphics, mouseX, mouseY, left, top, width, height)
            Style.CLEAR_PANEL -> GlassSurface.panel(graphics, left, top, width, height)
            Style.SMOKE_PANEL -> GlassSurface.smokePanel(graphics, left, top, width, height)
            Style.CLEAR_BUTTON, Style.CLEAR_BUTTON_OVERLAY, Style.SMOKE_BUTTON_OVERLAY -> {
                val now = PageTransition.nowMs()
                val over = mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
                hover.setTarget(if (over) 1f else 0f, now)
                hover.update(now)
                if (style == Style.SMOKE_BUTTON_OVERLAY) {
                    GlassSurface.smokeControl(graphics, left, top, width, height, hover.value())
                } else {
                    GlassSurface.control(graphics, left, top, width, height, hover.value(), style == Style.CLEAR_BUTTON)
                }
            }
        }
    }

    private fun button(g: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val now = PageTransition.nowMs()
        val over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
        hover.setTarget(if (over) 1f else 0f, now)
        hover.update(now)
        val t = hover.value().coerceIn(0f, 1f)
        plate(
            g, x, y, w, h,
            GuiMotion.lerpColor(GlassPalette.BUTTON_EDGE, GlassPalette.ACCENT, t),
            GuiMotion.lerpColor(GlassPalette.BUTTON_FILL, GlassPalette.BUTTON_FILL_HOVER, t),
            true,
        )
    }

    /** 四条边各自画，再画半透明内芯，这样内芯下面是世界而不是边框色。 */
    private fun plate(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int, edge: Int, fill: Int, lit: Boolean) {
        g.fill(x, y, x + w, y + 1, edge)
        g.fill(x, y + h - 1, x + w, y + h, edge)
        g.fill(x, y + 1, x + 1, y + h - 1, edge)
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, edge)
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill)
        if (lit && w > 4 && h > 4) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, GlassPalette.LIT)
            g.fill(x + 1, y + 2, x + 2, y + h - 1, GlassPalette.LIT_SOFT)
        }
    }

    /** 根板外缘三圈渐淡的下偏柔影。 */
    private fun shadow(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        for (i in 1..3) {
            val alpha = 0x30 shr (i - 1)
            g.fill(x - i, y - i + 2, x + w + i, y + h + i + 2, alpha shl 24)
        }
    }

    companion object {
        @JvmStatic
        fun root(): GlassPanelTexture = GlassPanelTexture(Style.ROOT)

        @JvmStatic
        fun panel(): GlassPanelTexture = GlassPanelTexture(Style.PANEL)

        @JvmStatic
        fun display(): GlassPanelTexture = GlassPanelTexture(Style.DISPLAY)

        @JvmStatic
        fun page(): GlassPanelTexture = GlassPanelTexture(Style.PAGE)

        @JvmStatic
        fun button(): GlassPanelTexture = GlassPanelTexture(Style.BUTTON)

        /** 中性浅色面板，也可用作根板或内容区。 */
        @JvmStatic
        fun clearPanel(): GlassPanelTexture = GlassPanelTexture(Style.CLEAR_PANEL)

        /** 中性浅色按钮，每个按钮各持一份 hover 弹簧。 */
        @JvmStatic
        fun clearButton(): GlassPanelTexture = GlassPanelTexture(Style.CLEAR_BUTTON)

        /** 已放在浅色毛玻璃面板内的按钮，省去每个按钮重复采样背景。 */
        @JvmStatic
        fun clearButtonOverlay(): GlassPanelTexture = GlassPanelTexture(Style.CLEAR_BUTTON_OVERLAY)

        /** 山海：低亮度中性烟灰根板，只在根板取样背景。 */
        @JvmStatic
        fun smokePanel(): GlassPanelTexture = GlassPanelTexture(Style.SMOKE_PANEL)

        /** 烟灰面板内的按钮，每个实例保留独立 hover 弹簧。 */
        @JvmStatic
        fun smokeButtonOverlay(): GlassPanelTexture = GlassPanelTexture(Style.SMOKE_BUTTON_OVERLAY)
    }
}
