// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.NativeLiveActivityServer;
import net.minecraft.server.level.ServerPlayer;

/** Optional server-to-client live activities. Call from the server game thread for a connected player. */
public final class ServerLiveActivities {
    private ServerLiveActivities() {}

    public static boolean supported(ServerPlayer player) {
        return NativeLiveActivityServer.supported(player);
    }

    /** Publishes or replaces a read-only activity identified by its namespaced ID. */
    public static boolean publish(ServerPlayer player, NetworkLiveActivity activity) {
        return publish(player, activity, null);
    }

    /** Publishes or replaces an activity with its matching action binding. */
    public static boolean publish(ServerPlayer player, NetworkLiveActivity activity,
                                  ServerLiveActivityAction<ServerPlayer> action) {
        return NativeLiveActivityServer.publish(player, activity, action);
    }

    public static boolean remove(ServerPlayer player, UiId id) {
        return NativeLiveActivityServer.remove(player, id);
    }

    /** Resends a complete snapshot of an already published session. */
    public static boolean snapshot(ServerPlayer player) {
        return NativeLiveActivityServer.snapshot(player);
    }
}
