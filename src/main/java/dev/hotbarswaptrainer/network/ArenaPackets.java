package dev.hotbarswaptrainer.network;

import dev.hotbarswaptrainer.arena.ArenaStatus;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

/** Separate bounded arena protocol. Training/recovery DTOs and their world binding are unchanged. */
public final class ArenaPackets {
    public static final Identifier REQUEST = new Identifier("hotbar-swap-trainer", "arena_request");
    public static final Identifier STATUS = new Identifier("hotbar-swap-trainer", "arena_status");
    public enum Action { STATUS, RESET, RETRY, EXIT, EXIT_COMMIT }
    private ArenaPackets() { }

    public static void register(BiConsumer<ServerPlayerEntity, Action> handler) {
        ServerSidePacketRegistry.INSTANCE.register(REQUEST, (context, buffer) -> {
            final Action action;
            try { action = readAction(buffer); }
            catch (RuntimeException malformed) { return; }
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            context.getTaskQueue().execute(() -> {
                if (player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player) handler.accept(player, action);
            });
        });
    }

    public static Action readAction(PacketByteBuf buffer) {
        if (buffer.readableBytes() != 2 || buffer.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported arena request");
        return Action.values()[buffer.readUnsignedByte()];
    }

    public static void writeAction(PacketByteBuf buffer, Action action) { buffer.writeByte(1); buffer.writeByte(action.ordinal()); }

    public static ArenaStatus readStatus(PacketByteBuf buffer) {
        if (buffer.readableBytes() != 5 || buffer.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported arena status");
        return new ArenaStatus(ArenaStatus.State.values()[buffer.readUnsignedByte()],
                ArenaStatus.Problem.values()[buffer.readUnsignedByte()], buffer.readUnsignedByte(), buffer.readBoolean());
    }

    public static void writeStatus(PacketByteBuf buffer, ArenaStatus status) {
        buffer.writeByte(1); buffer.writeByte(status.state.ordinal()); buffer.writeByte(status.problem.ordinal());
        buffer.writeByte(status.percent); buffer.writeBoolean(status.resetAllowed);
    }

    public static void send(ServerPlayerEntity player, ArenaStatus status) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        writeStatus(buffer, status);
        ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, STATUS, buffer);
    }
}
