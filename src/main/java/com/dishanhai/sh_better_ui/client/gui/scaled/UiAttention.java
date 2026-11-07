package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;

/** 山海：每组页签独享的注意力切换，指示条可连续改目标，新内容短距淡入。 */
public final class UiAttention {
    public static final float CONTENT_OFFSET = 6f;

    private final InterruptibleAnimation x = new InterruptibleAnimation(Spring.INDICATOR);
    private final InterruptibleAnimation y = new InterruptibleAnimation(Spring.INDICATOR);
    private final InterruptibleAnimation width = new InterruptibleAnimation(Spring.INDICATOR);
    private final InterruptibleAnimation height = new InterruptibleAnimation(Spring.INDICATOR);
    private final InterruptibleAnimation reveal = new InterruptibleAnimation(Spring.STAGGER, 1f);
    private Object selectedKey;
    private boolean visible;

    /** key 使用稳定、可比较的内容键。相同键只更新布局，新的键触发内容进入。 */
    public void select(Object key, float left, float top, float controlWidth, float controlHeight, long nowMs) {
        Objects.requireNonNull(key);
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(controlWidth)
                || !Float.isFinite(controlHeight) || controlWidth <= 0 || controlHeight <= 0) return;
        if (!visible) {
            x.snapTo(left);
            y.snapTo(top);
            width.snapTo(controlWidth);
            height.snapTo(controlHeight);
        } else {
            x.animateTo(left, nowMs);
            y.animateTo(top, nowMs);
            width.animateTo(controlWidth, nowMs);
            height.animateTo(controlHeight, nowMs);
            if (!Objects.equals(selectedKey, key)) {
                reveal.snapTo(0f);
                reveal.animateTo(1f, nowMs);
            }
        }
        selectedKey = key;
        visible = true;
    }

    /** 在绘制及命中时共用这一帧。事件与绘制使用 PageTransition.nowMs() 的单调时钟。 */
    public Frame sample(long nowMs, UiPreferences preferences) {
        float progress = GuiMotion.clamp01(reveal.sample(nowMs, preferences));
        return new Frame(visible, x.sample(nowMs, preferences), y.sample(nowMs, preferences),
                Math.max(0f, width.sample(nowMs, preferences)), Math.max(0f, height.sample(nowMs, preferences)),
                progress, CONTENT_OFFSET * (1f - progress), 1f - progress);
    }

    /** 隐藏或移除整个分组时清理；下次显示从新位置直接建立选择。 */
    public void clear() {
        selectedKey = null;
        visible = false;
        x.snapTo(0f);
        y.snapTo(0f);
        width.snapTo(0f);
        height.snapTo(0f);
        reveal.snapTo(1f);
    }

    public record Frame(boolean visible, float x, float y, float width, float height,
                        float contentAlpha, float contentOffsetY, float emphasis) {
        /** 内容做纵向平移后，鼠标换回原内容坐标。指示条与页签本身不做内容平移。 */
        public double contentMouseY(double mouseY) { return mouseY - contentOffsetY; }
    }
}
