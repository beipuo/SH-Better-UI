package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** 山海：在绘制前修饰单元。多个效果按传入顺序执行，有状态的效果应由一个区域独占。 */
@FunctionalInterface
public interface CodeRainEffect {
    void apply(Frame frame, Cell cell);

    /** 是否需要用宿主鼠标快照生成区域内的指针事件。 */
    default boolean usesPointer() { return false; }

    /** 每个可绘制帧调用一次，可在此准备所有单元共用的数据。 */
    default void beginFrame(Frame frame) {}

    /** 区域隐藏、重置或改变尺寸时调用，用于清除瞬时状态。 */
    default void suspend() {}

    /** seconds 为有效动画秒数。指针不可用时 pointer 为 null，否则坐标以区域左上角为原点。 */
    record Frame(Bounds bounds, CodeRainProfile profile, int glyphCount, double seconds,
                 boolean reducedMotion, UiPointerTracker.Frame pointer) {}

    /** 渲染器反复复用此对象。效果只在 apply 调用期间读取或修改它。 */
    final class Cell {
        int column;
        int row;
        float x;
        float y;
        int glyphIndex;
        float brightness;
        int argb;
        float glow;
        float dotSize;

        Cell() {}

        public int column() { return column; }
        public int row() { return row; }
        public float x() { return x; }
        public float y() { return y; }
        public int glyphIndex() { return glyphIndex; }
        public float brightness() { return brightness; }
        public int argb() { return argb; }
        public float glow() { return glow; }
        public float dotSize() { return dotSize; }

        public void setGlyphIndex(int value) { glyphIndex = value; }
        public void setBrightness(float value) { brightness = Float.isFinite(value) ? GuiMotion.clamp01(value) : 0; }
        public void setArgb(int value) { argb = value; }
        public void setGlow(float value) { glow = Float.isFinite(value) ? GuiMotion.clamp01(value) : 0; }
        /** 以单元边长的比例显示方点。零表示使用字形。 */
        public void setDotSize(float value) { dotSize = Float.isFinite(value) ? GuiMotion.clamp01(value) : 0; }
    }
}
