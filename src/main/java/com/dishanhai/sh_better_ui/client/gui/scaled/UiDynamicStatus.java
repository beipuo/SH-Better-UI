package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplay;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 山海：每帧读取接入方的实时状态并绘制图文。状态来源在客户端线程读取。 */
public final class UiDynamicStatus {
    public static final int UNKNOWN_PROGRESS = -1;
    private static final GraphicDisplayLayout.Grid SINGLE_COLUMN = new GraphicDisplayLayout.Grid(1, 1);

    /** 进度以整数百分比表示。传 UNKNOWN_PROGRESS 仅展示状态文字。 */
    public record Snapshot(Status status, Component label, int progressPercent) {
        public Snapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(label, "label");
            if (progressPercent < UNKNOWN_PROGRESS || progressPercent > 100) {
                throw new IllegalArgumentException("Progress must be -1 or a percentage in [0, 100]");
            }
        }

        public Snapshot(Status status) {
            this(status, UNKNOWN_PROGRESS);
        }

        public Snapshot(Status status, Component label) {
            this(status, label, UNKNOWN_PROGRESS);
        }

        public Snapshot(Status status, int progressPercent) {
            this(status, Component.translatable(status.translationKey()), progressPercent);
        }

        /** 供显示或旁白使用的完整本地化文字。 */
        public Component text() {
            return progressPercent == UNKNOWN_PROGRESS ? label
                    : Component.translatable("sh_better_ui.status.progress", label, progressPercent);
        }

        /** 宿主可用已显示的快照提供旁白，避免再次读取状态来源。 */
        public void addNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, text());
        }
    }

    private final Supplier<Snapshot> source;

    public UiDynamicStatus(Supplier<Snapshot> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public Snapshot sample() {
        return Objects.requireNonNull(source.get(), "status snapshot");
    }

    /** 只读取一次状态来源。返回的快照可用于工具提示及旁白。 */
    public Snapshot render(GuiGraphics graphics, Font font, Bounds area, UiPreferences preferences) {
        Snapshot snapshot = sample();
        draw(graphics, font, area, snapshot, preferences);
        return snapshot;
    }

    /** 以独立的一行图文显示快照。区域宽高至少为 24 个 GUI 单位时可显示完整图标。 */
    public static void draw(GuiGraphics graphics, Font font, Bounds area, Snapshot snapshot,
                            UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0) return;
        var layout = GraphicDisplayLayout.arrange(area, 1, area.height(), false, SINGLE_COLUMN);
        int tint = preferences.highContrast() ? 0xFFFFFFFF : iconColor(snapshot.status());
        var entry = new GraphicDisplay.Entry(icon(snapshot.status()), snapshot.text(), tint);
        GraphicDisplay.drawRows(graphics, font, layout, List.of(entry), 1, LiquidGlassTheme.textColor(), 0, preferences);
    }

    /** 等待图标沿用库的旋转动效，减少动效时使用静止图标。 */
    public static GraphicIcon icon(Status status) {
        return switch (status) {
            case READY -> GraphicIcon.STATUS_READY;
            case PENDING -> GraphicIcon.STATUS_PENDING;
            case SUCCEEDED -> GraphicIcon.STATUS_SUCCEEDED;
            case PARTIAL -> GraphicIcon.STATUS_PARTIAL;
            case FAILED -> GraphicIcon.STATUS_FAILED;
            case CANCELLED -> GraphicIcon.STATUS_CANCELLED;
            case TIMED_OUT -> GraphicIcon.STATUS_TIMED_OUT;
        };
    }

    private static int iconColor(Status status) {
        return switch (status) {
            case READY, CANCELLED -> LiquidGlassTheme.mutedTextColor();
            case PENDING -> LiquidGlassTheme.accentColor();
            case SUCCEEDED -> DarkModeTheme.SUCCESS;
            case PARTIAL, TIMED_OUT -> 0xFFFBBF24;
            case FAILED -> 0xFFF87171;
        };
    }
}
