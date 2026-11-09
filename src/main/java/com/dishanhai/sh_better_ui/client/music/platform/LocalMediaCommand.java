// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a single bounded desktop query at a time. No shell, listeners, or temp files. */
final class LocalMediaCommand implements AutoCloseable {
    private static final int MAX_REPLY = 48 * 1024;
    record Reply(int code, String text) {}
    private Process child;
    private boolean closed;

    Reply run(List<String> command, long timeoutMs) throws IOException {
        Process active = launch(command);
        try (var input = active.getInputStream()) {
            var out = new ByteArrayOutputStream();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (active.isAlive()) {
                int available = input.available();
                if (available > 0) out.write(input.readNBytes(Math.min(available, MAX_REPLY + 1 - out.size())));
                if (out.size() > MAX_REPLY || System.nanoTime() - deadline >= 0)
                    throw new IOException("Media command exceeded its output or time limit");
                active.waitFor(10, TimeUnit.MILLISECONDS);
            }
            out.write(input.readNBytes(MAX_REPLY + 1 - out.size()));
            if (out.size() > MAX_REPLY) throw new IOException("Oversized media response");
            return new Reply(active.exitValue(), out.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Media query interrupted", interrupted);
        } finally {
            synchronized (this) {
                if (child == active) child = null;
            }
            if (active.isAlive()) active.destroyForcibly();
        }
    }

    private synchronized Process launch(List<String> command) throws IOException {
        if (closed) throw new IOException("Media command closed");
        try {
            var builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().put("LC_ALL", "C");
            child = builder.start();
            return child;
        } catch (IOException failure) {
            throw new com.dishanhai.sh_better_ui.client.music.MusicProvider.Unsupported("Cannot launch platform media command");
        }
    }

    @Override public synchronized void close() {
        closed = true;
        if (child != null) child.destroyForcibly();
    }
}
