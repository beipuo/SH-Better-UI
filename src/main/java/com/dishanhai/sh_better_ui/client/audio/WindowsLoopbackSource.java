package com.dishanhai.sh_better_ui.client.audio;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/** 山海：仅打开 Windows 默认播放端点的 WASAPI 回环，不枚举或打开输入端点。 */
final class WindowsLoopbackSource implements LoopbackSource {
    private static final Guid.GUID ENUMERATOR_CLASS = guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final Guid.GUID ENUMERATOR_IID = guid("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static final Guid.GUID AUDIO_CLIENT_IID = guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
    private static final Guid.GUID CAPTURE_CLIENT_IID = guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
    private static final int RENDER_ENDPOINT = 0;
    private static final int CONSOLE_ROLE = 0;
    private static final int SHARED_MODE = 0;
    private static final int LOOPBACK = 0x00020000;
    private static final int SILENT = 0x2;
    private static final int DATA_DISCONTINUITY = 0x1;

    private Pointer enumerator;
    private Pointer endpoint;
    private Pointer audioClient;
    private Pointer captureClient;
    private PcmDecoder decoder;
    private boolean comInitialized;
    private boolean started;

    @Override
    public void open() {
        if (!Platform.isWindows()) throw new UnsupportedOperationException("WASAPI");
        if (comInitialized) throw new IllegalStateException("WASAPI.AlreadyOpen");
        try {
            check("CoInitializeEx", Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).intValue());
            comInitialized = true;
            PointerByReference result = new PointerByReference();
            check("CoCreateInstance", Ole32.INSTANCE.CoCreateInstance(
                    ENUMERATOR_CLASS, null, 1, ENUMERATOR_IID, result).intValue());
            enumerator = result.getValue();
            // IMMDeviceEnumerator::GetDefaultAudioEndpoint(eRender, eConsole)。
            call(enumerator, 4, RENDER_ENDPOINT, CONSOLE_ROLE, result);
            endpoint = result.getValue();
            call(endpoint, 3, AUDIO_CLIENT_IID, 1, null, result);
            audioClient = result.getValue();
            PointerByReference mixFormat = new PointerByReference();
            call(audioClient, 8, mixFormat);
            Pointer format = mixFormat.getValue();
            try {
                int size = 18 + Short.toUnsignedInt(format.getShort(16));
                decoder = PcmDecoder.fromFormat(format.getByteBuffer(0, size));
                // 共享模式 + LOOPBACK：这里不提供输入端点或麦克风回退路径。
                call(audioClient, 3, SHARED_MODE, LOOPBACK, 1_000_000L, 0L, format, null);
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(format);
            }
            call(audioClient, 14, CAPTURE_CLIENT_IID, result);
            captureClient = result.getValue();
            call(audioClient, 10);
            started = true;
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    @Override
    public Block poll() {
        if (!started) throw new IllegalStateException("WASAPI.Closed");
        IntByReference packet = new IntByReference();
        call(captureClient, 5, packet);
        if (packet.getValue() == 0) return null;
        PointerByReference buffer = new PointerByReference();
        IntByReference frames = new IntByReference();
        IntByReference flags = new IntByReference();
        call(captureClient, 3, buffer, frames, flags, null, null);
        int count = frames.getValue();
        try {
            if (count == 0) return null;
            if (count < 0) throw new IllegalStateException("WASAPI.FrameCount");
            float[] samples = (flags.getValue() & SILENT) != 0 ? new float[Math.multiplyExact(count, decoder.channels())]
                    : decoder.decode(buffer.getValue().getByteBuffer(0, (long) count * decoder.blockAlign()), count);
            return new Block(samples, decoder.channels(), decoder.sampleRate(),
                    (flags.getValue() & DATA_DISCONTINUITY) != 0);
        } finally {
            call(captureClient, 4, count);
        }
    }

    @Override
    public void close() {
        try {
            if (started) {
                started = false;
                // 端点移除后 Stop 可失败，仍须释放所有 COM 引用。
                invoke(audioClient, 11);
            }
        } finally {
            try {
                release(captureClient);
            } finally {
                captureClient = null;
                try {
                    release(audioClient);
                } finally {
                    audioClient = null;
                    try {
                        release(endpoint);
                    } finally {
                        endpoint = null;
                        try {
                            release(enumerator);
                        } finally {
                            enumerator = null;
                            decoder = null;
                            if (comInitialized) {
                                comInitialized = false;
                                Ole32.INSTANCE.CoUninitialize();
                            }
                        }
                    }
                }
            }
        }
    }

    private static Guid.GUID guid(String value) { return new Guid.GUID(value); }

    private static void release(Pointer object) {
        if (object != null) invoke(object, 2);
    }

    private static void call(Pointer object, int slot, Object... parameters) {
        check("vtable[" + slot + "]", invoke(object, slot, parameters));
    }

    private static int invoke(Pointer object, int slot, Object... parameters) {
        Object[] arguments = new Object[parameters.length + 1];
        arguments[0] = object;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        Pointer function = object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);
        return Function.getFunction(function, Function.ALT_CONVENTION).invokeInt(arguments);
    }

    private static void check(String operation, int result) {
        if (result < 0) throw new IllegalStateException("WASAPI." + operation + ":0x" + Integer.toHexString(result));
    }
}
