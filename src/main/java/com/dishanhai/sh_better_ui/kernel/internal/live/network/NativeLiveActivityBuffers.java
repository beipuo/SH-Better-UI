// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Action;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.ServerMessage;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;

final class NativeLiveActivityBuffers {
    private NativeLiveActivityBuffers() {}

    static ServerMessage readServer(FriendlyByteBuf buffer) {
        try {
            return LiveActivityWire.decodeServer(readBounded(buffer, LiveActivityWire.MAX_SERVER_BYTES));
        } catch (IOException invalid) {
            throw new DecoderException(invalid);
        }
    }

    static Action readAction(FriendlyByteBuf buffer) {
        try {
            return LiveActivityWire.decodeAction(readBounded(buffer, LiveActivityWire.MAX_ACTION_BYTES));
        } catch (IOException invalid) {
            throw new DecoderException(invalid);
        }
    }

    private static byte[] readBounded(FriendlyByteBuf buffer, int maximum) {
        int length = buffer.readableBytes();
        if (length > maximum) throw new DecoderException("Live activity packet exceeds payload limit");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }
}
