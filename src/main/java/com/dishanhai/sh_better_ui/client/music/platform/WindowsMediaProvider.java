// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music.platform;

import com.dishanhai.sh_better_ui.client.music.MusicProvider;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot.*;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/** Reads Windows system media sessions on the media worker using Minecraft's JNA. */
public final class WindowsMediaProvider implements MusicProvider {
    private static final String MANAGER = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final GUID MANAGER_STATICS = new GUID("2050C4EE-11A0-57DE-AED7-C97C70338245");
    private static final GUID ASYNC_INFO = new GUID("00000036-0000-0000-C000-000000000046");
    private static final GUID RANDOM_ACCESS_STREAM = new GUID("905A0FE1-BC53-11DF-8C49-001E4FC686DA");
    private static final GUID DATA_READER_FACTORY = new GUID("D7527847-57DA-4E15-914C-06806699A098");
    private static final long WINDOWS_EPOCH_MS = 11_644_473_600_000L;
    private static final int MAX_SESSIONS = 8;
    private static final int COVER_EDGE = 240;
    private static final int MAX_COVER_INPUT = 16 * 1024 * 1024;
    private final boolean artworkEnabled;
    private final HashMap<String, CachedCover> covers = new HashMap<>();
    private volatile boolean closed;

    private record CachedCover(String track, long expiresAt, Artwork artwork) {}

    public WindowsMediaProvider() { this(true); }

    /** @deprecated Use {@link #WindowsMediaProvider()} for artwork-enabled sessions. */
    @Deprecated
    public WindowsMediaProvider(Path executable) { this(); }

    /**
     * @param executable legacy bridge path retained for binary compatibility
     * @param artworkEnabled whether to read session artwork
     * @deprecated Windows sessions use JNA. This overload preserves the artwork setting for existing callers.
     */
    @Deprecated
    public WindowsMediaProvider(Path executable, boolean artworkEnabled) { this(artworkEnabled); }

    private WindowsMediaProvider(boolean artworkEnabled) { this.artworkEnabled = artworkEnabled; }

    @Override public String id() { return "windows-smtc"; }
    @Override public void close() { closed = true; }

    @Override
    public Poll poll() throws IOException {
        checkRunning();
        var api = WinRt.INSTANCE;
        check(api.RoInitialize(1), "RoInitialize"); // RO_INIT_MULTITHREADED
        try (var statics = activationFactory(MANAGER, MANAGER_STATICS);
             var request = statics.object(6); // IGlobalSystemMediaTransportControlsSessionManagerStatics.RequestAsync
             var manager = result(request, 8_000)) {
            return readSessions(manager);
        } finally {
            api.RoUninitialize();
        }
    }

    private Poll readSessions(ComRef manager) throws IOException {
        var samples = new ArrayList<Session>();
        var seen = new HashSet<String>();
        String preferred = "";
        // IGlobalSystemMediaTransportControlsSessionManager.GetCurrentSession / GetSessions
        try (var current = manager.object(6)) {
            if (current.present()) preferred = readSession(current, seen, samples);
        } catch (NativeFailure failure) {
            // The preferred session can disappear before the session list is read.
        }
        try (var sessions = manager.object(7)) {
            int size = sessions.integer(7); // IVectorView.Size
            for (int i = 0; i < size && samples.size() < MAX_SESSIONS; i++) {
                checkRunning();
                try (var session = sessions.object(6, i)) { // IVectorView.GetAt
                    readSession(session, seen, samples);
                } catch (NativeFailure failure) {
                    // The system can remove a session while it is being read.
                }
            }
        }
        covers.keySet().retainAll(seen);
        return new Poll(samples, preferred);
    }

