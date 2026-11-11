// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LiquidGlassFixtureTest {
    @Test
    void theSameFrameReplaysTheSameBackdropAndPointerAfterSeeking() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("catalog", "fixtures", "liquid-glass.json"))) {
            var fixture = LiquidGlassFixture.read(reader);
            assertEquals(GlassRegion.Shape.CONTINUOUS, fixture.surface().shape());
            var timeline = new UiTimeline(fixture.fps(), fixture.frameCount());
            timeline.play(1000);
            var at800 = fixture.sample(timeline.sample(1800).seconds() * 1000);
            assertEquals(195, at800.objectX());
            assertEquals(188, at800.objectY());
            assertEquals(.75f, at800.lightStrength());
            timeline.pause(1800);
            assertEquals(at800, fixture.sample(timeline.sample(3000).seconds() * 1000));
            timeline.seek(49);
            assertNotEquals(at800, fixture.sample(timeline.sample(3000).seconds() * 1000));
            timeline.seek(48);
            assertEquals(at800, fixture.sample(timeline.sample(4000).seconds() * 1000));
            timeline.seek(fixture.frameCount() - 1);
            assertEquals(0f, fixture.sample(timeline.sample(4000).seconds() * 1000).lightStrength());
        }
    }
}
