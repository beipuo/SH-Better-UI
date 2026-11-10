// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.api.live.network.ServerLiveActivityAction;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Action;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

/** Connects the shared server authority to the verified native sender and transport. */
public final class NativeLiveActivityServer {
    private static final ServerLiveActivitySessions<ServerPlayer> SESSIONS = new ServerLiveActivitySessions<>(
            error -> ShBetterUi.LOGGER.warn("Server live activity action failed", error));

    private NativeLiveActivityServer() {}

    public static boolean supported(ServerPlayer player) {
        return negotiatedConnection(player) != null;
    }

    public static boolean publish(ServerPlayer player, NetworkLiveActivity activity,
                                  ServerLiveActivityAction<ServerPlayer> action) {
        Connection connection = negotiatedConnection(player);
        return connection != null && SESSIONS.publish(connection, activity, action,
                packet -> PlatformLiveActivityNetwork.send(connection, packet));
    }

    public static boolean remove(ServerPlayer player, UiId id) {
        Connection connection = negotiatedConnection(player);
        return connection != null && SESSIONS.remove(connection, id,
                packet -> PlatformLiveActivityNetwork.send(connection, packet));
    }

    public static boolean snapshot(ServerPlayer player) {
        Connection connection = negotiatedConnection(player);
        return connection != null && SESSIONS.snapshot(connection,
                packet -> PlatformLiveActivityNetwork.send(connection, packet));
    }

    static void receive(ServerPlayer sender, Connection connection, Action action) {
        if (negotiatedConnection(sender) == connection) {
            SESSIONS.receive(connection, sender, action, packet -> PlatformLiveActivityNetwork.send(connection, packet));
        }
    }

    static void logout(ServerPlayer player) {
        if (player.connection != null) SESSIONS.disconnect(PlatformLiveActivityNetwork.connection(player));
    }

    static void stop() {
        SESSIONS.clear();
    }

    private static Connection negotiatedConnection(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return null;
        if (!server.isSameThread()) throw new IllegalStateException("Server live activities require the server thread");
        if (player.connection == null || player.connection.player != player) return null;
        Connection connection = PlatformLiveActivityNetwork.connection(player);
        return connection != null && connection.isConnected() && PlatformLiveActivityNetwork.supported(connection)
                ? connection : null;
    }
}
