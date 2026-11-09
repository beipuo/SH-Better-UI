package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.client.api.UiApiContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** 山海：预览场景共享的绘制、原生输入和资源作用域，区域采用绝对 GUI 坐标。 */
public interface PreviewContext {
    Screen screen();

    Bounds viewport();

    Bounds toolbar();

    Font font();

    UiPreferences preferences();

    UiOverlayHost overlays();

    UiApiContext context();

    boolean interactive();

    <T extends AbstractWidget> T add(T widget);

    /** 注册绘制在预览裁切范围之外的控件，例如浮动通知。 */
    <T extends AbstractWidget> T addOverlay(T widget);
}
