package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.BiConsumer;

/** 山海：每个底部抽屉独享的拖动、橡皮筋阻尼及吸附控制器。仅在客户端线程调用。 */
public final class UiBottomSheet {
    private static final int PRIMARY_BUTTON = 0;
    private static final int HANDLE_WIDTH = 36;
    private static final int HANDLE_HEIGHT = 12;
    private static final float RUBBER_BAND_LIMIT = 24f;
    private static final double PROJECTION_SECONDS = 0.16;
    private static final long VELOCITY_WINDOW_MS = 100L;
    private static final double MAX_POINTER_SPEED = 2400.0;
    private static final Spring.Preset SETTLE = new Spring.Preset(0.24, 0.74, 0.00025f, 0.004f);
    private static final Spring.Preset CAPSULE_SETTLE = new Spring.Preset(0.36, 0.88,
            SETTLE.settleEps(), SETTLE.settleVelocity());

    /**
     * 绘制和命中共用的绝对 GUI 坐标。抽屉上沿可暂时移出视口，正文区域已裁切。
     * {@code targetAnchor} 在拖动时表示当前松手会落到的档位，否则表示已选定的档位。
     */
    public record Frame(Bounds viewport, Bounds panel, Bounds handle, Bounds content,
                        int targetAnchor, boolean dragging, boolean settling) {}

    private final float[] anchors;
    private final boolean capsuleMorph;
    private final PointerDrag pointer = new PointerDrag();
    // 弹簧和手势共用未压缩的位置，重新抓住回弹中的把手时可沿同一轨迹接续。
    private final Spring height;
    private int targetAnchor;
    private Frame sampledFrame;
    private float sampledHeight;
    private boolean reducedMotion;
    private int dragViewportHeight;
    private long lastMotionMs;
    private double lastMotionY;
    private double velocity;

    /**
     * 吸附高度使用从底边向上露出的视口比例，按严格递增的 0..1 提供，0 表示完全隐藏。
     * {@code initialAnchor} 是从 0 开始的数组索引。至少提供一个档位。
     */
    public UiBottomSheet(int initialAnchor, float... anchorHeights) {
        this(initialAnchor, false, anchorHeights);
    }

    /** 从底部小胶囊展开的抽屉。拖动、渲染和命中使用同一套几何。 */
    public static UiBottomSheet capsule(int initialAnchor, float... anchorHeights) {
        return new UiBottomSheet(initialAnchor, true, anchorHeights);
    }

    private UiBottomSheet(int initialAnchor, boolean capsuleMorph, float... anchorHeights) {
        this.anchors = anchorHeights.clone();
        if (anchors.length == 0 || initialAnchor < 0 || initialAnchor >= anchors.length) {
            throw new IllegalArgumentException("Invalid initial anchor index");
        }
        float previous = -1f;
        for (float anchor : anchors) {
            if (!Float.isFinite(anchor) || anchor < 0f || anchor > 1f || anchor <= previous) {
                throw new IllegalArgumentException("Anchor heights must increase within [0, 1]");
            }
            previous = anchor;
        }
        this.capsuleMorph = capsuleMorph;
        this.targetAnchor = initialAnchor;
        this.height = new Spring(capsuleMorph ? CAPSULE_SETTLE : SETTLE, anchors[initialAnchor]);
    }

    public int anchorCount() { return anchors.length; }

    /** 当前已选定的档位。拖动中的落点预览由 {@link Frame#targetAnchor()} 提供。 */
    public int targetAnchor() { return targetAnchor; }

    /** 命中最近一次绘制的把手且子控件未接收按下时调用。事件和取样共用单调毫秒时钟。 */
    public boolean begin(Frame frame, int button, double x, double y, long nowMs) {
        if (!canBegin(frame, button, x, y)) return false;
        beginDrag(frame, button, x, y, nowMs);
        return true;
    }

    /** 从原生 Screen 的 mouseClicked 接入捕获，放开、Esc、窗口失焦和画面移除由库分派。 */
    public boolean beginCaptured(Screen owner, Frame frame, int button, double x, double y) {
        if (!canBegin(frame, button, x, y) || !UiPointerCapture.start(owner, button, new UiPointerCapture.Handler() {
            public void drag(double x, double y) {
                UiBottomSheet.this.drag(button, x, y, PageTransition.nowMs());
            }

            public void release(double x, double y) {
                UiBottomSheet.this.release(button, x, y, PageTransition.nowMs());
            }

            public void cancel() {
                cancelGesture(PageTransition.nowMs());
            }
        })) return false;
        beginDrag(frame, button, x, y, PageTransition.nowMs());
        return true;
    }

    /** 手动分派已捕获的拖动，离开把手后也继续跟随。 */
    public boolean drag(int button, double x, double y, long nowMs) {
        if (button != PRIMARY_BUTTON || !pointer.active()) return false;
        PointerDrag.Delta delta = pointer.move(button, x, y);
        if (delta != null) follow(delta, y, nowMs);
        return true;
    }

