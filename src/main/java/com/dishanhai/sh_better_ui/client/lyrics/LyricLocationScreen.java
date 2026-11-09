// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics;

import com.dishanhai.sh_better_ui.client.lyrics.common.i18n.LangKeys;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricDragController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 歌词位置调整界面。
 *
 * <p>为什么必须开界面：只有 GUI 打开时游戏才释放光标，
 * 玩家才看得见并操作鼠标。HUD 状态下光标被锁定在窗口中心，
 * 「按住拖动」无从谈起。
 *
 * <p>语义是「先拖、后确认」：拖动只改屏幕上的位置，松手不落盘也不退出；
 * 点「保存」才写盘并关闭，点「取消」回到进入时的位置。
 * 这样一次调整可以分多次拖、反复微调，不满意还能整套撤销。
 *
 * <p>界面全透明，只画歌词与可拖区域的高亮；按下必须落在歌词上才开始拖动，
 * 因此在空白处按住不会误拖，也不会挡住下方的界面。
 */
final class LyricLocationScreen extends Screen {

    private final FloatingLyricRenderer renderer;
    private final LyricDragController controller;
    private final Screen parent;
    /** 本次按下是否落在歌词上，决定松手时是否结束拖动。 */
    private boolean pressedOnLyric;
    private boolean editing;
    private final LyricDemo demo = new LyricDemo();

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    LyricLocationScreen(FloatingLyricRenderer renderer,
                        LyricDragController controller, Screen parent) {
        super(Component.translatable("sh_better_ui.lyrics.location.title"));
        this.renderer = renderer;
        this.controller = controller;
        this.parent = parent;
    }

    @Override
    protected void init() {
        ownedWidgets.clear();
        if (!editing) {
            controller.beginSession();
            editing = true;
        }
        renderer.setPreviewForced(true);

        int centerX = width / 2;
        int y = height - 34;
        addOwned(Button.builder(
                        Component.translatable(LangKeys.SAVE),
                        b -> {
                            saveAndClose();
                        })
                .bounds(centerX - BUTTON_WIDTH - BUTTON_GAP / 2, y,
                        BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addOwned(Button.builder(
                        Component.translatable(LangKeys.CANCEL),
                        b -> {
                            onClose();
                        })
                .bounds(centerX + BUTTON_GAP / 2, y,
                        BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var prefs = UiPreferences.configured();
        LyricView.draw(renderer, graphics, font, null, prefs, demo.frame(prefs.reducedMotion()));

        // 提示放在顶部，避开被拖的歌词与底部按钮。
        graphics.drawCenteredString(font,
                Component.translatable("sh_better_ui.lyrics.location.hint"),
                width / 2, 12, 0xFFFFFFFF);
        if (controller.sessionDirty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("sh_better_ui.lyrics.location.dirty"), width / 2, 26, 0xFFFFD166);
        }

        // 显式顺序而不是 super.render：后者会先调 renderBackground，
        // 一旦将来有人给本界面加了背景，歌词就会被压在遮罩下。
        for (var widget : ownedWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让按钮处理：否则拖过歌词的手感会把按钮点击也吞掉。
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && pressedOnLyric) {
            controller.cancel();
            pressedOnLyric = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int x = (int) Math.round(mouseX);
            int y = (int) Math.round(mouseY);
            pressedOnLyric = renderer.bounds().contains(x, y) && controller.begin(x, y);
            // 按下落在歌词外：放行为 false，不启动拖动。
            return pressedOnLyric;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedOnLyric) {
            // 松手只停止跟随，不落盘、不关闭：位置留在屏幕上继续微调。
            controller.end();
            pressedOnLyric = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedOnLyric) {
            // 用当前光标而不是 dragX/dragY：后者是帧间增量，
            // 掉帧时会丢位移，累计位移的方式更稳。
            controller.drag((int) Math.round(mouseX), (int) Math.round(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (getFocused() == null) {
            int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                controller.nudge(dx, dy);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveAndClose() {
        controller.commit();
        editing = false;
        close();
    }

    /** 关闭界面并回到来源界面。 */
    private void close() {
        pressedOnLyric = false;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** 自管控件列表：显式控制绘制顺序，不交给 {@code super.render}。 */
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> ownedWidgets =
            new java.util.ArrayList<>();

    private void addOwned(net.minecraft.client.gui.components.AbstractWidget widget) {
        addWidget(widget);
        ownedWidgets.add(widget);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public void removed() {
        if (editing) controller.revert();
        editing = false;
        pressedOnLyric = false;
        renderer.setPreviewForced(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // 单人游戏下不要因为调位置而暂停世界，音乐还要继续放。
        return false;
    }
}
