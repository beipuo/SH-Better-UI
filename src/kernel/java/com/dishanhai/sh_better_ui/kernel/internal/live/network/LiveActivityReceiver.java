// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.ServerMessage;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Snapshot;

import java.util.UUID;

/** Orders packets for one client connection. A later snapshot can recover a gap. */
public final class LiveActivityReceiver {
    private UUID session;
    private long sequence;

    public boolean accept(ServerMessage message) {
        if (session == null) {
            if (!(message instanceof Snapshot)) return false;
            session = message.session();
        } else if (!session.equals(message.session()) || message.sequence() <= sequence) {
            return false;
        } else if (!(message instanceof Snapshot) && message.sequence() != sequence + 1) {
            return false;
        }
        sequence = message.sequence();
        return true;
    }

    public UUID session() {
        return session;
    }

    public void reset() {
        session = null;
        sequence = 0;
    }
}