    /** 纳入放开位置，按近期纵向速度选档，并把该速度交给回弹弹簧。 */
    public boolean release(int button, double x, double y, long nowMs) {
        if (button != PRIMARY_BUTTON || !pointer.active()) return false;
        PointerDrag.Delta delta = pointer.release(button, x, y);
        if (delta == null) {
            height.setTarget(anchors[targetAnchor], nowMs);
        } else {
            follow(delta, y, nowMs);
            double releaseVelocity = recentVelocity(nowMs) / dragViewportHeight;
            targetAnchor = projectedAnchor(releaseVelocity);
            height.setTarget(anchors[targetAnchor], (float) releaseVelocity, nowMs);
        }
        return true;
    }

    /** 取消拖动，回到按下前选定的档位。手动分派时应接入失焦、关闭和取消。 */
    public void cancelGesture(long nowMs) {
        if (!pointer.active()) return;
        pointer.cancel();
        height.setTarget(anchors[targetAnchor], nowMs);
    }

    /** 按钮、键盘与业务代码使用同一档位入口。移动途中换档会保留当前位置和速度。 */
    public void animateTo(int anchor, long nowMs) {
        float destination = anchors[anchor];
        pointer.cancel();
        targetAnchor = anchor;
        height.setTarget(destination, nowMs);
    }

    /** 立即重置位置及指针状态，适用于画面初始化。 */
    public void snapTo(int anchor) {
        float destination = anchors[anchor];
        pointer.cancel();
        targetAnchor = anchor;
        height.snap(destination);
        sampledFrame = null;
    }

    /** 每帧按当前视口取样。减少动效时仍可直接拖动，放开即到达选定档位。 */
    public Frame sample(Bounds viewport, long nowMs, UiPreferences preferences) {
        if (pointer.active() && viewport.height() != dragViewportHeight) cancelGesture(nowMs);
        boolean dragging = pointer.active();
        reducedMotion = preferences.reducedMotion();
        if (!dragging) {
            height.update(nowMs);
            float destination = anchors[targetAnchor];
            if (reducedMotion || (destination == 0f && height.value() <= 0f)) height.snap(destination);
        } else if (reducedMotion) {
            height.snap(UiMath.clamp(height.value(), anchors[0], anchors[anchors.length - 1]));
        }

        double visibleHeight = visibleHeight(viewport.height());
        int pixels = Math.max(0, Math.round((float) visibleHeight));
        float spread = capsuleProgress(viewport, pixels);
        int startWidth = Math.min(FluidMorph.SEED_WIDTH, viewport.width());
        int panelWidth = Math.round(GuiMotion.lerp(startWidth, viewport.width(), spread));
        Bounds panel = new Bounds(viewport.x() + (viewport.width() - panelWidth) / 2,
                viewport.bottom() - pixels, panelWidth, pixels);
        int handleWidth = Math.min(HANDLE_WIDTH, panel.width());
        int handleHeight = Math.min(HANDLE_HEIGHT, pixels);
        int handleTop = panel.y() + Math.min(3, pixels - handleHeight);
        Bounds handle = new Bounds(panel.x() + (panel.width() - handleWidth) / 2, handleTop,
                handleWidth, handleHeight);
        int contentTop = UiMath.clamp(handle.bottom() + 2, viewport.y(), viewport.bottom());
        Bounds content = new Bounds(panel.x(), contentTop, panel.width(), panel.bottom() - contentTop);
        int preview = dragging ? projectedAnchor(recentVelocity(nowMs) / dragViewportHeight) : targetAnchor;
        sampledHeight = height.value();
        sampledFrame = new Frame(viewport, panel, handle, content, preview, dragging, !height.settled());
        return sampledFrame;
    }

