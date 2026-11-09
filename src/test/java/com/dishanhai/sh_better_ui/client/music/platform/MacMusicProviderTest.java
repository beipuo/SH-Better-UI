// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot.PlaybackStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MacMusicProviderTest {
    @Test void recognizesStoppedApplicationAndPartialMusicSession() {
        assertTrue(MacMusicProvider.parse("{}", 100).sessions().isEmpty());
        var partial = MacMusicProvider.parse("{\"status\":\"paused\",\"title\":null,\"position\":null}", 100)
                .sessions().get(0);
        assertNull(partial.track());
        assertTrue(partial.positionMs().isEmpty());
        assertEquals(PlaybackStatus.PAUSED, partial.status());
        var playing = MacMusicProvider.parse("""
                {"status":"playing","title":"曲目","key":"00001","position":1.25,"duration":9.0}
                """, 100).sessions().get(0);
        assertEquals("00001", playing.track().key());
        assertEquals(1250, playing.positionMs().orElseThrow());
        assertEquals(9000, playing.durationMs().orElseThrow());
        assertEquals(100, playing.sampledAtNanos());
    }
}
