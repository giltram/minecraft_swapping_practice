package dev.hotbarswaptrainer.network;

import dev.hotbarswaptrainer.profile.StackValue;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.TrainingMode;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

public class TrainerProtocolTest {
    @Test
    public void viewRoundTripPreservesIdentityInventoryAndCounters() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:long_fire_resistance");
        InventoryLayout layout = InventoryLayout.empty().with(9,
                StackValue.of("minecraft:potion", 1, tag));
        TrainerProtocol.View original = new TrainerProtocol.View.Builder()
                .session(UUID.randomUUID()).task(UUID.randomUUID()).revision(72).phase("PREPARING")
                .message("Ready when Swap Hands is released").profile("potions").mode(TrainingMode.FULL_INVENTORY)
                .progress(4, 25).countdown(40).elapsed(123456789L).counts(7, 6, 3, 1, 2)
                .layouts(layout, layout.withSwap(2)).selectedSlot(2).destination(9)
                .slotHints(Arrays.asList("1: empty", "Offhand: required fire resistance"))
                .profileIds(Arrays.asList("default", "potions")).build();
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            TrainerProtocol.writeView(buf, original);
            TrainerProtocol.View actual = TrainerProtocol.readView(buf);
            assertEquals(original.sessionId, actual.sessionId);
            assertEquals(original.taskId, actual.taskId);
            assertEquals(original.revision, actual.revision);
            assertEquals(original.phase, actual.phase);
            assertEquals(original.profileId, actual.profileId);
            assertEquals(original.mode, actual.mode);
            assertEquals(4, actual.completed);
            assertEquals(25, actual.goal);
            assertEquals(40, actual.countdownTicks);
            assertEquals(123456789L, actual.elapsedNanos);
            assertEquals(7, actual.swapAttempts);
            assertEquals(6, actual.effectiveSwaps);
            assertEquals(3, actual.slotChanges);
            assertEquals(1, actual.noOpSwaps);
            assertEquals(2, actual.forbiddenActions);
            assertEquals(layout, actual.expectedInitial);
            assertEquals(layout.withSwap(2), actual.target);
            assertEquals(2, actual.selectedSlot);
            assertEquals(9, actual.destination);
            assertEquals(original.slotHints, actual.slotHints);
            assertEquals(original.profileIds, actual.profileIds);
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
    }

    @Test
    public void requestRoundTripEchoesTheExactAcknowledgedRevision() {
        TrainerProtocol.View view = new TrainerProtocol.View.Builder().session(UUID.randomUUID())
                .task(UUID.randomUUID()).revision(8).phase("GO").build();
        TrainerProtocol.Request request = TrainerProtocol.Request.signal(TrainerProtocol.Action.GO_ACK, view, "");
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            TrainerProtocol.writeRequest(buf, request);
            TrainerProtocol.Request actual = TrainerProtocol.readRequest(buf);
            assertEquals(TrainerProtocol.Action.GO_ACK, actual.action);
            assertEquals(view.sessionId, actual.sessionId);
            assertEquals(view.taskId, actual.taskId);
            assertEquals(8, actual.revision);
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedVersionIsRejected() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try { buf.writeVarInt(999); TrainerProtocol.readRequest(buf); }
        finally { buf.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSessionLengthIsRejected() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            TrainerProtocol.writeRequest(buf, TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM, "default", -1));
            TrainerProtocol.readRequest(buf);
        } finally { buf.release(); }
    }

    @Test
    public void arbitrarySuccessCountsRoundTripInRequestsAndProgressViews() {
        for (int goal : new int[] {0, 1, 73, 10001, Integer.MAX_VALUE}) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            try {
                TrainerProtocol.writeRequest(buf, TrainerProtocol.Request.start(TrainingMode.FULL_INVENTORY, "default", goal));
                assertEquals(goal, TrainerProtocol.readRequest(buf).goal);
                assertFalse(buf.isReadable());
                buf.clear();
                TrainerProtocol.writeView(buf, new TrainerProtocol.View.Builder().progress(0, goal).build());
                assertEquals(goal, TrainerProtocol.readView(buf).goal);
                assertFalse(buf.isReadable());
            } finally { buf.release(); }
        }
    }
}
