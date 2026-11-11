// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine.Binding;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native consumer of committed correctness snapshots. Geometry, material and presented input receipts share one version. */
final class ClientCorrectnessPainter implements AutoCloseable {
    private final CorrectnessRuntime runtime;
    private final ClientGlassRender glass;
    private final Map<Binding, Row> rows = new IdentityHashMap<>();
    private ClientBackgroundSampler backdrop = new ClientBackgroundSampler(16, 100_000_000L);
    private ClientBackgroundSampler material = new ClientBackgroundSampler(16, 100_000_000L);
    private List<Binding> roster = List.of();
    private long screenGeneration = Long.MIN_VALUE, rosterVersion = Long.MIN_VALUE, budgetFrame = Long.MIN_VALUE;
    private int width, height, guiWidth, guiHeight, cursor, sampledThisFrame;
    private int maxAxis = 16;
    private long interval = 100_000_000L;
    private boolean samplingEnabled = true, closed;
    private String language;

    ClientCorrectnessPainter(CorrectnessRuntime runtime, ClientGlassRender glass) {
        this.runtime = Objects.requireNonNull(runtime); this.glass = Objects.requireNonNull(glass);
    }

    void configure(boolean enabled, long minimumIntervalNanos) {
        configureSampling(enabled, maxAxis, minimumIntervalNanos);
    }

    void configureSampling(boolean enabled, int samplesPerAxis, long minimumIntervalNanos) {
        if (closed) throw new IllegalStateException("Correctness painter is closed");
        if (samplesPerAxis < 1 || samplesPerAxis > 64 || minimumIntervalNanos < 0) {
            throw new IllegalArgumentException("Invalid native visual sampling policy");
        }
        if (samplingEnabled == enabled && maxAxis == samplesPerAxis && interval == minimumIntervalNanos) return;
        backdrop.close(); material.close();
        backdrop = new ClientBackgroundSampler(samplesPerAxis, minimumIntervalNanos);
        material = new ClientBackgroundSampler(samplesPerAxis, minimumIntervalNanos);
        samplingEnabled = enabled; maxAxis = samplesPerAxis; interval = minimumIntervalNanos;
    }

    void render(ScopeHandle screen, GuiGraphics graphics, long frameId, long nowNanos) {
        requireOpen(); Objects.requireNonNull(graphics);
        var minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        var window = minecraft.getWindow();
        String nextLanguage = minecraft.getLanguageManager().getSelected();
        long scope = screen == null ? 0 : screen.generation();
        if (target == null || target.width <= 0 || target.height <= 0) return;
        if (scope != screenGeneration || width != target.width || height != target.height
                || guiWidth != window.getGuiScaledWidth() || guiHeight != window.getGuiScaledHeight()
                || !Objects.equals(language, nextLanguage)) {
            reset(); screenGeneration = scope;
            width = target.width; height = target.height;
            guiWidth = window.getGuiScaledWidth(); guiHeight = window.getGuiScaledHeight(); language = nextLanguage;
        }
        long currentRoster = runtime.declarationVersion(screen);
        if (currentRoster != rosterVersion) {
            roster = runtime.bindings(screen); rosterVersion = currentRoster;
            var removed = rows.keySet().iterator();
            while (removed.hasNext()) {
                var binding = removed.next();
                if (!binding.isActive() || !roster.contains(binding)) {
                    UiId id = rows.get(binding).id;
                    backdrop.forget(id); material.forget(id); removed.remove();
                }
            }
            if (roster.isEmpty()) cursor = 0; else cursor %= roster.size();
        }
        if (budgetFrame != frameId) { budgetFrame = frameId; sampledThisFrame = 0; }
        long committedVersion = -1;
        for (var binding : roster) {
            var node = binding.committed().orElse(null);
            if (node != null) { committedVersion = node.version(); break; }
        }
        // Raw pixels follow declaration/resource generations; post-material pixels follow the exact render commit.
        backdrop.beginFrame(frameId, nowNanos, currentRoster);
        material.beginFrame(frameId, nowNanos, committedVersion);
        int start = cursor, selected = 0;
        for (int index = 0; index < roster.size(); index++) {
            var binding = roster.get(index);
            if (!binding.isActive()) continue;
            var node = binding.committed().orElse(null);
            if (node == null || !node.renderable() || node.clipped().empty() || node.resolved().empty()) continue;
            var row = rows.get(binding);
            if (row == null) { row = new Row(node.id()); rows.put(binding, row); }
            try {
                row.layout(node, minecraft.font);
                boolean choose = samplingEnabled && sampledThisFrame + selected < 2 && !row.lines.isEmpty()
                        && (index - start + roster.size()) % roster.size() < 2;
                if (choose) selected++;
                draw(binding, node, row, graphics, minecraft.font, choose);
            } catch (RuntimeException | LinkageError failure) {
                // An aborted native pass may leave later nodes undrawn over a newly redrawn game background.
                for (var affected : roster) runtime.presentationFailed(affected);
                throw failure;
            }
        }
        sampledThisFrame += selected;
        if (!roster.isEmpty() && samplingEnabled && sampledThisFrame < 3) cursor = (cursor + 2) % roster.size();
    }

