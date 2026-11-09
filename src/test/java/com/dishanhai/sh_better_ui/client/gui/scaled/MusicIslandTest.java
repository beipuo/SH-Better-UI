package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicIslandTest {
    @Test
    void waveDistinguishesBandsAndOffsetsColumns() {
        var bass = signal(1, 0, 0);
        var mid = signal(0, 1, 0);
        var high = signal(0, 0, 1);
        assertTrue(MusicIsland.level(bass, 0) > MusicIsland.level(bass, 6));
        assertTrue(MusicIsland.level(mid, 3) > MusicIsland.level(mid, 0));
        assertTrue(MusicIsland.level(high, 6) > MusicIsland.level(high, 0));

        var rhythm = signal(.58f, .67f, .51f);
        assertTrue(MusicIsland.level(rhythm, 2) != MusicIsland.level(rhythm, 4));
        for (int i = 0; i < 7; i++) {
            assertTrue(MusicIsland.level(rhythm, i) >= 0 && MusicIsland.level(rhythm, i) <= 1);
            assertEquals(0, MusicIsland.level(MusicRhythm.Frame.STILL, i));
        }
    }

    @Test
    void pauseConvergesWithoutClearingTheEnvelopeOnTheFirstFrame() {
        var rhythm = new MusicRhythm();
        rhythm.sample(AudioFrame.silence(0), 0, false);
        var playing = rhythm.sample(new AudioFrame(500, .36f, .4f, .2f, .24f, .2f, 0, 0), 500, false);
        var firstSilent = rhythm.sample(AudioFrame.silence(516), 516, false);
        var resting = rhythm.sample(AudioFrame.silence(3000), 3000, false);
        float first = MusicIsland.level(firstSilent, 3);
        assertTrue(first > 0 && first < MusicIsland.level(playing, 3));
        assertTrue(MusicIsland.level(resting, 3) < .001f);
    }

    @Test
    void outlineKeepsItsTopAndCenterAcrossTheMorph() {
        var compact = new Bounds(28, 5, 148, 26);
        var panel = new Bounds(0, 5, 204, 68);
        assertEquals(13f, MusicIsland.outline(compact, panel, 0).radius());
        assertEquals(10f, MusicIsland.outline(compact, panel, 1).radius());
        var halfway = MusicIsland.outline(compact, panel, .5f).rect();
        assertEquals(5f, halfway.getY());
        assertEquals(102f, halfway.getX() + halfway.getW() / 2);
        assertEquals(47f, halfway.getH());
    }

    private static MusicRhythm.Frame signal(float bass, float mid, float high) {
        return new MusicRhythm.Frame(bass, mid, high, .5f, .44f, .8f, 0, List.of());
    }
}
