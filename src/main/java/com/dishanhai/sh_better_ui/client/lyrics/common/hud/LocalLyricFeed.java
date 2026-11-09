package com.dishanhai.sh_better_ui.client.lyrics.common.hud;

import com.dishanhai.sh_better_ui.client.lyrics.core.capability.Capability;
import com.dishanhai.sh_better_ui.client.lyrics.core.capability.CapabilitySet;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.PlayerSnapshot;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;

import java.util.Objects;

/** 山海：把宿主已有的播放进度与时间轴发布给歌词渲染器。 */
public final class LocalLyricFeed implements LyricFeed {
    private static final CapabilitySet CAPABILITIES = CapabilitySet.of(Capability.PLAYBACK_POSITION);

    private final String id;
    private volatile Frame current;
    private volatile boolean enabled = true;

    public LocalLyricFeed(String id) {
        this.id = Objects.requireNonNull(id);
        clear();
    }

    /** 时间轴可跨帧复用。换曲、定位或暂停时发布最新播放状态。 */
    public synchronized void publish(TrackIdentity track, LyricTimeline timeline, long positionMs, boolean playing) {
        Objects.requireNonNull(track);
        Objects.requireNonNull(timeline);
        var previous = current;
        long revision = previous.timeline() == timeline ? previous.signature() : previous.signature() + 1;
        var snapshot = new PlayerSnapshot(id, track, positionMs, playing, 0, System.nanoTime(), CAPABILITIES);
        current = new Frame(!timeline.isEmpty(), snapshot, timeline, revision);
    }

    public synchronized void clear() {
        long revision = current == null ? 0 : current.signature() + 1;
        current = new Frame(false, new PlayerSnapshot(id, null, 0, false, 0, 0, CAPABILITIES),
                LyricTimeline.empty(), revision);
    }

    @Override public String id() { return id; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean value) { enabled = value; }
    @Override public boolean isAlive() { return frame().alive(); }
    @Override public PlayerSnapshot pollSnapshot() { return current.snapshot(); }
    @Override public LyricTimeline lyric() { return current.timeline(); }
    @Override public long lyricSignature() { return current.signature(); }

    @Override public Frame frame() {
        var frame = current;
        return enabled ? frame : new Frame(false, frame.snapshot(), frame.timeline(), frame.signature());
    }
}