    private String readSession(ComRef session, HashSet<String> seen, ArrayList<Session> samples) throws IOException {
        // IGlobalSystemMediaTransportControlsSession.SourceAppUserModelId
        String id = session.string(6);
        if (id.isBlank() || seen.contains(id)) return id;
        try (var playback = session.object(9); var timeline = session.object(8)) {
            var status = switch (playback.integer(7)) { // PlaybackStatus
                case 0, 3 -> PlaybackStatus.STOPPED;
                case 2 -> PlaybackStatus.BUFFERING;
                case 4 -> PlaybackStatus.PLAYING;
                case 5 -> PlaybackStatus.PAUSED;
                default -> PlaybackStatus.UNKNOWN;
            };
            OptionalDouble rate = OptionalDouble.empty();
            try (var reference = playback.object(10)) { // PlaybackRate (IReference<Double>)
                if (reference.present()) {
                    var value = new DoubleByReference();
                    reference.call(6, value);
                    double speed = value.getValue();
                    if (speed >= 0 && speed <= 16) rate = OptionalDouble.of(speed);
                }
            }
            long start = timeline.longValue(6);
            long duration = (timeline.longValue(7) - start) / 10_000;
            long rawPosition = timeline.longValue(10);
            long updatedAt = timeline.longValue(11);
            long position = (rawPosition - start) / 10_000;
            long sampledAt = System.nanoTime();
            if (updatedAt <= 0 || rawPosition < start || (rawPosition == 0 && duration <= 0)) position = -1;
            if (position >= 0 && status == PlaybackStatus.PLAYING) {
                long elapsed = System.currentTimeMillis() - (updatedAt / 10_000 - WINDOWS_EPOCH_MS);
                position += (long) (Math.min(86_400_000L, Math.max(0, elapsed)) * rate.orElse(1));
                if (duration > 0) position = Math.min(position, duration);
            }

            Track track = null;
            Artwork artwork = null;
            try (var request = session.object(7); var media = result(request, 650)) {
                if (media.present()) {
                    String title = media.string(6);
                    String artist = media.string(9);
                    if (!title.isBlank()) {
                        track = Track.of(trackKey(title, artist), title, artist, media.string(10));
                        if (artworkEnabled) artwork = cover(id, track.key(), media);
                    }
                }
            } catch (NativeFailure | RequestTimeout unavailable) {
                // Playback and timeline still describe the session.
            }
            if (track == null) covers.remove(id);
            var capabilities = EnumSet.noneOf(Capability.class);
            if (track != null) capabilities.add(Capability.METADATA);
            if (status != PlaybackStatus.UNKNOWN) capabilities.add(Capability.PLAYBACK_STATE);
            if (position >= 0) capabilities.add(Capability.TIMELINE);
            if (artwork != null) capabilities.add(Capability.ARTWORK);
            samples.add(new Session(id, track, status, OptionalLong.of(position), OptionalLong.of(duration), rate,
                    sampledAt, artwork, capabilities));
            seen.add(id);
        } catch (NativeFailure unavailable) {
            // A player may close while its session is being read.
        }
        return id;
    }

    private static String trackKey(String title, String artist) {
        var base64 = Base64.getEncoder();
        return base64.encodeToString(title.getBytes(StandardCharsets.UTF_8)) + ":"
                + base64.encodeToString(artist.getBytes(StandardCharsets.UTF_8));
    }

    private Artwork cover(String id, String key, ComRef media) throws InterruptedIOException {
        long now = System.nanoTime();
        var cached = covers.get(id);
        if (cached != null && cached.track().equals(key) && now - cached.expiresAt() < 0) return cached.artwork();
        Artwork artwork = null;
        try (var thumbnail = media.object(15)) { // IGlobalSystemMediaTransportControlsSessionMediaProperties.Thumbnail
            if (thumbnail.present()) artwork = readCover(thumbnail);
        } catch (InterruptedIOException interrupted) {
            throw interrupted;
        } catch (IOException unavailable) {
            // The player may publish a missing, unsupported or unreadable image.
        }
        covers.put(id, new CachedCover(key, now + TimeUnit.SECONDS.toNanos(15), artwork));
        return artwork;
    }

    private Artwork readCover(ComRef thumbnail) throws IOException {
        byte[] bytes;
        try (var open = thumbnail.object(6); var stream = result(open, 500);
             var randomAccess = stream.object(0, RANDOM_ACCESS_STREAM)) {
            long length = randomAccess.longValue(6); // IRandomAccessStream.Size
            if (length <= 0 || length > MAX_COVER_INPUT) return null;
            try (var input = randomAccess.object(8, 0L); // GetInputStreamAt
                 var factory = activationFactory("Windows.Storage.Streams.DataReader", DATA_READER_FACTORY);
                 var reader = factory.object(6, input.pointer);
                 var load = reader.object(29, (int) length)) { // IDataReader.LoadAsync
                await(load, 500);
                if (load.integer(8) != length) return null; // IAsyncOperation<UInt32>.GetResults
                bytes = new byte[(int) length];
                reader.call(14, bytes.length, bytes); // IDataReader.ReadBytes
            }
        }
        return resizeCover(bytes);
    }

