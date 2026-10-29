package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.network.PacketByteBuf;

import java.util.function.Consumer;

final class ClientPackets {
    private ClientPackets() { }

    static void register(Consumer<TrainerProtocol.View> consumer) {
        ClientSidePacketRegistry.INSTANCE.register(TrainerProtocol.VIEW, (context, buf) -> {
            final TrainerProtocol.View view;
            try {
                view = TrainerProtocol.readView(buf);
            } catch (RuntimeException malformed) {
                return;
            }
            context.getTaskQueue().execute(() -> consumer.accept(view));
        });
    }

    static void send(TrainerProtocol.Request request) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        TrainerProtocol.writeRequest(buf, request);
        ClientSidePacketRegistry.INSTANCE.sendToServer(TrainerProtocol.REQUEST, buf);
    }
}
