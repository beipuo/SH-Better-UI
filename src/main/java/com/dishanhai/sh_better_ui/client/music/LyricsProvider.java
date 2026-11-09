// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;

/** Supply a local or explicitly authorized source; match the query before returning a timeline. */
@FunctionalInterface
public interface LyricsProvider {
    LyricTimeline load(TrackIdentity track) throws Exception;
}
