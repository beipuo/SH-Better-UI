package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.client.api.UiFrame;

import java.util.function.Supplier;

/** 山海：一个示例的交互状态。在全屏切换和 GUI 重布局时保留同一实例。 */
@FunctionalInterface
public interface PreviewScene extends AutoCloseable {
    /** 按本次布局的坐标创建控件，保留本实例中需要跨重布局的状态。 */
    void layout(PreviewContext host);

    /** 使用宿主的帧时间和裁切范围，在原生控件之前绘制。 */
    default void render(UiFrame frame) {}

    /** 预览暂时没有可用的绘制区域时暂停交互或昂贵资源。 */
    default void suspend() {}

    /** 可选。离开示例后仅保留值，供在同一个图库中再次打开时恢复。 */
    default Supplier<? extends PreviewScene> saveForRemount() {
        return null;
    }

    @Override
    default void close() {
        suspend();
    }
}
