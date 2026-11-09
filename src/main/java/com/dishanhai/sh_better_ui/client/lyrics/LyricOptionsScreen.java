package com.dishanhai.sh_better_ui.client.lyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 山海：为 Forge 1.20.1 的原版滚动列表提供歌词设置控件行。 */
abstract class LyricOptionsScreen extends Screen {
    private final Screen parent;
    private Options rows;

    LyricOptionsScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows = addRenderableWidget(new Options(minecraft, width, height));
        addOptions();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 75, height - 27, 150, 20).build());
    }

    protected abstract void addOptions();

    final void addOptionRow(AbstractWidget left, AbstractWidget right) {
        rows.add(left, right);
    }

    final void rebuildOptions() {
        double scroll = rows.getScrollAmount();
        rows.children().clear();
        addOptions();
        rows.setScrollAmount(scroll);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static final class Options extends ContainerObjectSelectionList<Options.Row> {
        Options(Minecraft client, int width, int height) {
            super(client, width, height, 32, height - 32, 24);
            setRenderSelection(false);
        }

        void add(AbstractWidget left, AbstractWidget right) {
            addEntry(new Row(left, right));
        }

        @Override public int getRowWidth() { return Math.min(310, Math.max(1, width - 24)); }
        @Override protected int getScrollbarPosition() { return width - 8; }

        private static final class Row extends Entry<Row> {
            private final List<AbstractWidget> widgets;

            Row(AbstractWidget left, AbstractWidget right) {
                widgets = right == null ? List.of(left) : List.of(left, right);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                int cell = widgets.size() == 1 ? width : (width - 10) / 2;
                for (int i = 0; i < widgets.size(); i++) {
                    var widget = widgets.get(i);
                    widget.setWidth(Math.max(1, cell));
                    widget.setX(left + i * (cell + 10));
                    widget.setY(top);
                    widget.render(graphics, mouseX, mouseY, partialTick);
                }
            }

            @Override public List<? extends GuiEventListener> children() { return widgets; }
            @Override public List<? extends NarratableEntry> narratables() { return widgets; }
        }
    }
}