    private void draw(Binding binding, UISnapshot.Node node, Row row, GuiGraphics graphics, Font font, boolean sample) {
        var clip = node.clipped();
        graphics.enableScissor((int) Math.floor(clip.x()), (int) Math.floor(clip.y()),
                (int) Math.ceil(clip.right()), (int) Math.ceil(clip.bottom()));
        try {
            if (row.roiTextBounds != row.textBounds || !Objects.equals(row.roiClip, clip)) {
                row.roi = row.textBounds == null ? null : pixelRegion(row.textBounds.intersection(clip), guiWidth, guiHeight, width, height);
                row.roiTextBounds = row.textBounds; row.roiClip = clip;
            }
            PixelRegion roi = row.roi;
            if (roi != null && samplingEnabled) {
                BackgroundSample raw;
                if (sample) { graphics.flush(); raw = backdrop.request(row.id, roi, Stage.BACKDROP_PRE_MATERIAL); }
                else raw = backdrop.cached(row.id, roi, Stage.BACKDROP_PRE_MATERIAL);
                if (raw != null && raw.validity() == Validity.VALID && raw.frameId() > row.rawObservedFrame
                        && runtime.observe(binding, node.version(), raw)) row.rawObservedFrame = raw.frameId();
            }
            glass.paint(graphics, row.draws);
            float protection = Math.max(node.material().opacity(), node.material().contrastMask());
            int tint = node.material().tintArgb();
            if (protection > 0 && (tint >>> 24) != 0) {
                var bounds = node.resolved();
                int fill = tint & 0xffffff | Math.round((tint >>> 24) * protection) << 24;
                RoundedCorners.draw(graphics, (float) bounds.x(), (float) bounds.y(), (float) bounds.width(),
                        (float) bounds.height(), node.declaration().visualPolicy().radius(), 0, 0, fill);
            }
            if (roi != null && samplingEnabled) {
                BackgroundSample measured;
                if (sample) { graphics.flush(); measured = material.request(row.id, roi, Stage.POST_MATERIAL_PRE_TEXT); }
                else measured = material.cached(row.id, roi, Stage.POST_MATERIAL_PRE_TEXT);
                if (measured != null && measured.validity() == Validity.VALID && measured.frameId() > row.materialObservedFrame
                        && runtime.observeMaterial(binding, node.version(), measured)) row.materialObservedFrame = measured.frameId();
            }
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(row.textX, row.textY, 0);
                graphics.pose().scale(row.scale, row.scale, 1);
                int y = 0;
                for (var line : row.lines) {
                    int x = (row.logicalWidth - line.width) / 2;
                    var text = node.text();
                    if (text.shadowRadius() > 0 && (text.shadowArgb() >>> 24) != 0) {
                        float offset = Math.max(.5f, text.shadowRadius() / row.scale);
                        translatedText(graphics, font, line.shadow, x, y, offset, offset, text.shadowArgb());
                    }
                    if (text.outlineWidth() > 0 && (text.outlineArgb() >>> 24) != 0) {
                        float amount = text.outlineWidth() / row.scale;
                        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                            if (dx != 0 || dy != 0) translatedText(graphics, font, line.outline, x, y,
                                    dx * amount, dy * amount, text.outlineArgb());
                        }
                    }
                    if ((text.argb() >>> 24) > 3) graphics.drawString(font, line.normal, x, y, text.argb(), false);
                    y += font.lineHeight;
                }
            } finally { graphics.pose().popPose(); }
            // Flushing commits all foreground quads before declaring the version presented to hit testing.
            graphics.flush();
            runtime.present(binding, node.version());
        } finally { graphics.disableScissor(); }
    }

    private static void translatedText(GuiGraphics graphics, Font font, FormattedCharSequence line, int x, int y,
                                       float dx, float dy, int color) {
        graphics.pose().pushPose();
        try { graphics.pose().translate(dx, dy, 0); graphics.drawString(font, line, x, y, color, false); }
        finally { graphics.pose().popPose(); }
    }

    static PixelRegion pixelRegion(Rect bounds, int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight) {
        if (bounds.space() != Space.GUI) throw new IllegalArgumentException("Sampling requires GUI coordinates");
        if (bounds.empty() || guiWidth <= 0 || guiHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) return null;
        double sx = framebufferWidth / (double) guiWidth, sy = framebufferHeight / (double) guiHeight;
        int left = (int) Math.max(0, Math.min(framebufferWidth, Math.floor(bounds.x() * sx)));
        int top = (int) Math.max(0, Math.min(framebufferHeight, Math.floor(bounds.y() * sy)));
        int right = (int) Math.max(0, Math.min(framebufferWidth, Math.ceil(bounds.right() * sx)));
        int bottom = (int) Math.max(0, Math.min(framebufferHeight, Math.ceil(bounds.bottom() * sy)));
        return left >= right || top >= bottom ? null : new PixelRegion(left, top, right - left, bottom - top);
    }

    List<ClientBackgroundSampler.Metrics> samplingMetrics() { return List.of(backdrop.metrics(), material.metrics()); }

    void reset() {
        if (closed) throw new IllegalStateException("Correctness painter is closed");
        backdrop.reset(); material.reset(); rows.clear(); roster = List.of();
        rosterVersion = Long.MIN_VALUE; screenGeneration = Long.MIN_VALUE; cursor = 0;
    }
    private void requireOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Correctness painter is closed");
    }
    @Override public void close() {
        if (!closed) { backdrop.close(); material.close(); rows.clear(); roster = List.of(); closed = true; }
    }

    private record GlyphLine(FormattedCharSequence normal, FormattedCharSequence outline, FormattedCharSequence shadow, int width) {}
    private static final class Row {
        final UiId id;
        long rawObservedFrame = -1, materialObservedFrame = -1;
        UIElement.Text lastText; TextStyle lastStyle; int logicalWidth = -1;
        Rect lastBounds, textBounds, roiTextBounds, roiClip; PixelRegion roi;
        float radius, scale; double textX, textY;
        GlassMaterial lastMaterial; List<GlassDraw> draws = List.of(); List<GlyphLine> lines = List.of();
        Row(UiId id) { this.id = id; }

        void layout(UISnapshot.Node node, Font font) {
            var bounds = node.resolved(); var style = node.text();
            var material = node.material().glass();
            // Foreground semantics were normalized during commit; rendering must consume that exact material.
            float nextRadius = node.declaration().visualPolicy().radius();
            if (!bounds.equals(lastBounds) || !material.equals(lastMaterial) || radius != nextRadius) {
                draws = List.of(new GlassDraw(GlassRegion.rounded((float) bounds.x(), (float) bounds.y(),
                        (float) bounds.width(), (float) bounds.height(), nextRadius), material));
                lastBounds = bounds; lastMaterial = material; radius = nextRadius;
            }
            scale = style.fontSize() / 9f;
            double padding = node.declaration().padding();
            int nextWidth = Math.max(1, (int) Math.floor(Math.max(1, bounds.width() - 2 * padding) / scale));
            if (!Objects.equals(lastText, node.declaration().text()) || !style.equals(lastStyle) || logicalWidth != nextWidth) {
                logicalWidth = nextWidth; lastText = node.declaration().text(); lastStyle = style;
                if (lastText.translationKey().isEmpty()) lines = List.of();
                else {
                    var component = Component.translatable(lastText.translationKey(), lastText.arguments().toArray());
                    if (style.fontWeight() >= 600) component.withStyle(ChatFormatting.BOLD);
                    var split = font.split(component, logicalWidth);
                    var prepared = new ArrayList<GlyphLine>(Math.min(256, split.size()));
                    for (int i = 0; i < split.size() && i < 256; i++) {
                        var line = split.get(i);
                        prepared.add(new GlyphLine(color(line, style.argb(), style.fontWeight() >= 600),
                                color(line, style.outlineArgb(), style.fontWeight() >= 600),
                                color(line, style.shadowArgb(), style.fontWeight() >= 600), font.width(line)));
                    }
                    lines = List.copyOf(prepared);
                }
            }
            textX = bounds.x() + padding;
            textY = bounds.y() + Math.max(padding, (bounds.height() - lines.size() * font.lineHeight * scale) * .5);
            double textWidth = Math.max(1, bounds.width() - 2 * padding), textHeight = Math.max(1, lines.size() * font.lineHeight * scale);
            if (lines.isEmpty()) textBounds = null;
            else if (textBounds == null || textBounds.x() != textX || textBounds.y() != textY
                    || textBounds.width() != textWidth || textBounds.height() != textHeight) {
                textBounds = new Rect(textX, textY, textWidth, textHeight, Space.GUI);
            }
        }
        private static FormattedCharSequence color(FormattedCharSequence source, int color, boolean bold) {
            return sink -> source.accept((index, style, point) -> sink.accept(index, style.withColor(color & 0xffffff).withBold(bold), point));
        }
    }
}
