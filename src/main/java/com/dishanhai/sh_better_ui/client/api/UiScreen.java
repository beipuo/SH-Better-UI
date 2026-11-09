package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 山海：通过回调组合现有 API 的原生界面，每次打开独立持有并释放上下文。 */
public final class UiScreen extends Screen {
    @FunctionalInterface
    public interface Draw {
        void draw(UiScreen screen, UiFrame frame);
    }

    private final UiScreenBuilder.Definition definition;
    private Screen parent;
    private boolean parentResolved;
    private GuiEventListener returnFocus;
    private UiApiContext context;
    private boolean removed;
    private boolean disposed;
    private boolean handlingFailure;
    private UiButton back;

    UiScreen(UiScreenBuilder.Definition definition) {
        super(definition.title());
        this.definition = definition;
        parent = definition.parent();
        parentResolved = definition.parentSpecified();
        returnFocus = parent == null ? null : parent.getFocused();
    }

    /** 同一次打开及重布局共用上下文。离开后可检查已关闭的上下文，返回本页会创建新上下文。 */
    public UiApiContext context() {
        if (context == null) {
            if (disposed) throw new IllegalStateException("Disposed UI screen");
            context = Objects.requireNonNull(definition.contexts().get());
        }
        return context;
    }

    /** GUI 逻辑坐标中的内容区域，默认扣除返回顶栏，窗口缩小时保持非负尺寸。 */
    public Bounds viewport() {
        int top = barHeight();
        return new Bounds(0, top, Math.max(0, width), Math.max(0, height - top));
    }

    public <T extends AbstractWidget> T add(T widget) {
        if (disposed) throw new IllegalStateException("Disposed UI screen");
        return addRenderableWidget(Objects.requireNonNull(widget));
    }

    public UiButton button(int x, int y, int width, int height, String translationKey, Runnable onPress) {
        return button(x, y, width, height, Component.translatable(translationKey), onPress);
    }

    public UiButton button(int x, int y, int width, int height, Component label, Runnable onPress) {
        Objects.requireNonNull(onPress);
        var api = context();
        return add(api.controls().button(new Bounds(x, y, width, height), Objects.requireNonNull(label),
                button -> runCallback(onPress), api.preferences(), false, UiStyle.DEFAULT));
    }

