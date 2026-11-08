package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：概览长设备名完整换行，实时更新保留控件焦点和完整朗读文本。 */
class ModOverviewGroupTest {
    private static final Font FONT = new Font(ignored -> null, false) {
        private final StringSplitter splitter = new StringSplitter((codePoint, style) -> 1f);
        @Override
        public StringSplitter getSplitter() { return splitter; }
    };

    @Test
    void narrowInformationGroupsWrapLongNamesAndRetainTheCompleteNarration() {
        var value = new ModOverviewData.Group(Component.literal("设备信息"), List.of(
                Component.literal("GPU：非常长的显卡名称与驱动版本 abcdefghijklmnopqrstuvwxyz"),
                Component.literal("CPU：处理器")));
        var wide = new ModOverviewGroup(0, 0, 100, value, FONT, UiPreferences.DEFAULT);
        var narrow = new ModOverviewGroup(0, 0, 24, value, FONT, UiPreferences.DEFAULT);
        assertTrue(narrow.getHeight() > wide.getHeight());
        assertEquals("设备信息\nGPU：非常长的显卡名称与驱动版本 abcdefghijklmnopqrstuvwxyz\nCPU：处理器", narrow.getMessage().getString());
    }

    @Test
    void refreshingLiveInformationRetainsFocusAndRecalculatesItsHeight() {
        var widget = new ModOverviewGroup(10, 20, 30, new ModOverviewData.Group(Component.literal("运行状态"),
                List.of(Component.literal("相当长的一段信息 abcdefghijklmnopqrstuvwxyz"))), FONT, UiPreferences.DEFAULT);
        widget.setFocused(true);
        int height = widget.getHeight();
        widget.update(new ModOverviewData.Group(Component.literal("运行状态"), List.of(Component.literal("60 FPS"))));
        assertTrue(widget.isFocused());
        assertTrue(widget.getHeight() < height);
        assertEquals(10, widget.getX());
        assertEquals(20, widget.getY());
        assertTrue(widget.getMessage().getString().endsWith("60 FPS"));
    }
}
