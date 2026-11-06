package dev.hotbarswaptrainer.network;

import dev.hotbarswaptrainer.arena.ArenaStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaPacketsTest {
    @Test public void allActionsAndStatusValuesRoundTrip() {
        for (ArenaPackets.Action action : ArenaPackets.Action.values()) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try { ArenaPackets.writeAction(buffer, action); assertEquals(action, ArenaPackets.readAction(buffer)); }
            finally { buffer.release(); }
        }
        for (ArenaStatus.State state : ArenaStatus.State.values()) for (ArenaStatus.Problem problem : ArenaStatus.Problem.values()) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                ArenaPackets.writeStatus(buffer, new ArenaStatus(state, problem, 93, true));
                ArenaStatus read = ArenaPackets.readStatus(buffer);
                assertEquals(state, read.state); assertEquals(problem, read.problem); assertEquals(93, read.percent); assertTrue(read.resetAllowed);
            } finally { buffer.release(); }
        }
    }

    @Test public void malformedUnsupportedAndTrailingMessagesAreRejected() {
        for (byte[] raw : new byte[][] {{}, {1, 0}, {2, 99}, {2, 0, 0}}) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(raw));
            try { ArenaPackets.readAction(buffer); fail("Accepted malformed request"); } catch (RuntimeException expected) { }
            finally { buffer.release(); }
        }
        for (byte[] raw : new byte[][] {{}, {2, 0, 0, 0, 0}, {1, 99, 0, 0, 0}, {1, 0, 99, 0, 0}, {1, 0, 0, 101, 0}, {1, 0, 0, 0, 0, 0}}) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(raw));
            try { ArenaPackets.readStatus(buffer); fail("Accepted malformed status"); } catch (RuntimeException expected) { }
            finally { buffer.release(); }
        }
    }
}