    /** 在客户端执行阶段取得默认父页，保持显式 parent(null) 的返回游戏含义。 */
    public void open() {
        if (disposed) throw new IllegalStateException("Disposed UI screen");
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (disposed || client.screen == this) return;
            if (!parentResolved) {
                parent = client.screen;
                parentResolved = true;
            }
            returnFocus = parent == null ? null : parent.getFocused();
            client.setScreen(this);
        });
    }

    @Override
    protected void init() {
        if (disposed) {
            onClose();
            return;
        }
        parentResolved = true;
        removed = false;
        try {
            if (context == null || context.isClosed()) {
                context = Objects.requireNonNull(definition.contexts().get());
            }
            back = null;
            if (definition.backButton()) {
                int inset = inset();
                var area = new Bounds(inset, inset, Math.min(100, Math.max(0, width - 2 * inset)),
                        Math.min(22, Math.max(0, height - 2 * inset)));
                back = addWidget(context.controls().button(area, Component.translatable("gui.back"),
                        button -> onClose(), context.preferences(), false, UiStyle.DEFAULT));
                back.setLeadingIcon(GraphicIcon.BACK);
                back.setSampleBackground(false);
                setInitialFocus(back);
            }
            definition.onInit().accept(this);
        } catch (RuntimeException | Error failure) {
            if (failure instanceof RuntimeException runtime && definition.onError() != null) {
                handleFailure(runtime);
                return;
            }
            try {
                removed();
            } catch (RuntimeException | Error cleanupFailure) {
                preserveCleanupFailure(failure, cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return definition.pausesGame();
    }

    @Override
    public void onClose() {
        if (minecraft == null || minecraft.screen != this) return;
        try {
            removed();
        } finally {
            var target = returnTarget(parent, returnFocus);
            if (minecraft.screen == this) minecraft.setScreen(target.screen());
            if (target.screen() != null && minecraft.screen == target.screen()) {
                try {
                    var focus = target.focus();
                    if (!disposed && target.screen() == parent && definition.returnFocus() != null) {
                        focus = definition.returnFocus().get();
                    }
                    if (focus != null && target.screen().children().contains(focus)) target.screen().setFocused(focus);
                } catch (RuntimeException failure) {
                    handleFailure(failure);
                }
            }
        }
    }

    @Override
    public void tick() {
        if (disposed || removed) return;
        super.tick();
        runCallback(() -> definition.onTick().accept(this));
    }

    /** 在客户端线程永久释放页面，用于脚本重载。活动页面会返回父页。 */
    public void dispose() {
        disposed = true;
        disposeMarked();
    }

    /** 在客户端线程先标记整批页面失效，再释放资源，返回时跳过同批旧页面。 */
    public static void disposeAll(Collection<? extends UiScreen> screens) {
        List<UiScreen> snapshot = List.copyOf(screens);
        for (var screen : snapshot) screen.disposed = true;
        Throwable failure = null;
        for (var screen : snapshot) {
            try {
                screen.disposeMarked();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private void disposeMarked() {
        try {
            removed();
        } finally {
            onClose();
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    record ReturnTarget(Screen screen, GuiEventListener focus) {}

    static ReturnTarget returnTarget(Screen parent, GuiEventListener returnFocus) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Screen, Boolean>());
        while (parent instanceof UiScreen screen && screen.disposed) {
            if (!visited.add(parent)) return new ReturnTarget(null, null);
            parent = screen.parent;
            returnFocus = screen.returnFocus;
        }
        return new ReturnTarget(parent, returnFocus);
    }

    @Override
    public void removed() {
        if (removed) return;
        removed = true;
        try {
            Throwable failure = null;
            try {
                if (context != null) definition.onRemoved().accept(this);
            } catch (RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                try {
                    if (context != null) context.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure == null) throw cleanupFailure;
                    preserveCleanupFailure(failure, cleanupFailure);
                } finally {
                    super.removed();
                }
            }
        } catch (RuntimeException failure) {
            handleFailure(failure);
        }
    }

    // NeoForge 的 Screen.render 会调用背景方法，背景由当前 render 统一绘制。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        if (disposed || removed) return Optional.empty();
        if (viewport().contains(mouseX, mouseY)) return super.getChildAt(mouseX, mouseY);
        if (back != null && back.isMouseOver(mouseX, mouseY)) return Optional.of(back);
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (disposed || removed) return false;
        if (viewport().contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (back == null || !back.mouseClicked(mouseX, mouseY, button)) return false;
        if (!disposed && !removed) {
            setFocused(back);
            if (button == 0) setDragging(true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (disposed || removed) return false;
        if (button == 0 && isDragging() && getFocused() != null) {
            setDragging(false);
            return getFocused().mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (disposed || removed) return;
        var frame = context().frame(graphics, viewport(), partialTick);
        if (definition.onRender() == null) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
        }
        var viewport = frame.viewport();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (definition.onRender() != null) runCallback(() -> definition.onRender().draw(this, frame));
            if (disposed || removed) return;
            boolean overContent = viewport.contains(mouseX, mouseY);
            super.render(graphics, overContent ? mouseX : -1, overContent ? mouseY : -1, partialTick);
            runCallback(() -> definition.onForeground().draw(this, frame));
        } finally {
            graphics.disableScissor();
        }
        if (disposed || removed) return;
        if (back != null) {
            graphics.fill(0, 0, width, barHeight(), DarkModeTheme.surfaceColor());
            back.render(graphics, mouseX, mouseY, partialTick);
            int titleX = back.getX() + back.getWidth() + inset();
            int availableWidth = width - inset() - titleX;
            if (availableWidth > 0 && barHeight() >= font.lineHeight) {
                var title = Language.getInstance().getVisualOrder(font.substrByWidth(getTitle(), availableWidth));
                UiFormattedText.draw(graphics, font, title, titleX,
                        (barHeight() - font.lineHeight) / 2, DarkModeTheme.textColor(), false, frame.preferences());
            }
        }
    }

    private int inset() {
        return Math.min(6, Math.max(0, Math.min(width, height)) / 8);
    }

    private int barHeight() {
        if (!definition.backButton()) return 0;
        return Math.min(22, Math.max(0, height - 2 * inset())) + 2 * inset();
    }

    private void runCallback(Runnable callback) {
        if (disposed || removed) return;
        try {
            callback.run();
        } catch (RuntimeException failure) {
            handleFailure(failure);
        }
    }

    private void handleFailure(RuntimeException failure) {
        if (definition.onError() == null || handlingFailure) throw failure;
        handlingFailure = true;
        try {
            try {
                dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                preserveCleanupFailure(failure, cleanupFailure);
            }
            definition.onError().accept(this, failure);
        } finally {
            handlingFailure = false;
        }
    }

    private static void preserveCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (failure == cleanupFailure) return;
        if (cleanupFailure instanceof Error error && !(failure instanceof Error)) {
            error.addSuppressed(failure);
            throw error;
        }
        failure.addSuppressed(cleanupFailure);
    }
}