    /** 绘制底板、把手及吸附预览。正文按回调收到的区域绘制，并以同一区域做输入命中。 */
    public Frame render(GuiGraphics graphics, Bounds viewport, int mouseX, int mouseY, long nowMs,
                        UiPreferences preferences, BiConsumer<GuiGraphics, Bounds> renderContent) {
        Frame frame = sample(viewport, nowMs, preferences);
        if (viewport.width() == 0 || viewport.height() == 0) return frame;
        Bounds panel = frame.panel();
        Bounds handle = frame.handle();
        Bounds content = frame.content();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (panel.height() > 0) {
                float spread = capsuleProgress(viewport, panel.height());
                if (spread < 1f) {
                    float radius = GuiMotion.lerp(Math.min(panel.width(), panel.height()) * 0.5f, 6f, spread);
                    RoundedCorners.draw(graphics, panel.x(), panel.y(), panel.width(), panel.height(), radius, 1f,
                            preferences.highContrast() ? DarkModeTheme.textColor()
                                    : GuiMotion.lerpColor(LiquidGlassTheme.accentColor(), DarkModeTheme.borderColor(), spread),
                            preferences.highContrast() ? DarkModeTheme.backgroundColor() : UiTheme.current().surface(0xF21E293B));
                } else {
                    DarkModeTheme.modalPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                            preferences.highContrast());
                }
                float hover = handle.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY) ? 1f : 0f;
                var state = new DarkModeTheme.State(hover, hover, frame.dragging() ? 1f : 0f, false, true,
                        preferences.reducedMotion(), preferences.highContrast());
                DarkModeTheme.dragHandle(graphics, handle.x(), handle.y(), handle.width(), handle.height(), state);
                if (content.height() > 0) {
                    graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
                    try {
                        renderContent.accept(graphics, content);
                    } finally {
                        graphics.disableScissor();
                    }
                }
            }
            if (frame.dragging()) {
                int snapY = viewport.bottom() - Math.round(viewport.height() * anchors[frame.targetAnchor()]);
                if (snapY != panel.y()) {
                    int y = Math.min(snapY, viewport.bottom() - 1);
                    graphics.fill(handle.x(), y, handle.right(), y + 1,
                            preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.accentColor());
                }
            }
        } finally {
            graphics.disableScissor();
        }
        return frame;
    }

    private float capsuleProgress(Bounds viewport, int pixels) {
        if (!capsuleMorph || reducedMotion) return 1f;
        float unfoldHeight = Math.min(56f, Math.max(1f, viewport.height() * anchors[anchors.length - 1]));
        float progress = GuiMotion.clamp01(pixels / unfoldHeight);
        return progress * progress * (3f - 2f * progress);
    }

    private boolean canBegin(Frame frame, int button, double x, double y) {
        return button == PRIMARY_BUTTON && !pointer.active() && frame != null && frame == sampledFrame
                && frame.viewport().contains(x, y) && frame.handle().contains(x, y);
    }

    private void beginDrag(Frame frame, int button, double x, double y, long nowMs) {
        pointer.begin(button, x, y);
        height.snap(sampledHeight);
        dragViewportHeight = frame.viewport().height();
        lastMotionY = y;
        lastMotionMs = nowMs;
        velocity = 0;
    }

    private void follow(PointerDrag.Delta delta, double y, long nowMs) {
        if (delta.y() != 0.0) {
            double allowance = reducedMotion ? 0.0 : 6.0 * RUBBER_BAND_LIMIT / dragViewportHeight;
            double current = height.value();
            double lower = Math.min(current, anchors[0] - allowance);
            double upper = Math.max(current, anchors[anchors.length - 1] + allowance);
            height.snap((float) UiMath.clamp(current - delta.y() / dragViewportHeight, lower, upper));
        }
        long elapsed = nowMs - lastMotionMs;
        double displacement = lastMotionY - y;
        if (elapsed <= 0 || displacement == 0.0) return;
        if (elapsed >= VELOCITY_WINDOW_MS) {
            velocity = 0;
        } else {
            double instant = UiMath.clamp(displacement * 1000.0 / elapsed,
                    -MAX_POINTER_SPEED, MAX_POINTER_SPEED);
            // 快速反向时采用新趋势，同时过滤小于 2 GUI 像素的单次抖动。
            if (velocity == 0 || (instant * velocity < 0 && Math.abs(displacement) >= 2.0)) {
                velocity = instant;
            } else {
                velocity += (instant - velocity) * (1.0 - Math.exp(-elapsed / 24.0));
            }
        }
        lastMotionY = y;
        lastMotionMs = nowMs;
    }

    private double recentVelocity(long nowMs) {
        long idle = UiMath.clamp(nowMs - lastMotionMs, 0L, VELOCITY_WINDOW_MS);
        return velocity * (1.0 - (double) idle / VELOCITY_WINDOW_MS);
    }

    private int projectedAnchor(double velocity) {
        double position = UiMath.clamp(height.value(), anchors[0], anchors[anchors.length - 1]);
        double projected = position + velocity * PROJECTION_SECONDS;
        int closest = 0;
        double best = Math.abs(anchors[0] - projected);
        for (int i = 1; i < anchors.length; i++) {
            double distance = Math.abs(anchors[i] - projected);
            if (distance < best || (distance == best && (velocity > 0 || (velocity == 0 && i == targetAnchor)))) {
                closest = i;
                best = distance;
            }
        }
        return closest;
    }

    private double visibleHeight(int viewportHeight) {
        double raw = (double) height.value() * viewportHeight;
        double boundary = UiMath.clamp(raw, (double) anchors[0] * viewportHeight,
                (double) anchors[anchors.length - 1] * viewportHeight);
        double excess = raw - boundary;
        return boundary + RUBBER_BAND_LIMIT * excess / (RUBBER_BAND_LIMIT + Math.abs(excess));
    }
}
