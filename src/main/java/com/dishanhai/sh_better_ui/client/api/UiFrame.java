package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：一次绘制共用的图形、GUI 区域、鼠标、偏好和时钟快照。 */
public record UiFrame(GuiGraphics graphics, Bounds viewport, UiMouseState mouse,
                      UiPreferences preferences, long nowMs, float partialTick) {}
