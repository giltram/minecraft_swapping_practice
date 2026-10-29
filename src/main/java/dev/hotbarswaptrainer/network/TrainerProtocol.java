package dev.hotbarswaptrainer.network;

import dev.hotbarswaptrainer.profile.StackValue;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Versioned, bounded wire contract. Contains no client runtime references. */
public final class TrainerProtocol {
    public static final int VERSION = 1;
    public static final UUID NONE = new UUID(0, 0);
    public static final Identifier REQUEST = new Identifier("hotbar-swap-trainer", "request");
    public static final Identifier VIEW = new Identifier("hotbar-swap-trainer", "view");
    private static final int MAX_TEXT = 2048;

    private TrainerProtocol() { }

    public enum Action { START, STOP, READY, GO_ACK, INTERRUPT, FORBIDDEN }

    public static final class Request {
        public final Action action;
        public final UUID sessionId;
        public final UUID taskId;
        public final long revision;
        public final TrainingMode mode;
        public final String profileId;
        public final int goal;
        public final String reason;

        public Request(Action action, UUID sessionId, UUID taskId, long revision,
                       TrainingMode mode, String profileId, int goal, String reason) {
            this.action = action;
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.revision = revision;
            this.mode = mode;
            this.profileId = profileId;
            this.goal = goal;
            this.reason = reason;
        }

        public static Request start(TrainingMode mode, String profileId, int goal) {
            return new Request(Action.START, NONE, NONE, 0, mode, profileId, goal, "");
        }

        public static Request signal(Action action, View view, String reason) {
            return new Request(action, view.sessionId, view.taskId, view.revision,
                    view.mode, view.profileId, view.goal, reason);
        }
    }

    public static final class View {
        public final UUID sessionId;
        public final UUID taskId;
        public final long revision;
        public final String phase;
        public final String message;
        public final String profileId;
        public final TrainingMode mode;
        public final int completed;
        public final int goal;
        public final int countdownTicks;
        public final long elapsedNanos;
        public final int swapAttempts;
        public final int effectiveSwaps;
        public final int slotChanges;
        public final int noOpSwaps;
        public final int forbiddenActions;
        public final InventoryLayout expectedInitial;
        public final InventoryLayout target;
        public final int selectedSlot;
        public final int destination;
        public final List<String> slotHints;
        public final List<String> profileIds;

        private View(Builder b) {
            sessionId = b.sessionId; taskId = b.taskId; revision = b.revision;
            phase = b.phase; message = b.message; profileId = b.profileId; mode = b.mode;
            completed = b.completed; goal = b.goal; countdownTicks = b.countdownTicks;
            elapsedNanos = b.elapsedNanos; swapAttempts = b.swapAttempts;
            effectiveSwaps = b.effectiveSwaps; slotChanges = b.slotChanges;
            noOpSwaps = b.noOpSwaps; forbiddenActions = b.forbiddenActions;
            expectedInitial = b.expectedInitial; target = b.target;
            selectedSlot = b.selectedSlot; destination = b.destination;
            slotHints = Collections.unmodifiableList(new ArrayList<>(b.slotHints));
            profileIds = Collections.unmodifiableList(new ArrayList<>(b.profileIds));
        }

        public boolean isActive() {
            return !"IDLE".equals(phase) && !"ERROR".equals(phase);
        }

        public static final class Builder {
            private UUID sessionId = NONE, taskId = NONE;
            private long revision, elapsedNanos;
            private String phase = "IDLE", message = "", profileId = "default";
            private TrainingMode mode = TrainingMode.SINGLE_ITEM;
            private int completed, goal = 10, countdownTicks;
            private int swapAttempts, effectiveSwaps, slotChanges, noOpSwaps, forbiddenActions;
            private InventoryLayout expectedInitial, target;
            private int selectedSlot, destination = -1;
            private List<String> slotHints = Collections.emptyList(), profileIds = Collections.emptyList();

            public Builder session(UUID value) { sessionId = value; return this; }
            public Builder task(UUID value) { taskId = value; return this; }
            public Builder revision(long value) { revision = value; return this; }
            public Builder phase(String value) { phase = value; return this; }
            public Builder message(String value) { message = value; return this; }
            public Builder profile(String value) { profileId = value; return this; }
            public Builder mode(TrainingMode value) { mode = value; return this; }
            public Builder progress(int done, int total) { completed = done; goal = total; return this; }
            public Builder countdown(int value) { countdownTicks = value; return this; }
            public Builder elapsed(long value) { elapsedNanos = value; return this; }
            public Builder counts(int attempts, int effective, int changes, int noOps, int forbidden) {
                swapAttempts = attempts; effectiveSwaps = effective; slotChanges = changes;
                noOpSwaps = noOps; forbiddenActions = forbidden; return this;
            }
            public Builder layouts(InventoryLayout initial, InventoryLayout goalLayout) {
                expectedInitial = initial; target = goalLayout; return this;
            }
            public Builder selectedSlot(int value) { selectedSlot = value; return this; }
            public Builder destination(int value) { destination = value; return this; }
            public Builder slotHints(List<String> value) { slotHints = value; return this; }
            public Builder profileIds(List<String> value) { profileIds = value; return this; }
            public View build() { return new View(this); }
        }
    }