    private static Artwork resizeCover(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                int longest = Math.max(width, height);
                if (width < 1 || height < 1 || longest > 4096) return null;
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, longest / COVER_EDGE);
                params.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, params);
                double scale = Math.min(1, (double) COVER_EDGE / longest);
                var resized = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(image, 0, 0, resized.getWidth(), resized.getHeight(), null);
                } finally {
                    graphics.dispose();
                }
                var png = new ByteArrayOutputStream();
                ImageIO.write(resized, "png", png);
                return png.size() > 0 && png.size() <= Artwork.MAX_BYTES ? new Artwork(png.toByteArray()) : null;
            } finally {
                reader.dispose();
            }
        }
    }

    private static ComRef activationFactory(String name, GUID iid) throws IOException {
        var api = WinRt.INSTANCE;
        var hstring = new PointerByReference();
        check(api.WindowsCreateString(new WString(name), name.length(), hstring), "WindowsCreateString");
        try {
            var result = new PointerByReference();
            int hr = api.RoGetActivationFactory(hstring.getValue(), iid, result);
            if (hr == 0x80040154 && MANAGER.equals(name)) throw new Unsupported("Windows version lacks GSMTC");
            check(hr, name);
            return new ComRef(result.getValue());
        } finally {
            api.WindowsDeleteString(hstring.getValue());
        }
    }

    private ComRef result(ComRef operation, long timeoutMs) throws IOException {
        await(operation, timeoutMs);
        return operation.object(8); // IAsyncOperation<T>.GetResults
    }

    private void await(ComRef operation, long timeoutMs) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try (var info = operation.object(0, ASYNC_INFO)) {
            // IAsyncInfo.Status: Started = 0, Completed = 1, Canceled = 2, Error = 3.
            int status;
            while ((status = info.integer(7)) == 0) {
                try {
                    checkRunning();
                    if (System.nanoTime() - deadline >= 0) {
                        info.invoke(9); // IAsyncInfo.Cancel
                        throw new RequestTimeout();
                    }
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    info.invoke(9); // IAsyncInfo.Cancel
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Windows media worker interrupted");
                } catch (InterruptedIOException stopped) {
                    info.invoke(9);
                    throw stopped;
                }
            }
            if (status != 1) throw new NativeFailure("IAsyncInfo", info.integer(8));
        }
    }

    private void checkRunning() throws InterruptedIOException {
        if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Windows media worker stopped");
    }

    // ABI slot order follows the Windows SDK interfaces. Every returned COM interface owns one reference.
    private record ComRef(Pointer pointer) implements AutoCloseable {
        boolean present() { return pointer != null; }

        ComRef object(int slot, Object... args) throws NativeFailure {
            return new ComRef(pointerResult(slot, args));
        }

        String string(int slot) throws NativeFailure {
            Pointer hstring = pointerResult(slot);
            if (hstring == null) return "";
            var api = WinRt.INSTANCE;
            try {
                var length = new IntByReference();
                Pointer data = api.WindowsGetStringRawBuffer(hstring, length);
                return new String(data.getByteArray(0, Math.min(512, length.getValue()) * 2), StandardCharsets.UTF_16LE);
            } finally {
                api.WindowsDeleteString(hstring);
            }
        }

        private Pointer pointerResult(int slot, Object... args) throws NativeFailure {
            var result = new PointerByReference();
            var parameters = Arrays.copyOf(args, args.length + 1);
            parameters[args.length] = result;
            call(slot, parameters);
            return result.getValue();
        }

        int integer(int slot) throws NativeFailure {
            var result = new IntByReference();
            call(slot, result);
            return result.getValue();
        }

        long longValue(int slot) throws NativeFailure {
            var result = new LongByReference();
            call(slot, result);
            return result.getValue();
        }

        void call(int slot, Object... args) throws NativeFailure {
            if (!present()) throw new NativeFailure("Missing COM interface", 0x80004003);
            check(invoke(slot, args), "vtable[" + slot + "]");
        }

        int invoke(int slot, Object... args) {
            Object[] parameters = new Object[args.length + 1];
            parameters[0] = pointer;
            System.arraycopy(args, 0, parameters, 1, args.length);
            Pointer function = pointer.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);
            return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(parameters);
        }

        @Override public void close() {
            if (present()) invoke(2); // IUnknown.Release
        }
    }

    private static void check(int hr, String operation) throws NativeFailure {
        if (hr < 0) throw new NativeFailure(operation, hr);
    }

    private static final class NativeFailure extends IOException {
        NativeFailure(String operation, int hr) {
            super(operation + ": 0x" + Integer.toHexString(hr));
        }
    }

    private static final class RequestTimeout extends IOException {
        RequestTimeout() { super("Windows media request timed out"); }
    }

    private interface WinRt extends StdCallLibrary {
        WinRt INSTANCE = Native.load("combase", WinRt.class);
        int RoInitialize(int type);
        void RoUninitialize();
        int RoGetActivationFactory(Pointer className, GUID iid, PointerByReference result);
        int WindowsCreateString(WString source, int length, PointerByReference result);
        Pointer WindowsGetStringRawBuffer(Pointer value, IntByReference length);
        int WindowsDeleteString(Pointer value);
    }
}
