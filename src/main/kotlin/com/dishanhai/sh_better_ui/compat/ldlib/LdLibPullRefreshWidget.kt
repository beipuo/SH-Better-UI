package com.dishanhai.sh_better_ui.compat.ldlib

import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.utils.Position
import com.lowdragmc.lowdraglib.utils.Size
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.Rect2i
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** 山海：固定在滚动区域外的客户端下拉把手。每个区域持有自己的刷新控制器。 */
class LdLibPullRefreshWidget @JvmOverloads constructor(
    x: Int, y: Int, width: Int, height: Int,
    private val controller: UiPullRefresh,
    private val preferences: UiPreferences,
    private val atTop: BooleanSupplier,
    private val releaseBounds: Supplier<Rect2i>,
    private val keyboardShortcut: Boolean = true,
) : Widget(Position(x, y), Size(width, height)) {
    private var captured = false
    private var shortcutHeld = false

    init {
        setClientSideWidget()
        setHoverTooltips(Component.translatable(if (keyboardShortcut) {
            "sh_better_ui.pull_refresh.widget_tooltip"
        } else {
            "sh_better_ui.pull_refresh.tooltip"
        }))
    }

    override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!canInteract()) cancelDrag()
        controller.render(graphics, Minecraft.getInstance().font, toRectangleBox(), PageTransition.nowMs(), preferences)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val screen = gui?.modularUIGui ?: return false
        if (!canInteract() || !isMouseOverElement(mouseX, mouseY)) return false
        if (!controller.begin(button, mouseX, mouseY, atTop.asBoolean, PageTransition.nowMs())) return false
        captured = UiPointerCapture.start(screen, button, object : UiPointerCapture.Handler {
            override fun drag(x: Double, y: Double) {
                if (canInteract() && atTop.asBoolean) {
                    controller.drag(button, x, y, insideReleaseBounds(x, y), PageTransition.nowMs())
                } else cancelDrag()
            }

            override fun release(x: Double, y: Double) {
                captured = false
                val allowed = canInteract() && atTop.asBoolean && insideReleaseBounds(x, y)
                controller.release(button, x, y, allowed, PageTransition.nowMs())
            }

            override fun cancel() {
                captured = false
                controller.cancelGesture(PageTransition.nowMs())
            }
        })
        if (!captured) controller.cancelGesture(PageTransition.nowMs())
        return captured
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode != GLFW.GLFW_KEY_F5 || modifiers != 0 || !keyboardShortcut || !canInteract()) return false
        val focus = gui?.modularUIGui?.lastFocus
        if (focus is TextFieldWidget && focus.isFocus) return false
        if (!shortcutHeld) {
            shortcutHeld = true
            requestRefresh()
        }
        return true
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode != GLFW.GLFW_KEY_F5 || !shortcutHeld) return false
        shortcutHeld = false
        return true
    }

    fun requestRefresh(): Boolean = canInteract() && controller.requestRefresh(PageTransition.nowMs())

    override fun setGui(gui: ModularUI?) {
        if (getGui() != null && getGui() !== gui) {
            cancelDrag()
            controller.reset(PageTransition.nowMs())
        }
        super.setGui(gui)
    }

    override fun setVisible(visible: Boolean): Widget {
        if (!visible) cancelDrag()
        return super.setVisible(visible)
    }

    override fun setActive(active: Boolean): Widget {
        if (!active) cancelDrag()
        return super.setActive(active)
    }

    private fun canInteract(): Boolean {
        val client = Minecraft.getInstance()
        val screen = gui?.modularUIGui ?: return false
        if (client.screen !== screen || !client.isWindowActive || screen.getQuickCrafting() ||
            screen.draggingElement != null || UiOverlayHost.current(screen)?.isModalOpen() == true
        ) return false
        var widget: Widget? = this
        while (widget != null) {
            if (!widget.isActive || !widget.isVisible) return false
            widget = widget.parent
        }
        return true
    }

    private fun cancelDrag() {
        shortcutHeld = false
        if (captured) UiPointerCapture.cancel(gui?.modularUIGui)
    }

    private fun insideReleaseBounds(x: Double, y: Double): Boolean {
        val bounds = releaseBounds.get() ?: return false
        return x >= bounds.x && x < bounds.x + bounds.width && y >= bounds.y && y < bounds.y + bounds.height
    }
}
