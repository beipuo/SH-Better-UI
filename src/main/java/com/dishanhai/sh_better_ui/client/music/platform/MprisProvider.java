// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import com.dishanhai.sh_better_ui.client.music.MusicProvider;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** MPRIS via the local D-Bus playerctl client. No shell or remote cover URLs. */
public final class MprisProvider implements MusicProvider {
    private static final String SEPARATOR = "\u001f";
    private static final String FORMAT = String.join(SEPARATOR, "{{playerName}}", "{{mpris:trackid}}",
            "{{trunc(title, 512)}}", "{{trunc(artist, 512)}}", "{{trunc(album, 512)}}",
            "{{status}}", "{{position}}", "{{mpris:length}}");
    private final String command;
    private final LocalMediaCommand query = new LocalMediaCommand();

    public MprisProvider(String command) { this.command = command; }
    @Override public String id() { return "linux-mpris"; }

    @Override public Poll poll() throws IOException {
        var reply = query.run(List.of(command, "--all-players", "metadata", "--format", FORMAT), 2000);
        if (reply.code() == 1 && (reply.text().isBlank() || reply.text().contains("No players found")
                || reply.text().contains("No player could handle this command"))) return new Poll(List.of(), null);
        if (reply.code() != 0) throw new IOException("MPRIS command failed");
        return parse(reply.text(), System.nanoTime());
    }

    static Poll parse(String response, long sampleTime) {
        var sessions = new ArrayList<Session>();
        var ids = new HashSet<String>();
        for (String line : response.split("\\R")) {
            if (sessions.size() == 8) break;
            String[] f = line.split(SEPARATOR, -1);
            if (f.length != 8 || f[0].isBlank() || !ids.add(f[0])) continue;
            var caps = EnumSet.noneOf(Capability.class);
            Track track = null;
            if (!f[2].isBlank()) {
                track = Track.of(f[1].isBlank() ? f[2] + "\u0000" + f[3] : f[1], f[2], f[3], f[4]);
                caps.add(Capability.METADATA);
            }
            var status = switch (f[5]) {
                case "Playing" -> PlaybackStatus.PLAYING;
                case "Paused" -> PlaybackStatus.PAUSED;
                case "Stopped" -> PlaybackStatus.STOPPED;
                default -> PlaybackStatus.UNKNOWN;
            };
            if (status != PlaybackStatus.UNKNOWN) caps.add(Capability.PLAYBACK_STATE);
            var position = millis(f[6]);
            if (position.isPresent()) caps.add(Capability.TIMELINE);
            sessions.add(new Session(f[0], track, status, position, millis(f[7]), OptionalDouble.empty(),
                    sampleTime, null, caps));
        }
        return new Poll(sessions, sessions.isEmpty() ? null : sessions.get(0).id());
    }

    private static OptionalLong millis(String value) {
        try {
            long micros = Long.parseLong(value.strip());
            if (micros >= 0) return OptionalLong.of(micros / 1000);
        } catch (NumberFormatException ignored) { /* Player does not report the field. */ }
        return OptionalLong.empty();
    }

    @Override public void close() { query.close(); }
}
