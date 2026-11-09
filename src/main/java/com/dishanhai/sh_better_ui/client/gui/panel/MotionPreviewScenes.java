package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRecipePageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntFunction;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.BUTTON_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.DEFAULT_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.addButton;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.centeredControl;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawBackdrop;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** 山海：使用图库传入的帧快照演示页面动效、动态状态和加载反馈。 */
final class MotionPreviewScenes {
    static final int HEIGHT = DEFAULT_HEIGHT + 20;
    private static final int NAV_WIDTH = 24;
    private static final int NAV_STEP = NAV_WIDTH + 3;

    private MotionPreviewScenes() {}

    private static Bounds content(Bounds preview) {
        return new Bounds(preview.x() + 6, preview.y() + 5, preview.width() - 12,
                preview.height() - BUTTON_HEIGHT - 15);
    }

    private static int controlsY(Bounds preview) {
        return preview.bottom() - BUTTON_HEIGHT - 5;
    }

    private abstract static class Slide implements PreviewScene {
        Font font;
        boolean alternate;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
            int x = host.viewport().x() + 6;
            int y = controlsY(host.viewport());
            addArrow(host, x, y, Direction.LEFT, GraphicIcon.PREVIOUS, "gallery.slide_left");
            addArrow(host, x + NAV_STEP, y, Direction.UP, GraphicIcon.COLLAPSE, "gallery.slide_up");
            addArrow(host, x + NAV_STEP * 2, y, Direction.DOWN, GraphicIcon.EXPAND, "gallery.slide_down");
            addArrow(host, x + NAV_STEP * 3, y, Direction.RIGHT, GraphicIcon.NEXT, "gallery.slide_right");
        }

        private void addArrow(PreviewContext host, int x, int y, Direction direction, GraphicIcon icon, String key) {
            var button = addButton(host, new Bounds(x, y, NAV_WIDTH, BUTTON_HEIGHT), label(key), null, () -> {
                alternate = !alternate;
                start(host, direction);
            });
            button.setIcon(icon);
        }

        abstract void start(PreviewContext host, Direction direction);

