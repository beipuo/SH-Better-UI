// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Action;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.ServerMessage;
import net.minecraft.network.Connection;

import java.util.function.Consumer;

/** Referenced only inside the physical client branch of a clientbound handler. */
final class ClientLiveActivityNetwork {
    private ClientLiveActivityNetwork() {}

    static void receive(Connection connection, ServerMessage packet, Consumer<Action> reply) {
        ClientUiKernel.receiveLiveActivity(connection, packet, reply);
    }
}
