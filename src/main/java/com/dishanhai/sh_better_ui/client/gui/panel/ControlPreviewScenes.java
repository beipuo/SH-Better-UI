package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.BUTTON_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.addButton;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.centeredControl;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** 山海：控件演示在自己的场景内保留交互状态。 */
final class ControlPreviewScenes {
    private ControlPreviewScenes() {}

    private static Bounds componentBounds(Bounds preview) {
        int width = Math.min(220, preview.width() - 16);
        int height = Math.min(48, preview.height() - 16);
        return new Bounds(preview.x() + (preview.width() - width) / 2,
                preview.y() + (preview.height() - height) / 2, width, height);
    }

    static final class Toggle implements PreviewScene {
        private NetworkToggle toggle;
        private boolean enabled;

        @Override
        public void layout(PreviewContext host) {
            toggle = host.add(new NetworkToggle(centeredControl(host.viewport()), label("gallery.try_toggle"),
                    enabled, host.preferences(), value -> {
                        enabled = value;
                        toggle.setValue(value);
                    }));
        }
    }

    static final class Sections implements PreviewScene {
        private int selected;

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            var sections = new UiSectionBar(area.x(), area.y(), area.width(), area.height(), host.preferences(), List.of(
                    new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.SETTINGS),
                    new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.MONITOR)),
                    selected, value -> selected = value);
            sections.buttons().forEach(host::add);
        }
    }

    static final class Tabs implements PreviewScene {
        private int selected;

        @Override
        public void layout(PreviewContext host) {
            var tabs = ShBetterUiApi.controls().underlinedTabs(componentBounds(host.viewport()), value -> selected = value);
            tabs.selection().select(selected);
            host.add(tabs);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = selected;
            return () -> {
                var scene = new Tabs();
                scene.selected = saved;
                return scene;
            };
        }
    }

    static final class Stepper implements PreviewScene {
        private int selected = 2;

        @Override
        public void layout(PreviewContext host) {
            var steps = ShBetterUiApi.controls().stepper(componentBounds(host.viewport()), 2, value -> selected = value);
            steps.progress().select(selected);
            host.add(steps);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = selected;
            return () -> {
                var scene = new Stepper();
                scene.selected = saved;
                return scene;
            };
        }
    }

    static final class Disclosure implements PreviewScene {
        private final UiDisclosure disclosure;
        private Font font;

        Disclosure() { this(false); }

        private Disclosure(boolean expanded) { disclosure = new UiDisclosure(expanded); }

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            font = host.font();
            host.add(disclosure.button(area.x(), host.viewport().y() + 4, area.width(), area.height(),
                    label("gallery.expand"), label("gallery.collapse"), host.preferences(), expanded -> {}));
        }

        @Override
        public void render(UiFrame frame) {
            var area = frame.viewport();
            if (disclosure.expanded()) {
                drawCentered(frame.graphics(), font, label("gallery.details"), area.x() + 6, area.y() + 23,
                        area.width() - 12);
            }
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            boolean expanded = disclosure.expanded();
            return () -> new Disclosure(expanded);
        }
    }

    static final class TextDetails implements PreviewScene {
        private final UiTextDetails details;
        private UiTextDetails.Layout layout;
        private UiButton detailsPage;
        private Font font;

        TextDetails() {
            this(new UiTextDetails(label("gallery.sample.text_details"), label("gallery.long_text"), 1));
        }

        private TextDetails(UiTextDetails details) {
            this.details = details;
        }

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            int width = Math.min(264, preview.width() - 16);
            int x = preview.x() + (preview.width() - width) / 2;
            int y = preview.bottom() - BUTTON_HEIGHT - 5;
            int buttonWidth = (width - 6) / 2;
            font = host.font();
            layout = details.layout(font, width);
            detailsPage = null;
            details.inlineButton(layout, x, y, buttonWidth, BUTTON_HEIGHT, host.preferences(),
                    expanded -> layout = details.layout(font, width)).ifPresent(host::add);
            details.pageButton(layout, host.screen(), x + buttonWidth + 6, y,
                    width - buttonWidth - 6, BUTTON_HEIGHT, host.preferences(), () -> detailsPage)
                    .ifPresent(button -> detailsPage = host.add(button));
        }

        @Override
        public void render(UiFrame frame) {
            var area = frame.viewport();
            int width = Math.min(264, area.width() - 16);
            int x = area.x() + (area.width() - width) / 2;
            layout.render(frame.graphics(), font, x, area.y() + 5, LiquidGlassTheme.textColor(), false,
                    frame.preferences());
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            UiTextDetails saved = details;
            return () -> new TextDetails(saved);
        }
    }

    static final class Slider implements PreviewScene {
        private int value = 50;

        @Override
        public void layout(PreviewContext host) {
            host.add(new UiSlider(centeredControl(host.viewport()), 0, 100, 5, value, host.preferences(),
                    percent -> label("gallery.slider_value", percent), percent -> value = percent));
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = value;
            return () -> {
                var scene = new Slider();
                scene.value = saved;
                return scene;
            };
        }
    }

    static final class SlideConfirm implements PreviewScene {
        private Font font;
        private UiSlideConfirm slide;

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            font = host.font();
            int resetWidth = 60;
            int gap = 6;
            int width = Math.min(164, preview.width() - 16 - resetWidth - gap);
            int x = preview.x() + (preview.width() - width - gap - resetWidth) / 2;
            int y = preview.bottom() - BUTTON_HEIGHT - 5;
            if (slide == null) {
                slide = new UiSlideConfirm(host.screen(), new Bounds(x, y, width, BUTTON_HEIGHT),
                        host.preferences(), () -> {});
            } else {
                slide.setX(x);
                slide.setY(y);
                slide.setWidth(width);
                slide.setPreferences(host.preferences());
            }
            host.add(slide);
            addButton(host, new Bounds(x + width + gap, y, resetWidth, BUTTON_HEIGHT),
                    Component.translatable("controls.reset"), GraphicIcon.RESET, slide::reset);
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            drawCentered(frame.graphics(), font, label("gallery.slide_confirm_hint"), preview.x() + 8,
                    preview.y(), preview.width() - 16);
        }
    }

    static final class Capsule implements PreviewScene {
        private UiToggleButton button;
        private boolean selected;

        @Override
        public void layout(PreviewContext host) {
            button = new UiToggleButton(centeredControl(host.viewport()), selected, host.preferences(),
                    UiStyle.LIQUID_GLASS, value -> Component.translatable(value ? "options.on" : "options.off"),
                    value -> {
                        selected = value;
                        button.setValue(value);
                    });
            button.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
            button.setModeIndicatorsVisible(true);
            host.add(button.button());
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            boolean saved = selected;
            return () -> {
                var scene = new Capsule();
                scene.selected = saved;
                return scene;
            };
        }
    }

    static final class Mode implements PreviewScene {
        private static final int COUNT = 3;
        private int selected;

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            var button = new UiButton(area.x(), area.y(), area.width(), area.height(),
                    label("gallery.mode", selected + 1), pressed -> {
                        selected = (selected + 1) % COUNT;
                        pressed.setMessage(label("gallery.mode", selected + 1));
                        ((UiButton) pressed).setModeIndicators(COUNT, selected);
                    }, host.preferences(), false, UiStyle.LIQUID_GLASS);
            button.setModeIndicators(COUNT, selected);
            button.setPreview(label("gallery.mode_tip"));
            host.add(button);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            int saved = selected;
            return () -> {
                var scene = new Mode();
                scene.selected = saved;
                return scene;
            };
        }
    }

    static final class Search implements PreviewScene {
        private String query = "";

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            var index = new SearchIndex<>(List.of(
                    new SearchIndex.Entry<>("minecraft:apple", Component.translatable("item.minecraft.apple").getString(),
                            List.of("minecraft:apple"), List.of()),
                    new SearchIndex.Entry<>("minecraft:carrot", Component.translatable("item.minecraft.carrot").getString(),
                            List.of("minecraft:carrot"), List.of())));
            var search = host.add(new SearchField<>(host.font(), area.x(), area.y(), area.width(), area.height(),
                    index, UiStyle.LIQUID_GLASS, host.preferences()));
            search.setHint(label("gallery.search_hint"));
            search.setValue(query);
            search.setResponder(value -> query = value);
        }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            String saved = query;
            return () -> {
                var scene = new Search();
                scene.query = saved;
                return scene;
            };
        }
    }

    static void dialog(PreviewContext host) {
        var area = centeredControl(host.viewport());
        host.add(DeveloperPanelScreen.button(area.x(), area.y(), area.width(), area.height(), label("demo.dialog"),
                GraphicIcon.WARNING, host.preferences(), pressed ->
                        DeveloperPanelScreen.showDialog(host.overlays(), (UiButton) pressed,
                                () -> DeveloperPanelScreen.showToast(host.preferences()))));
    }

    static final class Toast implements PreviewScene {
        private static final int HEIGHT = 16;
        private static final int GAP = 3;
        private static final int WIDTH = 82;
        private UiSwipeToast floating;
        private UiSwipeToast inline;

        @Override
        public void layout(PreviewContext host) {
            var preview = host.viewport();
            int x = preview.x() + 6;
            int stride = HEIGHT + GAP;
            int y = preview.y() + (preview.height() - 2 * stride - HEIGHT) / 2;
            addButton(host, new Bounds(x, y, WIDTH, HEIGHT), label("gallery.toast_classic"), null,
                    () -> DeveloperPanelScreen.showToast(host.preferences()));
            addButton(host, new Bounds(x, y + stride, WIDTH, HEIGHT), label("gallery.toast_floating"), null,
                    () -> showFloating(host));
            addButton(host, new Bounds(x, y + 2 * stride, WIDTH, HEIGHT), label("gallery.toast_inline"), null,
                    () -> showInline(host, x + WIDTH + 8));
            if (floating != null) {
                floating.setPreferences(host.preferences());
                host.addOverlay(floating);
            }
            if (inline != null) {
                inline.setPreferences(host.preferences());
                positionInline(preview, x + WIDTH + 8);
                host.add(inline);
            }
        }

        private void showFloating(PreviewContext host) {
            if (floating != null) {
                floating.setOpen(true);
                return;
            }
            var preferences = host.preferences();
            var acknowledge = new UiSwipeToast.Action(label("gallery.toast_acknowledge"),
                    () -> UiToast.show(UiToast.Kind.SUCCESS, label("gallery.toast_acknowledged"), Component.empty(),
                            UiToast.Effect.FADE, 2_500L, preferences));
            floating = host.addOverlay(new UiSwipeToast(host.screen(), UiSwipeToast.Placement.BOTTOM_RIGHT,
                    new UiSwipeToast.Content(label("demo.toast"), label("demo.toast_details"), GraphicIcon.INFO, acknowledge),
                    new UiSwipeToast.Options(4_000L, true, true, true), UiSwipeToast.Style.DEFAULT,
                    preferences, ignored -> {}));
        }

        private void showInline(PreviewContext host, int x) {
            if (inline != null) {
                inline.setOpen(true);
                return;
            }
            inline = new UiSwipeToast(host.screen(), UiSwipeToast.Placement.INLINE,
                    new UiSwipeToast.Content(label("gallery.toast_inline"), Component.empty(), null, null),
                    new UiSwipeToast.Options(4_000L, true, true, true), UiSwipeToast.Style.DEFAULT,
                    host.preferences(), ignored -> {});
            positionInline(host.viewport(), x);
            host.add(inline);
        }

        private void positionInline(Bounds preview, int x) {
            inline.setInlineBounds(x, preview.y() + 6, preview.right() - x - 6);
        }

        @Override
        public void suspend() {
            if (floating != null) floating.setOpen(false);
            if (inline != null) inline.setOpen(false);
        }
    }
}