        void drawPageLabel(GuiGraphics graphics, Bounds viewport, boolean alternate) {
            drawCentered(graphics, font, label("gallery.slide_page", alternate ? 2 : 1),
                    viewport.x(), viewport.y() + (viewport.height() - BUTTON_HEIGHT) / 2, viewport.width());
        }
    }

    static final class PageSlide extends Slide {
        private static final UiPageSlide.Effects DEPTH_EFFECTS =
                new UiPageSlide.Effects(true, true, GlassStyle.LIQUID_REGULAR);
        private final UiPageSlide slide = new UiPageSlide();
        private UiPageSlide.Effects effects = UiPageSlide.Effects.NONE;
        private UiToggleButton mode;
        private boolean depth;

        @Override
        public void layout(PreviewContext host) {
            super.layout(host);
            var preview = host.viewport();
            int x = preview.x() + 6 + NAV_STEP * 4 + 3;
            mode = new UiToggleButton(new Bounds(x, controlsY(preview), preview.right() - x - 6, BUTTON_HEIGHT),
                    depth, host.preferences(), UiStyle.LIQUID_GLASS,
                    value -> label(value ? "gallery.slide_depth" : "gallery.slide_flat"),
                    value -> {
                        depth = value;
                        mode.setValue(value);
                    });
            mode.button().setPreview(label("gallery.slide_mode_tip"));
            host.add(mode.button());
        }

        @Override
        void start(PreviewContext host, Direction direction) {
            var viewport = content(host.viewport());
            int distance = switch (direction) {
                case LEFT, RIGHT -> viewport.width();
                case UP, DOWN -> viewport.height();
            };
            effects = depth ? DEPTH_EFFECTS : UiPageSlide.Effects.NONE;
            slide.start(direction, depth ? UiPageSlide.Motion.DEPTH : UiPageSlide.Motion.FLAT,
                    distance, PageTransition.nowMs(), host.preferences());
        }

        @Override
        public void render(UiFrame frame) {
            var viewport = content(frame.viewport());
            var graphics = frame.graphics();
            drawBackdrop(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height());
            var sample = slide.sample(frame.nowMs(), frame.preferences());
            UiPageSlide.draw(graphics, viewport, sample, frame.preferences(), effects, () -> {
                var pose = graphics.pose();
                pose.pushPose();
                try {
                    // 保持旋转后的卡片在预览底板前方。
                    double distance = Math.abs(Math.sin(Math.toRadians(sample.yaw()))) * viewport.width()
                            + Math.abs(Math.sin(Math.toRadians(sample.pitch()))) * viewport.height();
                    if (distance > 0) pose.translate(0f, 0f, (float) (distance / 2 + 1));
                    sample.applyDepth(pose, viewport.x() + viewport.width() / 2f,
                            viewport.y() + viewport.height() / 2f);
                    int color = alternate ? 0xAC2F4F67 : 0xAC493C63;
                    RoundedCorners.draw(graphics, viewport.x() + 2, viewport.y() + 2,
                            viewport.width() - 4, viewport.height() - 4, 4, 1, LiquidGlassTheme.accentColor(), color);
                    drawPageLabel(graphics, new Bounds(viewport.x() + 4, viewport.y(),
                            viewport.width() - 8, viewport.height()), alternate);
                } finally {
                    pose.popPose();
                }
            });
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            boolean savedDepth = depth;
            boolean savedPage = alternate;
            return () -> {
                var scene = new PageSlide();
                scene.depth = savedDepth;
                scene.alternate = savedPage;
                return scene;
            };
        }
    }

    static final class RecipeSlide extends Slide {
        private final UiRecipePageTransition slide = new UiRecipePageTransition();

        @Override
        void start(PreviewContext host, Direction direction) {
            slide.start(direction, recipeViewport(host.viewport()), PageTransition.nowMs(), host.preferences());
        }

        @Override
        public void render(UiFrame frame) {
            var viewport = recipeViewport(frame.viewport());
            var graphics = frame.graphics();
            var sample = slide.sample(frame.nowMs(), frame.preferences());
            UiRecipePageTransition.draw(graphics, viewport, sample, frame.preferences(),
                    () -> drawCard(graphics, viewport, !alternate),
                    () -> drawCard(graphics, viewport, alternate));
            var side = sample.companion();
            int sideX = viewport.right() + 6;
            int right = content(frame.viewport()).right();
            graphics.enableScissor(sideX, viewport.y(), right, viewport.bottom());
            try {
                var pose = graphics.pose();
                pose.pushPose();
                try {
                    pose.translate(0f, side.offsetY(), 0f);
                    drawPageLabel(graphics, new Bounds(sideX, viewport.y(), right - sideX, viewport.height()),
                            side.incoming() ? alternate : !alternate);
                } finally {
                    pose.popPose();
                }
                if (side.veilOpacity() > 0f) {
                    int color = Math.round(side.veilOpacity() * 255f) << 24 | (DarkModeTheme.backgroundColor() & 0xFFFFFF);
                    graphics.fill(sideX, viewport.y(), right, viewport.bottom(), 2, color);
                }
            } finally {
                graphics.disableScissor();
            }
        }

        private static Bounds recipeViewport(Bounds preview) {
            var viewport = content(preview);
            return new Bounds(viewport.x(), viewport.y(), viewport.width() * 3 / 4 - 6, viewport.height());
        }

        private void drawCard(GuiGraphics graphics, Bounds viewport, boolean alternate) {
            int color = alternate ? 0xFF503D73 : 0xFF315879;
            graphics.fillGradient(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), color, 0xFF212A3A);
            drawPageLabel(graphics, new Bounds(viewport.x() + 4, viewport.y(), viewport.width() - 8, viewport.height()),
                    alternate);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            boolean savedPage = alternate;
            return () -> {
                var scene = new RecipeSlide();
                scene.alternate = savedPage;
                return scene;
            };
        }
    }

    static final class DynamicStatus implements PreviewScene {
        private static final int BUTTON_WIDTH = 86;
        private static final Status[] STATUSES = Status.values();
        private Status status = Status.READY;
        private long startedAt;
        private Font font;

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            font = host.font();
            int y = preview.y() + (preview.height() - BUTTON_HEIGHT) / 2;
            addButton(host, new Bounds(preview.right() - BUTTON_WIDTH - 6, y, BUTTON_WIDTH, BUTTON_HEIGHT),
                    label("gallery.status_next"), null, () -> {
                        long now = PageTransition.nowMs();
                        status = STATUSES[(sample(now).status().ordinal() + 1) % STATUSES.length];
                        startedAt = now;
                    });
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var area = new Bounds(preview.x() + 6, preview.y() + 5,
                    preview.width() - BUTTON_WIDTH - 18, preview.height() - 10);
            UiDynamicStatus.draw(frame.graphics(), font, area, sample(frame.nowMs()), frame.preferences());
        }

        private UiDynamicStatus.Snapshot sample(long now) {
            if (status != Status.PENDING) return new UiDynamicStatus.Snapshot(status);
            int progress = (int) Math.clamp((now - startedAt) / 40L, 0L, 100L);
            return progress < 100 ? new UiDynamicStatus.Snapshot(status, progress)
                    : new UiDynamicStatus.Snapshot(Status.SUCCEEDED);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            Status savedStatus = status;
            long savedStart = startedAt;
            return () -> {
                var scene = new DynamicStatus();
                scene.status = savedStatus;
                scene.startedAt = savedStart;
                return scene;
            };
        }
    }

    private abstract static class LoadingProgress implements PreviewScene {
        private final IntFunction<Component> message;
        int percent;

        LoadingProgress(IntFunction<Component> message, int percent) {
            this.message = message;
            this.percent = percent;
        }

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            var area = new Bounds(preview.x() + 6, controlsY(preview), preview.width() - 12, BUTTON_HEIGHT);
            host.add(new UiSlider(area, 0, 100, 5, percent, host.preferences(), message, value -> percent = value));
        }
    }

    static final class Spinner extends LoadingProgress {
        Spinner() { this(45); }

        private Spinner(int percent) { super(value -> label("gallery.loading_organizing"), percent); }

        @Override
        public void render(UiFrame frame) {
            var area = content(frame.viewport());
            var graphics = frame.graphics();
            int half = area.width() / 2;
            UiLoading.spinner(graphics, new Bounds(area.x() + (half - 20) / 2,
                    area.y() + (area.height() - 20) / 2, 20, 20), percent / 100f, frame.preferences());
            var list = new Bounds(area.x() + half + 3, area.y(), area.width() - half - 3, area.height());
            graphics.fill(list.x(), list.y(), list.right(), list.bottom(), 0xFF252F3D);
            UiLoading.edgeSpinner(graphics, list, UiLoading.Edge.TOP, frame.nowMs(), frame.preferences());
            UiLoading.edgeSpinner(graphics, list, UiLoading.Edge.BOTTOM, frame.nowMs(), frame.preferences());
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = percent;
            return () -> new Spinner(saved);
        }
    }

    static final class Progress extends LoadingProgress {
        Progress() { this(45); }

        private Progress(int percent) { super(value -> label("gallery.slider_value", value), percent); }

        @Override
        public void render(UiFrame frame) {
            var area = content(frame.viewport());
            float progress = percent / 100f;
            UiLoading.topBar(frame.graphics(), area, progress, frame.preferences());
            var track = new Bounds(area.x() + 6, area.y() + (area.height() - 5) / 2, area.width() - 12, 5);
            UiLoading.progressBar(frame.graphics(), track, progress, frame.preferences());
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = percent;
            return () -> new Progress(saved);
        }
    }

    static final class Skeleton implements PreviewScene {
        private boolean loading = true;
        private Font font;

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            var button = centeredControl(preview);
            font = host.font();
            addButton(host, new Bounds(button.x(), controlsY(preview), button.width(), button.height()),
                    label("gallery.loading_toggle_card"), null, () -> loading = !loading);
        }

        @Override
        public void render(UiFrame frame) {
            var area = content(frame.viewport());
            if (loading) {
                UiSkeleton.card(frame.graphics(), area, frame.nowMs(), frame.preferences());
            } else {
                LiquidGlassTheme.popover(frame.graphics(), area.x(), area.y(), area.width(), area.height(), frame.preferences());
                drawCentered(frame.graphics(), font, label("gallery.loading_card_ready"), area.x() + 6,
                        area.y() + (area.height() - BUTTON_HEIGHT) / 2, area.width() - 12);
            }
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            boolean saved = loading;
            return () -> {
                var scene = new Skeleton();
                scene.loading = saved;
                return scene;
            };
        }
    }
}
