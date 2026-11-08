package com.dishanhai.sh_better_ui.client.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖 PCM 容器位宽、有效位、异常浮点及交错声道边界。 */
class PcmDecoderTest {
    @Test
    void signed16BitBoundariesPreserveOppositePhaseChannels() {
        PcmDecoder decoder = decoder(1, 2, 16, 16, 4);
        ByteBuffer samples = buffer(8).putShort((short) -32768).putShort((short) 32767)
                .putShort((short) 16384).putShort((short) -16384).flip();
        assertArrayEquals(new float[]{-1f, 32767f / 32768, 0.5f, -0.5f}, decoder.decode(samples, 2), 1e-7f);
        assertEquals(48000, decoder.sampleRate());
        assertEquals(4, decoder.blockAlign());
        assertEquals(2, decoder.channels());
    }

    @Test
    void signed24BitUsesSignExtension() {
        PcmDecoder decoder = decoder(1, 1, 24, 24, 3);
        ByteBuffer samples = ByteBuffer.wrap(new byte[]{0, 0, (byte) 128, -1, -1, 127, 0, 0, 64});
        assertArrayEquals(new float[]{-1f, 8388607f / 8388608, 0.5f}, decoder.decode(samples, 3), 1e-7f);
    }

    @Test
    void signed32BitUsesDoubleNormalization() {
        PcmDecoder decoder = decoder(1, 1, 32, 32, 4);
        ByteBuffer samples = buffer(12).putInt(Integer.MIN_VALUE).putInt(Integer.MAX_VALUE).putInt(1073741824).flip();
        assertArrayEquals(new float[]{-1f, 1f, 0.5f}, decoder.decode(samples, 3), 1e-7f);
    }

    @Test
    void extensibleValidBitsAreLeftAlignedAndDiscardPadding() {
        PcmDecoder decoder = decoder(0xFFFE, 1, 32, 20, 4);
        ByteBuffer samples = buffer(8).putInt(0x40000FFF).putInt(0x80000FFF).flip();
        assertArrayEquals(new float[]{0.5f, -1f}, decoder.decode(samples, 2), 1e-7f);
    }

    @Test
    void float32SanitizesNonfiniteAndClampsOvershoot() {
        PcmDecoder decoder = decoder(3, 1, 32, 32, 4);
        ByteBuffer samples = buffer(20).putFloat(Float.NaN).putFloat(Float.POSITIVE_INFINITY)
                .putFloat(-2f).putFloat(2f).putFloat(0.25f).flip();
        assertArrayEquals(new float[]{0, 0, -1, 1, 0.25f}, decoder.decode(samples, 5));
    }

    @Test
    void extensibleFloat64AndFramePaddingAreRespected() {
        ByteBuffer format = format(0xFFFE, 2, 64, 64, 24);
        format.putInt(24, 3);
        PcmDecoder decoder = PcmDecoder.fromFormat(format);
        ByteBuffer samples = buffer(48).putDouble(0.5).putDouble(0.25).putLong(-1)
                .putDouble(Double.NaN).putDouble(-0.5).putLong(-1).flip();
        assertArrayEquals(new float[]{0.5f, 0.25f, 0f, -0.5f}, decoder.decode(samples, 2));
    }

    @Test
    void callerBufferPositionAndOrderAreNotChanged() {
        PcmDecoder decoder = decoder(1, 1, 16, 16, 2);
        ByteBuffer samples = buffer(4).putShort((short) 7).putShort((short) 16384);
        samples.position(2).order(ByteOrder.BIG_ENDIAN);
        assertArrayEquals(new float[]{0.5f}, decoder.decode(samples, 1));
        assertEquals(2, samples.position());
        assertEquals(ByteOrder.BIG_ENDIAN, samples.order());
    }

    @Test
    void rejectsUnknownSubtypeInsteadOfMisreadingItAsPcm() {
        ByteBuffer format = format(0xFFFE, 1, 32, 24, 4);
        format.put(39, (byte) 0);
        assertThrows(IllegalArgumentException.class, () -> PcmDecoder.fromFormat(format));
        assertThrows(IllegalArgumentException.class, () -> decoder(6, 1, 16, 16, 2));
    }

    @Test
    void rejectsMalformedFormatsAndShortPackets() {
        assertThrows(IllegalArgumentException.class, () -> PcmDecoder.fromFormat(buffer(16)));
        assertThrows(IllegalArgumentException.class, () -> decoder(1, 2, 16, 16, 2));
        assertThrows(IllegalArgumentException.class, () -> decoder(0xFFFE, 1, 32, 33, 4));
        ByteBuffer truncated = format(0xFFFE, 1, 32, 24, 4);
        truncated.limit(30);
        assertThrows(IllegalArgumentException.class, () -> PcmDecoder.fromFormat(truncated));
        PcmDecoder decoder = decoder(1, 1, 16, 16, 2);
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer(1), 1));
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(buffer(1), -1));
        assertArrayEquals(new float[0], decoder.decode(buffer(0), 0));
    }

    private static PcmDecoder decoder(int tag, int channels, int bits, int validBits, int align) {
        return PcmDecoder.fromFormat(format(tag, channels, bits, validBits, align));
    }

    private static ByteBuffer format(int tag, int channels, int bits, int validBits, int align) {
        ByteBuffer result = buffer(40);
        result.putShort(0, (short) tag).putShort(2, (short) channels).putInt(4, 48000)
                .putInt(8, 48000 * align).putShort(12, (short) align).putShort(14, (short) bits)
                .putShort(16, (short) (tag == 0xFFFE ? 22 : 0)).putShort(18, (short) validBits)
                .putInt(24, 1).putInt(28, 0x00100000).putLong(32, 0x719B3800AA000080L);
        return result;
    }

    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
