// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.Action;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.ServerMessage;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge optional PLAY channel for network live activities. */
public final class PlatformLiveActivityNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "live_activity_v" + LiveActivityWire.VERSION),
            () -> LiveActivityWire.CHANNEL_VERSION,
            NetworkRegistry.acceptMissingOr(LiveActivityWire.CHANNEL_VERSION),
            NetworkRegistry.acceptMissingOr(LiveActivityWire.CHANNEL_VERSION));

    private PlatformLiveActivityNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Clientbound.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeBytes(LiveActivityWire.encode(packet.message())))
                .decoder(buffer -> new Clientbound(NativeLiveActivityBuffers.readServer(buffer)))
                .consumerMainThread((packet, context) -> {
                    Connection connection = context.get().getNetworkManager();
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLiveActivityNetwork.receive(connection,
                            packet.message(), action -> reply(connection, action)));
                }).add();
        CHANNEL.messageBuilder(Action.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeBytes(LiveActivityWire.encode(packet)))
                .decoder(NativeLiveActivityBuffers::readAction)
                .consumerMainThread((packet, supplied) -> {
                    var context = supplied.get();
                    ServerPlayer sender = context.getSender();
                    if (sender != null) NativeLiveActivityServer.receive(sender, context.getNetworkManager(), packet);
                }).add();
        MinecraftForge.EVENT_BUS.addListener(PlatformLiveActivityNetwork::logout);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NativeLiveActivityServer.stop());
    }

    static Connection connection(ServerPlayer player) {
        return player.connection.connection;
    }

    static boolean supported(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }

    static void send(Connection connection, ServerMessage packet) {
        CHANNEL.sendTo(new Clientbound(packet), connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static void reply(Connection connection, Action packet) {
        if (connection.isConnected() && supported(connection)) {
            CHANNEL.sendTo(packet, connection, NetworkDirection.PLAY_TO_SERVER);
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NativeLiveActivityServer.logout(player);
    }

    private record Clientbound(ServerMessage message) {}
}
