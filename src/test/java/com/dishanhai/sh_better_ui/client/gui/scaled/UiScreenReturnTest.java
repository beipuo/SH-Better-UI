package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：父页只准备一次、不递归绘制、不接收真实指针，返回世界时没有额外界面层。 */
class UiScreenReturnTest {
    @Test
    void preparesExistingParentOnceWithoutReinitializingItsDraft() {
        var parent = new Parent();
        var layer = new UiScreenReturn(new Parent());
        parent.width = 400;
        parent.height = 240;
        layer.prepare(parent, 400, 240);
        layer.prepare(parent, 400, 240);
        assertEquals(1, parent.preparations);
        assertEquals(0, parent.resizes);
        assertEquals(7, parent.draft);
        layer.renderParent(null, 0.5f);
        assertEquals(1, parent.renders);
        assertEquals(-1, parent.mouseX);
        assertEquals(-1, parent.mouseY);
    }

    @Test
    void changedWindowSizePreparesParentOnlyOnceAtTheNewSize() {
        var parent = new Parent();
        var layer = new UiScreenReturn(new Parent());
        layer.prepare(parent, 400, 240);
        layer.prepare(parent, 400, 240);
        layer.prepare(parent, 600, 320);
        layer.prepare(parent, 600, 320);
        assertEquals(2, parent.resizes);
        assertEquals(2, parent.preparations);
        assertEquals(7, parent.draft);
    }

    @Test
    void rejectsRecursiveParentRendering() {
        var parent = new Parent();
        var layer = new UiScreenReturn(new Parent());
        layer.prepare(parent, 400, 240);
        parent.duringRender = () -> layer.renderParent(null, 0f);
        layer.renderParent(null, 0f);
        assertEquals(1, parent.renders);
    }

    @Test
    void nullParentAndSelfParentDoNotRenderAnotherScreen() {
        var owner = new Parent();
        var layer = new UiScreenReturn(owner);
        layer.prepare(null, 400, 240);
        layer.renderParent(null, 0f);
        layer.prepare(owner, 400, 240);
        layer.renderParent(null, 0f);
        assertEquals(0, owner.renders);
        assertEquals(0, owner.preparations);
    }

    private static final class Parent extends Screen implements UiScreenReturn.Target {
        int preparations, resizes, renders, mouseX, mouseY;
        final int draft = 7;
        Runnable duringRender;

        Parent() { super(Component.empty()); }

        @Override public void prepareReturnLayer() { preparations++; }
        @Override public void resize(Minecraft minecraft, int width, int height) {
            this.width = width;
            this.height = height;
            resizes++;
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
            renders++;
            mouseX = x;
            mouseY = y;
            if (duringRender != null) duringRender.run();
        }
    }
}