    public static void writeRequest(PacketByteBuf buf, Request value) {
        buf.writeVarInt(VERSION);
        buf.writeEnumConstant(value.action);
        buf.writeUuid(value.sessionId); buf.writeUuid(value.taskId); buf.writeLong(value.revision);
        buf.writeEnumConstant(value.mode); buf.writeString(value.profileId);
        buf.writeVarInt(value.goal); buf.writeString(value.reason);
    }

    public static Request readRequest(PacketByteBuf buf) {
        checkVersion(buf);
        Action action = buf.readEnumConstant(Action.class);
        UUID session = buf.readUuid(), task = buf.readUuid();
        long revision = buf.readLong();
        TrainingMode mode = buf.readEnumConstant(TrainingMode.class);
        String profile = buf.readString(128);
        int goal = buf.readVarInt();
        String reason = buf.readString(MAX_TEXT);
        if (goal < 0 || revision < 0) throw new IllegalArgumentException("Invalid request bounds");
        return new Request(action, session, task, revision, mode, profile, goal, reason);
    }

    public static void writeView(PacketByteBuf buf, View v) {
        buf.writeVarInt(VERSION);
        buf.writeUuid(v.sessionId); buf.writeUuid(v.taskId); buf.writeLong(v.revision);
        buf.writeString(v.phase); buf.writeString(v.message); buf.writeString(v.profileId);
        buf.writeEnumConstant(v.mode); buf.writeVarInt(v.completed); buf.writeVarInt(v.goal);
        buf.writeVarInt(v.countdownTicks); buf.writeLong(v.elapsedNanos);
        buf.writeVarInt(v.swapAttempts); buf.writeVarInt(v.effectiveSwaps); buf.writeVarInt(v.slotChanges);
        buf.writeVarInt(v.noOpSwaps); buf.writeVarInt(v.forbiddenActions);
        writeLayout(buf, v.expectedInitial); writeLayout(buf, v.target);
        buf.writeInt(v.selectedSlot); buf.writeInt(v.destination);
        writeStrings(buf, v.slotHints); writeStrings(buf, v.profileIds);
    }

    public static View readView(PacketByteBuf buf) {
        checkVersion(buf);
        View.Builder b = new View.Builder().session(buf.readUuid()).task(buf.readUuid()).revision(buf.readLong())
                .phase(buf.readString(32)).message(buf.readString(MAX_TEXT)).profile(buf.readString(128))
                .mode(buf.readEnumConstant(TrainingMode.class)).progress(buf.readVarInt(), buf.readVarInt())
                .countdown(buf.readVarInt()).elapsed(buf.readLong());
        b.counts(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        b.layouts(readLayout(buf), readLayout(buf)).selectedSlot(buf.readInt()).destination(buf.readInt());
        b.slotHints(readStrings(buf, 10)).profileIds(readStrings(buf, 128));
        return b.build();
    }

    private static void checkVersion(PacketByteBuf buf) {
        if (buf.readVarInt() != VERSION) throw new IllegalArgumentException("Unsupported trainer protocol version");
    }

    private static void writeLayout(PacketByteBuf buf, InventoryLayout layout) {
        buf.writeBoolean(layout != null);
        if (layout == null) return;
        for (int i = 0; i < 10; i++) {
            StackValue stack = layout.get(i);
            buf.writeString(stack.getItemId()); buf.writeVarInt(stack.getCount()); buf.writeCompoundTag(stack.getNbt());
        }
    }

    private static InventoryLayout readLayout(PacketByteBuf buf) {
        if (!buf.readBoolean()) return null;
        List<StackValue> stacks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = buf.readString(128);
            int count = buf.readVarInt();
            if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid stack count");
            stacks.add(StackValue.of(id, count, buf.readCompoundTag()));
        }
        return new InventoryLayout(stacks);
    }

    private static void writeStrings(PacketByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) buf.writeString(value);
    }

    private static List<String> readStrings(PacketByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid list size");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(buf.readString(MAX_TEXT));
        return values;
    }
}
