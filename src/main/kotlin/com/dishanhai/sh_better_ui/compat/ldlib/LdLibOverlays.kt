package com.dishanhai.sh_better_ui.compat.ldlib

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.Widget
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Rect2i
import net.minecraft.network.chat.Component

/** 山海：LDLib 1.x 当前客户端容器的浮层入口。每个容器持有一个实例。 */
class LdLibOverlays(screen: ModularUIGuiContainer, preferences: UiPreferences) {
    private val owner = screen
    private val ui = screen.modularUI
    private val overlays = UiOverlayHost(screen, preferences)
    private var previousFocus: Widget? = null
    private var previousIngredientLookup = true

    init {
        overlays.setModalLifecycle(::suspendWidgets, ::restoreWidgets)
    }

    @JvmOverloads
    fun showNotice(title: Component, details: Component, icon: GraphicIcon? = GraphicIcon.INFO): Boolean = openModal {
        overlays.showNotice(title, details, icon)
    }

    @JvmOverloads
    fun showDialog(prompt: UiConfirmation.Prompt, onConfirm: Runnable, icon: GraphicIcon? = GraphicIcon.WARNING): Boolean = openModal {
        overlays.showDialog(prompt, onConfirm, icon)
    }

    @JvmOverloads
    fun showActions(title: Component, actions: List<UiOverlayHost.Action>, icon: GraphicIcon? = GraphicIcon.LIST): Boolean = openModal {
        overlays.showActions(title, actions, icon)
    }

    @JvmOverloads
    fun showBubble(anchor: Widget, message: Component, icon: GraphicIcon? = GraphicIcon.BUBBLE) {
        overlays.showBubble({ visibleBounds(anchor) }, message, icon)
    }

    fun hideBubble() = overlays.hideBubble()

    fun close() = overlays.close()

    fun isModalOpen() = overlays.isModalOpen()

    private inline fun openModal(open: () -> Unit): Boolean {
        if (Minecraft.getInstance().screen !== owner || owner.getQuickCrafting() || owner.draggingElement != null) {
            return false
        }
        open()
        return overlays.isModalOpen()
    }

    private fun visibleBounds(anchor: Widget): Rect2i? {
        if (anchor.gui !== ui || !anchor.isVisible) return null
        val box = anchor.toRectangleBox()
        var left = box.x
        var top = box.y
        var right = left + box.width
        var bottom = top + box.height
        var parent = anchor.parent
        while (parent != null) {
            if (!parent.isVisible) return null
            if (parent is DraggableScrollableWidgetGroup) {
                left = maxOf(left, parent.positionX)
                top = maxOf(top, parent.positionY)
                right = minOf(right, parent.positionX + parent.sizeWidth)
                bottom = minOf(bottom, parent.positionY + parent.sizeHeight)
            }
            parent = parent.parent
        }
        if (right <= left || bottom <= top) return null
        return Rect2i(left, top, right - left, bottom - top)
    }

    private fun suspendWidgets() {
        previousFocus = owner.lastFocus
        previousFocus?.setFocus(false)
        previousIngredientLookup = ui.mainGroup.isAllowXEIIngredientOverMouse
        ui.mainGroup.setAllowXEIIngredientOverMouse(false)
    }

    private fun restoreWidgets(restoreFocus: Boolean) {
        ui.mainGroup.setAllowXEIIngredientOverMouse(previousIngredientLookup)
        val focus = previousFocus
        previousFocus = null
        if (restoreFocus && Minecraft.getInstance().screen === owner && focus != null && focus.isActive &&
            focus.gui === ui && focus in ui.flatVisibleWidgetCollection
        ) {
            owner.focused = false
            focus.setFocus(true)
        }
    }
}
