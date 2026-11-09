package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.lyrics.LyricDemo;
import com.dishanhai.sh_better_ui.client.lyrics.common.bridge.BridgePlaybackSource;
import com.dishanhai.sh_better_ui.client.lyrics.common.i18n.LangKeys;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LayoutMode;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.LyricStyleConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 山海：Link-Lyric（beipuo）歌词渲染的离线预览。 */
final class LyricGalleryPreview implements PreviewScene {
    static final int HEIGHT = 166;
    private static final String TEXT = "sh_better_ui.lyrics.gallery.";
    private static final String SOURCE = "https://github.com/beipuo/Link-Lyric";
    private static final int GAP = 6;
    private static final int BUTTON = GalleryPreviewLayout.BUTTON_HEIGHT;
    private static final LayoutMode[] LAYOUTS = LayoutMode.values();

    private final LyricDemo demo = new LyricDemo();
    private LayoutMode layout = LayoutMode.LINEAR;
    private Font font;
    private UiButton playback;
    private UiButton layoutButton;
    private UiButton sourceButton;

    @Override
    public void layout(PreviewContext host) {
        font = host.font();
        var area = host.viewport();
        int width = Math.max(1, Math.min(142, (area.width() - 3 * GAP) / 2));
        int x = area.x() + (area.width() - 2 * width - GAP) / 2;
        int y = area.bottom() - 2 * (BUTTON + GAP);
        playback = GalleryPreviewLayout.addButton(host, new Bounds(x, y, width, BUTTON),
                label(demo.playing() ? "pause" : "play"), GraphicIcon.PAUSE, () -> {
                    demo.toggle();
                    updatePlayback();
                });
        playback.active = !host.preferences().reducedMotion();
        if (!playback.active) playback.setPreview(label("reduced"));
        updatePlayback();

        layoutButton = GalleryPreviewLayout.addButton(host, new Bounds(x + width + GAP, y, width, BUTTON),
                Component.translatable(layout.labelKey), GraphicIcon.MUSIC, () -> {
                    layout = LAYOUTS[(layout.ordinal() + 1) % LAYOUTS.length];
                    layoutButton.setMessage(Component.translatable(layout.labelKey));
                });
        layoutButton.setPreview(label("layout"));
        y += BUTTON + GAP;
        GalleryPreviewLayout.addButton(host, new Bounds(x, y, width, BUTTON), label("settings"),
                GraphicIcon.SETTINGS, () -> Minecraft.getInstance().setScreen(
                        ShBetterUiApi.lyrics().settings(host.screen())))
                .setPreview(Component.translatable(LangKeys.LINK_MC_HELP));
        sourceButton = GalleryPreviewLayout.addButton(host, new Bounds(x + width + GAP, y, width, BUTTON),
                label("source"), GraphicIcon.LINK, () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(SOURCE);
                    sourceButton.setPreview(label("copied").append("\n").append(SOURCE));
                });
        sourceButton.setPreview(label("source_hint").append("\n").append(SOURCE)
                .append("\n\n").append(label("provider"))
                .append("\n").append(BridgePlaybackSource.LINK_MC_URL));
    }

    @Override
    public void render(UiFrame frame) {
        var area = frame.viewport();
        var graphics = frame.graphics();
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF111B2A);
        int fcsY = playback.getY() - font.lineHeight - GAP;
        int bottom = fcsY - GAP;
        var lyrics = new Bounds(area.x() + GAP, area.y() + GAP, area.width() - 2 * GAP,
                bottom - area.y() - GAP);
        var style = LyricStyleConfig.DEFAULT.withLayout(layout).withLineCount(3);
        var sample = demo.frame(frame.preferences().reducedMotion());
        ShBetterUiApi.lyrics().draw(demo.renderer(style), frame, font, lyrics);
        long position = sample.snapshot().positionMs();
        var line = sample.timeline().lineAt(position);
        if (line == null || fcsY < area.y()) return;

        var text = Component.literal(line.text()).withStyle(ChatFormatting.BOLD).getVisualOrderText();
        boolean highContrast = frame.preferences().highContrast();
        int sung = highContrast ? 0xFFEB78 : style.sungColor();
        int unsung = highContrast ? 0xFFFFFF : 0x91A4BE;
        var colored = ShBetterUiApi.graphics().colorizeLyricText(text, line.progressAt(position), sung, unsung);
        int left = area.x() + GAP;
        var tag = label("fcs");
        graphics.drawString(font, tag, left, fcsY, 0xFFD1DEF1, false);
        graphics.drawString(font, colored, left + font.width(tag) + GAP, fcsY, 0xFFFFFFFF, false);
    }

    private void updatePlayback() {
        playback.setMessage(label(demo.playing() && playback.active ? "pause" : "play"));
        playback.setLeadingIcon(demo.playing() && playback.active ? GraphicIcon.PAUSE : GraphicIcon.RESUME);
    }

    private static MutableComponent label(String key) {
        return Component.translatable(TEXT + key);
    }
}
