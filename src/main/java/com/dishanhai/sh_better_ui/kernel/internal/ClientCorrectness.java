// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Native adapters are injected into the existing kernel service, never the other way around. */
final class ClientCorrectness implements UICorrectnessEngine, AutoCloseable {
    private final KernelHost host;
    private final ThemeManager themes;
    private final CorrectnessRuntime runtime;
    private final ClientCorrectnessPainter painter;
    private final ClientCorrectnessMotion motion = new ClientCorrectnessMotion();
    private final Map<Key, Style> styles = new HashMap<>();
    private final Map<MeasureKey, UISnapshot.TextMeasurement> measurements = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<MeasureKey, UISnapshot.TextMeasurement> entry) { return size() > 256; }
    };
    private Settings settings = Settings.DEFAULT;
    private long frame, resources = -1;
    private Object connection;

    ClientCorrectness(KernelHost host, ThemeManager themes, ClientGlassRender glass) {
        this.host = host; this.themes = themes;
        runtime = new CorrectnessRuntime(host, this::measure, motion, this::visual);
        painter = new ClientCorrectnessPainter(runtime, glass);
    }

    void prepare(UIPlatform.Viewport view, List<UISnapshot.ReservedArea> areas, long resourceVersion, Settings policy) {
        host.assertOwner(); settings = policy;
        var minecraft = Minecraft.getInstance();
        var nextConnection = minecraft == null ? null : minecraft.getConnection();
        if (resources != resourceVersion || connection != nextConnection) {
            reset(); resources = resourceVersion; connection = nextConnection;
        }
        runtime.settings(policy);
        runtime.prepare(new Rect(0, 0, view.width(), view.height(), Space.GUI), areas, ++frame, host.nowNanos(),
                Math.max(0, resourceVersion), themes.snapshot().version(), ClientUiSettings.current().reducedMotion());
    }

    void render(ScopeHandle screen, GuiGraphics graphics) {
        var minecraft = Minecraft.getInstance();
        if (modalBlocks(minecraft, Double.NaN, Double.NaN)) { suspendPresentation(); return; }
        painter.configure(settings.backgroundSampling(), settings.sampleIntervalNanos());
        if (settings.enabled()) painter.render(screen, graphics, frame, host.nowNanos());
    }

    long frameId() { return frame; }
    void suspendPresentation() { runtime.suspendPresentation(); }
    void reset() { measurements.clear(); painter.reset(); runtime.invalidateObservations(); }

    private static boolean modalBlocks(Minecraft minecraft, double x, double y) {
        if (minecraft == null) return false;
        if (!minecraft.isWindowActive() || minecraft.getOverlay() != null) return true;
        var screen = minecraft.screen;
        if (screen == null) return minecraft.options.hideGui;
        var overlay = UiOverlayHost.current(screen);
        if (overlay != null && overlay.isModalOpen()) return true;
        var local = UiLocalModal.current(screen);
        return local != null && (!Double.isFinite(x) || local.blocksPointer(x, y));
    }

    @Override public Binding register(ModuleContext module, ScopeHandle scope, UIElement declaration) {
        var binding = runtime.register(module, scope, declaration);
        var target = host.ownedScope(module, scope);
        var key = new Key(target.generation(), declaration.id());
        var style = new Style(key, module, target, binding);
        try {
            styles.put(key, style);
            runtime.onRelease(binding, style::close);
            style.bind();
            style.lifetime = host.onScopeClose(target, style::close);
            return style;
        } catch (RuntimeException failure) {
            styles.remove(key, style); binding.close(); throw failure;
        }
    }

    private UIElement.VisualPolicy visual(long generation, UIElement declaration) {
        var source = declaration.visualPolicy();
        var bound = styles.get(new Key(generation, declaration.id()));
        int color = source.text().argb();
        if (!source.foregroundToken().isEmpty()) {
            if (bound != null && bound.value != null && !bound.value.isClosed()) color = bound.value.value().argb();
            else {
                var token = colorToken(source.foregroundToken());
                if (token != null) color = themes.snapshot().resolve(token).argb();
            }
        }
        boolean highContrast = ClientUiSettings.current().highContrast();
        return new UIElement.VisualPolicy(source.glass(), source.text().foreground(color),
                highContrast ? Math.max(7, source.minimumContrast()) : source.minimumContrast(), source.adaptive(),
                source.radius(), highContrast ? 0 : source.shadowExtent(), source.z(), source.foregroundToken());
    }

    @SuppressWarnings("unchecked")
    private ThemeToken<ColorValue> colorToken(String id) {
        var token = themes.tokenCatalog().get(id);
        return token != null && token.type().valueClass() == ColorValue.class ? (ThemeToken<ColorValue>) token : null;
    }

    private UISnapshot.TextMeasurement measure(UIElement declaration, Rect bounds, long resourceVersion) {
        var text = declaration.text();
        if (text.translationKey().isEmpty()) return new UISnapshot.TextMeasurement(0, 0, resourceVersion, true);
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return new UISnapshot.TextMeasurement(text.measuredWidth(), text.measuredHeight(), resourceVersion, false);
        }
        var visual = declaration.visualPolicy().text();
        float scale = visual.fontSize() / 9f;
        int width = Math.max(1, Math.min(32768, (int) Math.floor(Math.max(0, bounds.width() - 2 * declaration.padding()) / scale)));
        var key = new MeasureKey(text, width, visual.fontSize(), visual.fontWeight(), resourceVersion,
                minecraft.getLanguageManager().getSelected());
        var cached = measurements.get(key);
        if (cached != null) return cached;
        var component = Component.translatable(text.translationKey(), text.arguments().toArray());
        if (visual.fontWeight() >= 600) component = component.withStyle(ChatFormatting.BOLD);
        var lines = minecraft.font.split(component, width);
        double actualWidth = 0;
        for (var line : lines) actualWidth = Math.max(actualWidth, minecraft.font.width(line) * scale);
        var result = new UISnapshot.TextMeasurement(actualWidth, lines.size() * minecraft.font.lineHeight * scale, resourceVersion, true);
        measurements.put(key, result); return result;
    }

    @Override public UISnapshot snapshot(ModuleContext module, ScopeHandle scope) { return runtime.snapshot(module, scope); }
    @Override public ValidationReport report(ModuleContext module, ScopeHandle scope) { return runtime.report(module, scope); }
    @Override public ValidationReport validate(UISnapshot snapshot) { return runtime.validate(snapshot); }
    @Override public List<SpatialIntelligence.CollisionPrediction> predict(UISnapshot snapshot, double horizon) { return runtime.predict(snapshot, horizon); }
    @Override public CorrectionPlan resolve(UISnapshot snapshot, ValidationReport report) { return runtime.resolve(snapshot, report); }
    @Override public CorrectionPlan.Result apply(ModuleContext module, ScopeHandle scope, CorrectionPlan plan) { return runtime.apply(module, scope, plan); }
    @Override public CorrectionPlan.Result rollback(ModuleContext module, ScopeHandle scope, CorrectionPlan plan) { return runtime.rollback(module, scope, plan); }
    @Override public String exportJson(ModuleContext module, ScopeHandle scope) { return runtime.exportJson(module, scope); }
    @Override public Metrics metrics() {
        var core = runtime.metrics();
        var sampling = painter.samplingMetrics();
        long cpu = 0, hits = 0;
        for (var sample : sampling) { cpu += sample.cpuReadbackNanos(); hits += sample.cacheHits(); }
        return new Metrics(core.layoutNanos(), core.motionNanos(), core.contrastNanos(), core.correctionNanos(),
                core.renderPreparationNanos(), core.cacheHits(), core.revalidations(), core.rejectedResults(),
                core.registeredNodes(), core.lastIterations(), -1, cpu, hits, -1);
    }
    @Override public void close() {
        for (var style : List.copyOf(styles.values())) style.close();
        painter.close(); motion.close(); measurements.clear();
    }

    private record Key(long generation, UiId id) {}
    private record MeasureKey(UIElement.Text text, int width, float size, float weight, long resources, String language) {}

    private final class Style implements Binding {
        final Key key; final ModuleContext module; final ScopeLifecycle.Scope scope; final Binding binding;
        ThemeScope.InvalidationOwner owner; ThemeScope.BoundValue<ColorValue> value; Subscription lifetime;
        boolean closed;
        Style(Key key, ModuleContext module, ScopeLifecycle.Scope scope, Binding binding) {
            this.key = key; this.module = module; this.scope = scope; this.binding = binding;
        }
        void bind() {
            if (owner != null) owner.close(); owner = null; value = null;
            var token = colorToken(binding.declaration().visualPolicy().foregroundToken());
            if (token == null) return;
            var provider = module.engine().require(UIApis.THEME);
            var theme = (scope.kind() == ScopeKind.UI_TREE || scope.kind() == ScopeKind.COMPONENT) && provider instanceof ClientTheme nativeTheme
                    ? nativeTheme.nativeScope(module, scope) : themes.global();
            owner = theme.invalidationOwner(changes -> { if (binding.isActive()) binding.invalidate(InvalidationReason.THEME); });
            value = theme.bind(ThemeBinding.of(token), owner, StyleInvalidation.PAINT);
        }
        @Override public UIElement declaration() { return binding.declaration(); }
        @Override public long revision() { return binding.revision(); }
        @Override public boolean update(long expectedRevision, UIElement next) {
            boolean changed = !binding.declaration().visualPolicy().foregroundToken().equals(next.visualPolicy().foregroundToken());
            boolean accepted = binding.update(expectedRevision, next);
            if (accepted && changed) bind(); return accepted;
        }
        @Override public void invalidate(InvalidationReason reason) { binding.invalidate(reason); }
        @Override public Optional<UISnapshot.Node> committed() { return binding.committed(); }
        @Override public Optional<UISnapshot.Node> presented() { return binding.presented(); }
        @Override public boolean hitTest(double x, double y) {
            return !modalBlocks(Minecraft.getInstance(), x, y) && binding.hitTest(x, y);
        }
        @Override public boolean isActive() { return binding.isActive(); }
        @Override public void close() {
            host.assertOwner();
            if (closed) return;
            closed = true;
            if (owner != null) { owner.close(); owner = null; value = null; }
            styles.remove(key, this); binding.close();
            if (lifetime != null && lifetime.isActive()) lifetime.close();
        }
    }
}
