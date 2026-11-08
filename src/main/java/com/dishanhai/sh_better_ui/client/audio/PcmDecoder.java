package com.dishanhai.sh_better_ui.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 山海：WASAPI 混音格式解析与交错声道转换，不依赖音频设备或 Minecraft。 */
final class PcmDecoder {
    private final int channels;
    private final int sampleRate;
    private final int blockAlign;
    private final int bits;
    private final int validBits;
    private final boolean floatingPoint;

    private PcmDecoder(int channels, int sampleRate, int blockAlign, int bits,
                       int validBits, boolean floatingPoint) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.blockAlign = blockAlign;
        this.bits = bits;
        this.validBits = validBits;
        this.floatingPoint = floatingPoint;
    }

    static PcmDecoder fromFormat(ByteBuffer format) {
        ByteBuffer data = format.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (data.remaining() < 18) throw new IllegalArgumentException("WAVEFORMATEX");
        int tag = Short.toUnsignedInt(data.getShort(0));
        int channels = Short.toUnsignedInt(data.getShort(2));
        int sampleRate = data.getInt(4);
        int align = Short.toUnsignedInt(data.getShort(12));
        int bits = Short.toUnsignedInt(data.getShort(14));
        int extra = Short.toUnsignedInt(data.getShort(16));
        int valid = bits;
        if (tag == 0xFFFE) {
            if (extra < 22 || data.remaining() < 18 + extra)
                throw new IllegalArgumentException("WAVEFORMATEXTENSIBLE");
            valid = Short.toUnsignedInt(data.getShort(18));
            // KSDATAFORMAT_SUBTYPE_PCM / IEEE_FLOAT 的完整 GUID 尾部。
            if (data.getInt(28) != 0x00100000 || data.getLong(32) != 0x719B3800AA000080L)
                throw new IllegalArgumentException("WAVEFORMATEXTENSIBLE.SubFormat");
            tag = data.getInt(24);
        }
        boolean floating = tag == 3;
        if ((!floating && tag != 1) || channels < 1 || sampleRate < 1 ||
                (floating ? bits != 32 && bits != 64 : bits != 16 && bits != 24 && bits != 32) ||
                valid < 1 || valid > bits || (floating && valid != bits) ||
                align < channels * (bits / 8))
            throw new IllegalArgumentException("WAVEFORMATEX.Format");
        return new PcmDecoder(channels, sampleRate, align, bits, valid, floating);
    }

    int sampleRate() { return sampleRate; }
    int blockAlign() { return blockAlign; }
    int channels() { return channels; }

    float[] decode(ByteBuffer samples, int frames) {
        ByteBuffer data = samples.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (frames < 0 || (long) frames * blockAlign > data.remaining())
            throw new IllegalArgumentException("WASAPI.BufferLength");
        float[] decoded = new float[frames * channels];
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                int offset = frame * blockAlign + channel * (bits / 8);
                double value;
                if (floatingPoint) {
                    value = bits == 32 ? data.getFloat(offset) : data.getDouble(offset);
                    if (!Double.isFinite(value)) value = 0;
                } else {
                    int raw = switch (bits) {
                        case 16 -> data.getShort(offset);
                        case 24 -> (data.get(offset) & 255) | ((data.get(offset + 1) & 255) << 8)
                                | (data.get(offset + 2) << 16);
                        default -> data.getInt(offset);
                    };
                    // EXTENSIBLE 有效位左对齐；忽略容器内无效的低位。
                    raw >>= bits - validBits;
                    value = raw / Math.scalb(1.0, validBits - 1);
                }
                decoded[frame * channels + channel] = (float) Math.max(-1.0, Math.min(1.0, value));
            }
        }
        return decoded;
    }
}
