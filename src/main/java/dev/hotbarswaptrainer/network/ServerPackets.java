package dev.hotbarswaptrainer.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

/** Decode before queueing; never retain a pooled incoming buffer. */
public final class ServerPackets {
    private ServerPackets() { }

    public static void register(BiConsumer<ServerPlayerEntity, TrainerProtocol.Request> handler) {
        ServerSidePacketRegistry.INSTANCE.register(TrainerProtocol.REQUEST, (context, buf) -> {
            final TrainerProtocol.Request request;
            try {
                request = TrainerProtocol.readRequest(buf);
            } catch (RuntimeException malformed) {
                return;
            }
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            context.getTaskQueue().execute(() -> handler.accept(player, request));
        });
    }

    public static void send(ServerPlayerEntity player, TrainerProtocol.View view) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        TrainerProtocol.writeView(buf, view);
        ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, TrainerProtocol.VIEW, buf);
    }
}
