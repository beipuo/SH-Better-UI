package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentDebugOverlay;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment.Snap;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.Align.*;

/** 山海：用真实贴图和字体演示扩展后的避让及光学中心。 */
final class VisualAlignmentPreview implements PreviewScene {
    private static final String TEXT = "sh_better_ui.layout.preview.";
    private static final GraphicIcon ICON = GraphicIcon.BACK;
    private static final int PANEL_HEIGHT = 26;
    private static final int FOLLOWER_WIDTH = 30;
    private final InterruptibleAnimation expand = new InterruptibleAnimation(Spring.FLUID_PANEL);
    private AlignmentLayout layout;
    private AlignmentLayout.Node expanding;
    private AlignmentLayout.Node following;
    private AlignmentLayout.Node icon;
    private AlignmentLayout.Node caption;
    private Font font;
    private Component text;
    private int fullWidth;
    private boolean debug;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:visual_alignment", PreviewSpec.Category.CONTROLS,
                1002, TEXT + "title", 92, 0, "", VisualAlignmentPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        layout = null;
        var viewport = host.viewport();
        int width = Math.min(280, viewport.width() - 16);
        if (width < 100 || viewport.height() < 64) return;
        int left = viewport.x() + (viewport.width() - width) / 2;
        int top = viewport.y() + (viewport.height() - 64) / 2;
        var stage = new Rect(left, top, width, 34);
        var api = host.context().layout();
        var assets = api.assets(Minecraft.getInstance().getResourceManager());
        font = host.font();
        text = label("follower");
        fullWidth = Math.min(132, width - FOLLOWER_WIDTH - 20);
        layout = new AlignmentLayout(stage);
        expanding = layout.node("expand", panel(expand.value()));
        VisualAlignment.align(expanding).relativeToScreen().horizontal(LEFT).vertical(TOP).offset(4, 4).apply();
        following = layout.node("neighbor", AlignmentFrame.box(new Rect(left + 34, top + 4, FOLLOWER_WIDTH, PANEL_HEIGHT)));
        following.policies(AlignmentLayout.Axis.HORIZONTAL, 6, List.of(expanding),
                AlignmentLayout.Strategy.REFLOW, AlignmentLayout.Strategy.CLAMP_TO_PARENT);
        var iconBox = new Rect(0, 0, 16, 16);
        icon = layout.node("icon", expanding, AlignmentFrame.box(iconBox).withVisual(assets.icon(ICON, iconBox)))
                .snap(Snap.ORIGIN);
        VisualAlignment.align(icon).relativeTo(expanding).horizontal(CENTER).vertical(CENTER)
                .bounds(VisualAlignment.Bounds.VISUAL).opticalCorrection(true).apply();
        caption = layout.node("text", following, AlignmentFrame.box(new Rect(0, 0, font.width(text), font.lineHeight))
                .withVisual(assets.text(font, text.getVisualOrderText()))).snap(Snap.ORIGIN);
        VisualAlignment.align(caption).relativeTo(following).horizontal(CENTER).vertical(CENTER)
                .bounds(VisualAlignment.Bounds.VISUAL).opticalCorrection(true).apply();

        int half = (width - 6) / 2;
        host.add(Button.builder(expandLabel(), button -> {
            expand.toggle(PageTransition.nowMs());
            button.setMessage(expandLabel());
        }).bounds(left, top + 40, half, 20).build());
        host.add(Button.builder(debugLabel(), button -> {
            debug = !debug;
            button.setMessage(debugLabel());
        }).bounds(left + half + 6, top + 40, width - half - 6, 20).build());
    }

    @Override
    public void render(UiFrame frame) {
        if (layout == null) return;
        expanding.sample(expand, frame.nowMs(), frame.preferences().reducedMotion(), this::panel);
        layout.resolve();
        var api = ShBetterUiApi.layout();
        var grid = api.pixels(Minecraft.getInstance().getWindow());
        var graphics = frame.graphics();
        var panel = expanding.current(grid);
        var neighbor = following.current(grid);
        api.draw(graphics, panel, g -> g.fill(0, 0, (int) Math.round(panel.renderBounds().width()), PANEL_HEIGHT, 0xFF213455));
        api.draw(graphics, neighbor, g -> g.fill(0, 0, FOLLOWER_WIDTH, PANEL_HEIGHT, 0xFF34404F));
        api.draw(graphics, icon.current(grid), g -> ICON.draw(g, 0, 0, 1, 0xFFFFFFFF, frame.preferences()));
        api.draw(graphics, caption.current(grid), g -> g.drawString(font, text, 0, 0, 0xFFFFFFFF));
        if (debug) {
            AlignmentDebugOverlay.draw(graphics, expanding, grid);
            AlignmentDebugOverlay.draw(graphics, following, grid);
            AlignmentDebugOverlay.draw(graphics, icon, grid);
            AlignmentDebugOverlay.draw(graphics, caption, grid);
        }
    }

    @Override
    public void close() { expand.close(); layout = null; }

    private AlignmentFrame panel(double progress) {
        double width = 28 + (fullWidth - 28) * UiMath.clamp(progress, 0, 1);
        return AlignmentFrame.box(new Rect(0, 0, Math.round(width), PANEL_HEIGHT));
    }

    private Component expandLabel() { return label(expand.target() < 0.5f ? "expand" : "collapse"); }
    private Component debugLabel() { return label(debug ? "hide" : "show"); }
    private static Component label(String key) { return Component.translatable(TEXT + key); }
}
