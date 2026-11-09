package com.dishanhai.sh_better_ui.client.lyrics;

import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LocalLyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.LyricStyleConfig;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricWord;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 山海：静音歌词样例，供图库和设置页在断开播放器时使用。 */
public final class LyricDemo {
    private static final String TEXT = "sh_better_ui.lyrics.demo.";
    private static final long DURATION = 12_000;
    private static final TrackIdentity TRACK = TrackIdentity.of("sh_better_ui:lyric_demo", "", DURATION);

    private final LocalLyricFeed feed = new LocalLyricFeed("sh_better_ui:demo");
    private final FloatingLyricRenderer renderer = new FloatingLyricRenderer(feed);
    private final LyricTimeline timeline = new LyricTimeline(List.of(
            line(0, "first"), line(4000, "second"), line(8000, "third")));
    private long startedAt = Util.getMillis();
    private long pausedAt;
    private boolean playing = true;

    public LyricDemo() {
        renderer.setTextResolver(key -> Component.translatable(key).getString());
    }

    public FloatingLyricRenderer renderer(LyricStyleConfig style) {
        renderer.setStyle(style);
        return renderer;
    }

    public LyricFeed.Frame frame(boolean reducedMotion) {
        long position = reducedMotion ? 1800 : elapsed(Util.getMillis()) % DURATION;
        feed.publish(TRACK, timeline, position, playing && !reducedMotion);
        return feed.frame();
    }

    public boolean playing() { return playing; }

    public void toggle() {
        long now = Util.getMillis();
        if (playing) pausedAt = elapsed(now);
        else startedAt = now - pausedAt;
        playing = !playing;
    }

    private long elapsed(long now) {
        return playing ? now - startedAt : pausedAt;
    }

    private static LyricLine line(long start, String key) {
        var words = new ArrayList<LyricWord>(3);
        var text = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String part = Component.translatable(TEXT + key + "." + i).getString();
            text.append(part);
            words.add(new LyricWord(part, start + i * 1300, start + (i + 1) * 1300));
        }
        return new LyricLine(text.toString(), Component.translatable(TEXT + key + ".translation").getString(),
                start, start + 4000, words, "");
    }
}
