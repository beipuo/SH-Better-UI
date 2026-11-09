package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame.LayoutBounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame.VisualBounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Direction;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Issue;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment.Snap;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.Reference;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;
import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Axis.HORIZONTAL;
import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Strategy.*;
import static com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.Align.*;
import static com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.align;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：跨平台视觉对齐、输入和增量布局契约。 */
class VisualAlignmentTest {
    private static final PixelAlignment ONE = PixelAlignment.ONE;
    private static final double EPS = 1e-8;

    @Test
    void alignsInParentAndScreenWithPaddingMarginAndRtl() {
        var layout = new AlignmentLayout(new Rect(10, 20, 101, 81));
        var container = box(20, 30, 64, 50);
        var parent = layout.node("parent", container.withLayout(new LayoutBounds(container.layout().rect(),
                Insets.all(4), Insets.NONE, new Anchor(0, 0))));
        var frame = box(0, 0, 11, 7);
        var child = layout.node("child", parent, frame.withLayout(new LayoutBounds(frame.layout().rect(),
                Insets.NONE, Insets.all(2), new Anchor(0, 0))));
        align(child).relativeToParent().horizontal(START).vertical(BOTTOM).apply();
        layout.resolve();
        assertEquals(new Rect(26, 67, 11, 7), child.current(ONE).layoutBounds());
        layout.direction(Direction.RIGHT_TO_LEFT);
        assertEquals(1, layout.resolve().solved());
        assertEquals(new Rect(67, 67, 11, 7), child.current(ONE).layoutBounds());

        var center = layout.node("center", box(0, 0, 10, 4));
        align(center).relativeToScreen().horizontal(CENTER).vertical(TOP).apply();
        layout.resolve();
        assertEquals(55.5, center.current(ONE).layoutBounds().x());
        assertEquals(20, center.current(ONE).layoutBounds().y());
        layout.screen(new Rect(0, 0, 100, 70));
        assertEquals(1, layout.resolve().solved());
        assertEquals(45, center.current(ONE).layoutBounds().x());
    }

    @Test
    void alphaBoundsManualCorrectionAndHitRemainIndependent() {
        var texture = TextureMetrics.scan(8, 4, 1, (x, y) -> y == 1 ? x == 1 ? 255 : x == 3 ? 85 : 0 : 0);
        assertEquals(new Rect(0.125, 0.25, 0.375, 0.25), texture.visible());
        assertEquals(new Anchor(0.25, 0.375), texture.centroid());
        assertTrue(TextureMetrics.scan(2, 2, 64, (x, y) -> 63).visible().empty());

        var layout = new AlignmentLayout(new Rect(0, 0, 100, 50));
        var image = layout.node("image", box(0, 0, 16, 8)
                .withVisual(texture.in(new Rect(0, 0, 16, 8)).withCorrection(1, 0)
                        .withEffects(new Insets(0, 0, 2, 1)))
                .withHit(new Rect(0, 0, 15, 8)).withClip(new Rect(0, 0, 9, 7)));
        align(image).horizontal(CENTER).vertical(CENTER).bounds(Bounds.VISUAL)
                .opticalCorrection(true).opticalOffset(1, 0).apply();
        layout.resolve();
        var view = image.current(ONE);
        assertEquals(new Rect(44, 22, 16, 8), view.layoutBounds());
        assertEquals(new Rect(46, 24, 7, 3), view.visualBounds());
        assertEquals(new Rect(44, 22, 9, 7), view.hitBounds());
        assertEquals(new Anchor(49, 25), view.opticalCenter());
        assertTrue(view.hitTest(44.5, 22.5));
        assertFalse(view.hitTest(53, 25));
    }

    @Test
    void alignsDistinctGlyphMetricsAndDiagnosesMissingBaseline() {
        var layout = new AlignmentLayout(new Rect(0, 0, 100, 50));
        var title = layout.node("title", box(20, 11, 32, 10)
                .withVisual(new VisualBounds(new Rect(1, 1, 27, 8)).withBaseline(8)));
        var caption = layout.node("caption", box(0, 0, 11, 9)
                .withVisual(new VisualBounds(new Rect(0, 2, 9, 6)).withBaseline(6)));
        align(caption).relativeTo(title).vertical(BASELINE)
                .horizontal(LEFT, RIGHT).bounds(Bounds.VISUAL).offset(3, 0).apply();
        layout.resolve();
        assertEquals(new Rect(51, 13, 11, 9), caption.current(ONE).layoutBounds());

        var glyphless = layout.node("icon", box(0, 0, 10, 10));
        align(glyphless).relativeTo(title).vertical(BASELINE).priority(3).apply();
        align(glyphless).relativeTo(new Anchor(3, 5)).vertical(TOP).apply();
        layout.resolve();
        assertEquals(5, glyphless.current(ONE).layoutBounds().y());
        assertTrue(has(glyphless, Issue.MISSING_BASELINE));
    }

