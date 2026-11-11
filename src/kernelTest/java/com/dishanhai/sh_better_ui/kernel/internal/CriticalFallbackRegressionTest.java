// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.glass.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CriticalFallbackRegressionTest {
    private KernelHost host;
    private CorrectnessRuntime engine;
    private ModuleContext module;
    private static final Rect VIEW = new Rect(0, 0, 400, 240, Space.GUI);

    @BeforeEach void start() {
        host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, new EnginePublication());
        engine = new CorrectnessRuntime(host);
        engine.budget(256, 256, 100_000_000);
        host.start(List.of(TestModules.candidate(TestModules.module("owner").build(), Set.of(), false,
                ignored -> {}, context -> module = context, ignored -> {})));
    }
    @AfterEach void stop() { host.close(); }
    private UIElement declaration(int color, double contrast) {
        var visual = new UIElement.VisualPolicy(GlassMaterial.preset(GlassVariant.REGULAR), TextStyle.of(color), contrast, true, 8, 4, 0);
        return UIElement.builder("example:critical").preferredSize(140, 40).text("example.original")
                .visualPolicy(visual).interaction(new UIElement.InteractionPolicy(true, true, false, true, "example.name")).build();
    }
    private void prepare(long frame) { engine.prepare(VIEW, List.of(), frame, frame * 16_000_000, 1, 1, false); }

    @Test void greenCriticalTextUsesAReadableOpaqueFallbackWithoutClaimingPixelEvidence() {
        var binding = engine.register(module, module.scope(), declaration(0xff00ff00, 4.5));
        prepare(1);
        var node = binding.committed().orElseThrow();
        assertTrue(ContrastPolicy.ratio(node.text().argb(), node.material().tintArgb(), node.material().tintArgb()) >= 4.5);
        assertTrue(node.legibility().valid());
        assertFalse(node.legibility().pixelObserved());
        assertFalse(node.legibility().limitations().isEmpty());
    }
    @Test void aTransparentForegroundCannotBypassTheEmergencyContrastTarget() {
        var binding = engine.register(module, module.scope(), declaration(0x20ffffff, 7));
        prepare(1);
        var node = binding.committed().orElseThrow();
        assertEquals(255, node.text().argb() >>> 24);
        assertTrue(node.legibility().worstRatio() >= 7);
        assertTrue(node.renderable());
    }
    @Test void theMaximumContrastTargetUsesPureBlackAndWhiteWhenNecessary() {
        var binding = engine.register(module, module.scope(), declaration(0xffff0000, 21));
        prepare(1);
        var node = binding.committed().orElseThrow();
        assertEquals(21, node.legibility().worstRatio(), .0001);
        assertFalse(node.legibility().pixelObserved());
    }
    @Test void contentOrPaddingChangesRetireMaterialPixelsFromTheOldTextRegion() {
        var binding = engine.register(module, module.scope(), declaration(0xffffffff, 4.5));
        prepare(1);
        var node = binding.committed().orElseThrow();
        var sample = BackgroundSample.pixels(new PixelRegion(4, 4, 100, 10), 1, 1,
                new int[] {node.material().tintArgb()}, Stage.POST_MATERIAL_PRE_TEXT, Source.CPU_PIXELS,
                1, 16_000_000, node.version());
        assertTrue(engine.observeMaterial(binding, node.version(), sample));
        prepare(2);
        assertTrue(binding.committed().orElseThrow().legibility().pixelObserved());
        assertTrue(binding.update(binding.revision(), binding.declaration().toBuilder().text("example.changed").padding(9).build()));
        prepare(3);
        var changed = binding.committed().orElseThrow();
        assertNotEquals(Validity.VALID, changed.materialObservation().validity());
        assertFalse(changed.legibility().pixelObserved());
    }

    @Test void worldObservationResetAndModalSuspensionWithdrawOldInputAndPixels() {
        var binding = engine.register(module, module.scope(), declaration(0xffffffff, 4.5));
        prepare(1);
        var first = binding.committed().orElseThrow();
        assertTrue(engine.present(binding, first.version()));
        assertTrue(binding.hitTest(first.visible().x() + 1, first.visible().y() + 1));
        engine.suspendPresentation();
        assertFalse(binding.hitTest(first.visible().x() + 1, first.visible().y() + 1));
        assertTrue(binding.isActive());
        var sample = BackgroundSample.pixels(new PixelRegion(4, 4, 100, 10), 1, 1,
                new int[] {first.material().tintArgb()}, Stage.POST_MATERIAL_PRE_TEXT, Source.CPU_PIXELS,
                1, 16_000_000, first.version());
        assertTrue(engine.observeMaterial(binding, first.version(), sample));
        prepare(2);
        assertTrue(binding.committed().orElseThrow().legibility().pixelObserved());
        engine.invalidateObservations();
        prepare(3);
        assertFalse(binding.committed().orElseThrow().legibility().pixelObserved());
    }
}
