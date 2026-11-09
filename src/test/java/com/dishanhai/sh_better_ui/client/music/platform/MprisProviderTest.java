// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot.PlaybackStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MprisProviderTest {
    @Test void convertsMicrosecondsAndKeepsUnknownFieldsAbsent() {
        var sample = MprisProvider.parse(String.join("\u001f", "desktop", "/track/1", "歌曲", "歌手", "",
                "Playing", "1250000", "92000000") + "\n" + String.join("\u001f",
                "browser", "", "", "", "", "Paused", "", ""), 42);
        assertEquals(2, sample.sessions().size());
        var first = sample.sessions().get(0);
        assertEquals("/track/1", first.track().key());
        assertEquals(1250, first.positionMs().orElseThrow());
        assertEquals(92000, first.durationMs().orElseThrow());
        assertEquals("歌曲", first.track().title().orElseThrow());
        assertEquals(PlaybackStatus.PAUSED, sample.sessions().get(1).status());
        assertTrue(sample.sessions().get(1).positionMs().isEmpty());
        assertNull(sample.sessions().get(1).track());
    }
}