    @Test
    void composesNestedTransformsAndUsesInverseForHits() {
        var layout = new AlignmentLayout(new Rect(0, 0, 100, 100));
        var parent = layout.node("scale", box(40, 20, 30, 30)
                .withPose(Transform.scale(2, 1.5)).withClip(new Rect(0, 0, 20, 20)));
        var child = layout.node("rotate", parent, box(3, 2, 8, 6).withPose(Transform.rotation(Math.PI / 4)));
        child.policies(HORIZONTAL, 0, List.of(), KEEP_ANCHOR);
        layout.resolve();
        var view = child.current(ONE);
        assertEquals(new Anchor(54, 27.5), view.anchor());
        assertEquals(new Rect(46, 23, 16, 9), view.layoutBounds());
        assertEquals(new Rect(40, 20, 40, 30), view.clip());
        assertTrue(view.hitTest(54, 27.5));
        assertTrue(view.hitBounds().contains(44.2, 20.3));
        assertFalse(view.hitTest(44.2, 20.3));
        child.update(box(3, 2, 8, 6).withPose(Transform.scale(0, 0)));
        layout.resolve();
        assertFalse(child.current(ONE).hitTest(54, 27.5));
    }

    @Test
    void ordersConflictsDeterministicallyAndRejectsCyclesBeforePublication() {
        var layout = new AlignmentLayout(new Rect(0, 0, 100, 100));
        var a = layout.node("a", box(0, 0, 5, 5));
        var b = layout.node("b", box(50, 0, 5, 5));
        align(a).relativeTo(b).horizontal(LEFT).priority(7).apply();
        align(a).relativeToScreen().horizontal(RIGHT).priority(7).apply();
        var higher = align(a).relativeTo(new Anchor(22, 30)).horizontal(LEFT).priority(8).apply();
        layout.resolve();
        assertEquals(22, a.current(ONE).layoutBounds().x());
        assertTrue(has(a, Issue.CONFLICT));
        higher.close();
        layout.resolve();
        assertEquals(50, a.current(ONE).layoutBounds().x());
        var cycle = align(b).relativeTo(a).vertical(TOP).apply();
        var error = assertThrows(IllegalStateException.class, layout::resolve);
        assertTrue(error.getMessage().contains("a -> b -> a"));
        assertEquals(50, a.current(ONE).layoutBounds().x());
        cycle.close();
        layout.resolve();
        assertFalse(layout.resolve().rebuiltGraph());
        assertThrows(IllegalArgumentException.class, () -> box(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Transform.scale(Double.POSITIVE_INFINITY, 1));
    }

    @Test
    void scaleChangesSnapOnceAndNeverAccumulateInChildren() {
        var layout = new AlignmentLayout(new Rect(0, 0, 101, 101));
        var parent = layout.node("parent", box(0, 0, 8, 10)).snap(Snap.ORIGIN);
        align(parent).horizontal(CENTER).vertical(CENTER).apply();
        var child = layout.node("child", parent, box(.25, .25, 3, 3)).snap(Snap.ANCHOR);
        layout.resolve();
        for (int pass = 0; pass < 30; pass++) {
            for (int scale = 1; scale <= 4; scale++) {
                var grid = new PixelAlignment(scale);
                var view = child.current(grid);
                assertEquals(Math.rint(view.anchor().x() * scale), view.anchor().x() * scale, EPS);
                assertEquals(grid.snap(new Anchor(48.25, 47.25)), view.anchor());
                assertTrue(view.hitTest(view.anchor().x(), view.anchor().y()));
                assertEquals(0, layout.resolve().solved());
            }
        }
    }

    @Test
    void hoverPoseCanKeepTheOriginalHitArea() {
        var layout = new AlignmentLayout(new Rect(0, 0, 80, 80));
        var frame = box(20, 20, 10, 10);
        var button = layout.node("button", frame);
        button.policies(HORIZONTAL, 0, List.of(), KEEP_ANCHOR);
        for (int i = 0; i < 100; i++) {
            double scale = 1 + .1 * Math.sin(i * .6);
            button.update(frame.withPose(Transform.scale(scale, scale)).withHitFollowsPose(false));
            layout.resolve();
            var view = button.current(ONE);
            assertEquals(scale * 10, view.renderBounds().width(), EPS);
            assertEquals(25, view.anchor().x(), EPS);
            assertEquals(25, view.anchor().y(), EPS);
            assertEquals(new Rect(20, 20, 10, 10), view.hitBounds());
            assertTrue(view.hitTest(20, 20));
            assertFalse(view.hitTest(30, 30));
        }
    }

    @Test
    void retargetsVisibleExpansionAndSupportsCurrentAndTargetFollowers() {
        var layout = new AlignmentLayout(new Rect(0, 0, 100, 30));
        var panel = layout.node("panel", box(2, 2, 20, 12));
        var follower = layout.node("current", box(0, 2, 10, 12));
        follower.policies(HORIZONTAL, 3, List.of(panel), REFLOW);
        var reserve = layout.node("target", box(0, 18, 10, 8));
        align(reserve).relativeTo(panel).horizontal(LEFT, RIGHT).offset(3, 0).reference(Reference.TARGET).apply();
        try (var expansion = new InterruptibleAnimation(Spring.FLUID_PANEL)) {
            expansion.animateTo(1, 1000);
            for (long now = 1000; now <= 1080; now += 8) {
                panel.sample(expansion, now, false, value -> box(2, 2, 20 + 40 * value, 12));
                layout.resolve();
                assertEquals(panel.current(ONE).visualBounds().right() + 3, follower.current(ONE).layoutBounds().x(), EPS);
                assertEquals(65, reserve.current(ONE).layoutBounds().x(), EPS);
            }
            var before = panel.current(ONE);
            var beforeFollower = follower.current(ONE);
            expansion.animateTo(0, 1080);
            panel.sample(expansion, 1080, false, value -> box(2, 2, 20 + 40 * value, 12));
            layout.resolve();
            assertEquals(before, panel.current(ONE));
            assertEquals(beforeFollower, follower.current(ONE));
            assertEquals(25, reserve.current(ONE).layoutBounds().x(), EPS);
            assertEquals(0, layout.resolve().solved());
        }
    }

    @Test
    void avoidsChainedObstaclesAndReportsWhenClampMakesFlowImpossible() {
        var layout = new AlignmentLayout(new Rect(0, 0, 35, 12));
        var near = layout.node("near", box(4, 0, 12, 10));
        var far = layout.node("far", box(18, 0, 10, 10));
        var moving = layout.node("moving", box(3, 0, 8, 10));
        moving.policies(HORIZONTAL, 1, List.of(far, near), AVOID_OVERLAP, CLAMP_TO_PARENT);
        layout.resolve();
        assertEquals(27, moving.current(ONE).layoutBounds().x());
        assertTrue(has(moving, Issue.OVERLAP));
        layout.screen(new Rect(0, 0, 55, 12));
        assertEquals(1, layout.resolve().solved());
        assertEquals(29, moving.current(ONE).layoutBounds().x());
        assertFalse(has(moving, Issue.OVERLAP));
        moving.policies(HORIZONTAL, 1, List.of(), ALLOW_OVERLAY);
        layout.resolve();
        assertTrue(moving.current(ONE).visualBounds().overlaps(near.current(ONE).visualBounds()));
    }

    @Test
    void animatedTargetCompensationKeepsVisiblePositionOnRepeatedReversal() {
        var layout = new AlignmentLayout(new Rect(0, 0, 120, 50));
        var closed = box(4, 4, 20, 12);
        var open = box(4, 4, 80, 12);
        var panel = layout.node("panel", closed);
        var follower = layout.node("follower", box(0, 24, 10, 8)).animateTargetAlignment(Spring.FLUID_PANEL);
        align(follower).relativeTo(panel).horizontal(LEFT, RIGHT).reference(Reference.TARGET).apply();
        layout.resolve(1000, false);
        panel.update(closed, open);
        layout.resolve(1000, false);
        assertEquals(24, follower.current(ONE).layoutBounds().x(), EPS);
        assertEquals(84, follower.target(ONE).layoutBounds().x(), EPS);
        for (int i = 1; i <= 12; i++) {
            long now = 1000 + i * 35L;
            layout.resolve(now, false);
            var before = follower.current(ONE);
            panel.update(closed, i % 2 == 0 ? open : closed);
            layout.resolve(now, false);
            assertEquals(before.layoutBounds().x(), follower.current(ONE).layoutBounds().x(), EPS);
            assertEquals(0, layout.resolve(now, false).solved());
        }
        layout.resolve(1420, true);
        assertEquals(84, follower.current(ONE).layoutBounds().x(), EPS);
        assertEquals(0, layout.resolve(1421, true).solved());
    }

    @Test
    void cachesMissingTexturesAndUsesAuthoredOpticalMetadata() {
        var assets = new AlignmentAssets(ResourceManager.Empty.INSTANCE);
        var id = ResourceLocation.tryParse("sh_better_ui:textures/gui/test.png");
        assertTrue(assets.texture(id, 1).isEmpty());
        assertTrue(assets.texture(id, 1).isEmpty());
        assertEquals(new AlignmentAssets.Stats(1, 1), assets.stats());
        assets.invalidate();
        assertTrue(assets.texture(id, 1).isEmpty());
        var metadata = new TextureMetrics(new Rect(.25, .1, .5, .8), new Anchor(.4, .5));
        assets.textureMetadata(id, metadata);
        assertEquals(metadata, assets.texture(id, 1).orElseThrow());
        assets.invalidate();
        assertEquals(metadata, assets.texture(id, 1).orElseThrow());
        assertEquals(new AlignmentAssets.Stats(3, 2), assets.stats());
    }

    @Test
    void placesNativeWidgetWithTheSameHitArea() {
        var api = ShBetterUiApi.layout();
        var button = Button.builder(Component.empty(), ignored -> {}).bounds(0, 0, 20, 10).build();
        var layout = new AlignmentLayout(new Rect(0, 0, 101, 51));
        var node = layout.node("button", api.widget(button)).snap(Snap.GUI_ORIGIN);
        align(node).horizontal(CENTER).vertical(CENTER).apply();
        layout.resolve();
        var view = node.current(new PixelAlignment(3));
        api.placeWidget(button, view);
        assertEquals(41, button.getX());
        assertEquals(21, button.getY());
        assertEquals(button.isMouseOver(41, 21), view.hitTest(41, 21));
        assertEquals(button.isMouseOver(61, 31), view.hitTest(61, 31));
        assertThrows(IllegalArgumentException.class, () -> api.placeWidget(button, node.snap(Snap.NONE).current(ONE)));
    }

    @Test
    void measuresLargeLayoutAndTwoSimultaneousAnimatedBranches() {
        int total = 2500;
        var layout = new AlignmentLayout(new Rect(0, 0, 1200, 800));
        for (int i = 0; i < total - 4; i++) layout.node("static-" + i, box(i % 100, i / 100, 1, 1));
        var first = layout.node("first", box(10, 10, 15, 15));
        var second = layout.node("second", box(10, 30, 15, 15));
        align(layout.node("a", box(0, 0, 6, 4))).relativeTo(first).horizontal(LEFT, RIGHT).vertical(TOP).apply();
        align(layout.node("b", box(0, 0, 6, 4))).relativeTo(second).horizontal(LEFT, RIGHT).vertical(TOP).apply();
        long start = System.nanoTime();
        assertEquals(total, layout.resolve().solved());
        long initial = System.nanoTime() - start;
        start = System.nanoTime();
        for (int frame = 1; frame <= 240; frame++) {
            double scale = 1 + .2 * Math.sin(frame * .5);
            first.update(box(10, 10, 15, 15).withPose(Transform.scale(scale, scale)));
            second.update(box(10, 30, 15, 15).withPose(Transform.scale(2 - scale, 2 - scale)));
            var stats = layout.resolve();
            assertEquals(4, stats.solved());
            assertEquals(total - 4, stats.cached());
            assertFalse(stats.rebuiltGraph());
        }
        long animated = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) assertEquals(total, layout.resolve().cached());
        long cached = System.nanoTime() - start;
        System.out.printf(Locale.ROOT, "visual-layout nodes=%d initial=%.3f ms animated=%.3f us/frame static=%.3f us/frame cache=%.2f%%%n",
                total, initial / 1e6, animated / 240e3, cached / 1e6, 100.0 * (total - 4) / total);
    }

    private static boolean has(AlignmentLayout.Node node, Issue issue) {
        return node.debug().diagnostics().stream().anyMatch(diagnostic -> diagnostic.issue() == issue);
    }

    private static AlignmentFrame box(double x, double y, double w, double h) {
        return AlignmentFrame.box(new Rect(x, y, w, h));
    }
}
