// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.dishanhai.sh_better_ui.client.music.MusicProvider;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot.*;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Opt-in adapter for the macOS Music app's public scripting dictionary. */
public final class MacMusicProvider implements MusicProvider {
    private static final String SCRIPT = """
            function run() {
                const app = Application('Music');
                if (!app.running()) return '{}';
                const status = String(app.playerState());
                const get = fn => { try { return fn(); } catch (error) { return null; } };
                const trim = value => value == null ? null : String(value).slice(0, 512);
                const track = get(() => app.currentTrack());
                return JSON.stringify({
                    status: status,
                    position: get(() => app.playerPosition()),
                    key: track && trim(get(() => track.persistentID())),
                    title: track && trim(get(() => track.name())),
                    artist: track && trim(get(() => track.artist())),
                    album: track && trim(get(() => track.album())),
                    duration: track && get(() => track.duration())
                });
            }
            """;
    private final LocalMediaCommand query = new LocalMediaCommand();

    @Override public String id() { return "macos-music"; }

    @Override public Poll poll() throws IOException {
        var reply = query.run(List.of("/usr/bin/osascript", "-l", "JavaScript", "-e", SCRIPT), 4000);
        if (reply.code() != 0) {
            if (reply.text().contains("-1743")) throw new Unsupported("Music Automation permission denied");
            throw new IOException("Music script failed");
        }
        try { return parse(reply.text(), System.nanoTime()); }
        catch (RuntimeException e) { throw new IOException("Malformed Music response", e); }
    }

    static Poll parse(String response, long now) {
        var object = JsonParser.parseString(response).getAsJsonObject();
        if (!object.has("status")) return new Poll(List.of(), null);
        String id = "com.apple.Music";
        var state = switch (string(object.get("status")).toLowerCase(Locale.ROOT)) {
            case "playing" -> PlaybackStatus.PLAYING;
            case "paused" -> PlaybackStatus.PAUSED;
            case "stopped" -> PlaybackStatus.STOPPED;
            case "fast forwarding", "rewinding" -> PlaybackStatus.BUFFERING;
            default -> PlaybackStatus.UNKNOWN;
        };
        String title = string(object.get("title"));
        String artist = string(object.get("artist"));
        String key = string(object.get("key"));
        Track track = title.isBlank() ? null : Track.of(key.isBlank() ? title + "\u0000" + artist : key,
                title, artist, string(object.get("album")));
        var position = millis(object.get("position"));
        var caps = EnumSet.noneOf(Capability.class);
        if (track != null) caps.add(Capability.METADATA);
        if (state != PlaybackStatus.UNKNOWN) caps.add(Capability.PLAYBACK_STATE);
        if (position.isPresent()) caps.add(Capability.TIMELINE);
        return new Poll(List.of(new Session(id, track, state, position, millis(object.get("duration")),
                OptionalDouble.empty(), now, null, caps)), id);
    }

    private static String string(JsonElement value) {
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static OptionalLong millis(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return OptionalLong.empty();
        double seconds = value.getAsDouble();
        return Double.isFinite(seconds) && seconds >= 0
                ? OptionalLong.of(Math.round(seconds * 1000)) : OptionalLong.empty();
    }

    @Override public void close() { query.close(); }
}
