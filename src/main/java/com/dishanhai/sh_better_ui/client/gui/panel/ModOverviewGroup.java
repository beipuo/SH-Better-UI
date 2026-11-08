package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** 山海：概览页的可聚焦信息组，复用文本布局以保留长硬件名称和完整旁白。 */
final class ModOverviewGroup extends AbstractWidget {
    private static final int INSET = 4;
    private final Font font;
    private UiPreferences preferences;
    private UiTextDetails.Layout titleLayout;
    private UiTextDetails.Layout detailsLayout;

    ModOverviewGroup(int x, int y, int width, ModOverviewData.Group group, Font font, UiPreferences preferences) {
        super(x, y, width, 1, CommonComponents.EMPTY);
        this.font = font;
        this.preferences = preferences;
        update(group);
    }

    void update(ModOverviewData.Group group) {
        var details = Component.empty();
        for (Component line : group.lines()) {
            if (!details.getSiblings().isEmpty()) details.append(CommonComponents.NEW_LINE);
            details.append(line);
        }
        setMessage(group.title().copy().append(CommonComponents.NEW_LINE).append(details));
        int width = Math.max(1, getWidth() - 2 * INSET);
        titleLayout = new UiTextDetails(group.title(), group.title(), Integer.MAX_VALUE).layout(font, width);
        detailsLayout = new UiTextDetails(group.title(), details, Integer.MAX_VALUE).layout(font, width);
        setHeight(2 * INSET + titleLayout.height() + INSET + detailsLayout.height());
    }

    void setPreferences(UiPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isFocused()) RoundedCorners.draw(graphics, getX(), getY(), getWidth(), getHeight(),
                4f, 1f, LiquidGlassTheme.ACCENT, 0);
        int y = getY() + INSET;
        titleLayout.render(graphics, font, getX() + INSET, y, LiquidGlassTheme.ACCENT, false, preferences);
        detailsLayout.render(graphics, font, getX() + INSET, y + titleLayout.height() + INSET,
                LiquidGlassTheme.TEXT, false, preferences);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
