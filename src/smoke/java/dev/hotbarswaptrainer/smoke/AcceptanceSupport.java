package dev.hotbarswaptrainer.smoke;

import com.google.gson.GsonBuilder;
import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Shared evidence and session setup for the optional acceptance mod only. */
final class AcceptanceSupport {
    private AcceptanceSupport() { }

    static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void set(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    static Object session(ServerPlayerEntity player) throws Exception {
        return ((Map<?, ?>) field(TrainerMod.SESSIONS, "sessions")).get(player.getUuid());
    }

    static String phase(ServerPlayerEntity player) throws Exception {
        Object session = session(player);
        return session == null ? "IDLE" : field(session, "phase").toString();
    }

    static void signal(ServerPlayerEntity player, TrainerProtocol.Action action) throws Exception {
        Object s = session(player);
        InventoryTask task = (InventoryTask) field(s, "task");
        TrainerMod.SESSIONS.handle(player, new TrainerProtocol.Request(action,
                (UUID) field(s, "id"), task.getId(), (Long) field(s, "revision"),
                TrainingMode.SINGLE_ITEM, "default", 0, "Acceptance"));
    }

    /** Session lifecycle verification, deliberately independent of physical/rendered input. */
    static void startAt(ServerPlayerEntity player, String target) throws Exception {
        require(!InventoryService.isProtected(player), "A prior transaction is active");
        TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM, "default", 0));
        require(phase(player).equals("PREPARING"), "START did not prepare: " + phase(player));
        if (target.equals("PREPARING")) return;
        signal(player, TrainerProtocol.Action.READY);
        require(phase(player).equals("COUNTDOWN"), "READY did not begin countdown");
        if (target.equals("COUNTDOWN")) return;
        set(session(player), "deadlineTick", player.getServer().getTicks() - 1);
        TrainerMod.SESSIONS.tick(player.getServer());
        require(phase(player).equals("GO"), "Countdown did not reach GO");
        signal(player, TrainerProtocol.Action.GO_ACK);
        require(phase(player).equals("RUNNING"), "GO_ACK did not begin timing");
        if (target.equals("SUCCESS")) solve(player);
        require(phase(player).equals(target), "Expected " + target + ", got " + phase(player));
    }

    static void solve(ServerPlayerEntity player) throws Exception {
        InventoryTask task = (InventoryTask) field(session(player), "task");
        for (int selected : task.getSolution()) {
            player.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(selected));
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
    }

    static void restored(ServerPlayerEntity player, InventorySnapshot baseline) throws Exception {
        require(!InventoryService.isProtected(player), "Recovery guards still active");
        require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Original inventory/selection differs");
        verifyDisk(player, baseline, null);
    }

    static Path uuidPath(ServerPlayerEntity player) {
        return player.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat");
    }

    static void verifyDisk(ServerPlayerEntity player, InventorySnapshot expected, net.minecraft.nbt.Tag raw) throws Exception {
        CompoundTag[] saved = {read(uuidPath(player)), read(player.getServer().getSavePath(WorldSavePath.LEVEL_DAT))
                .getCompound("Data").getCompound("Player")};
        for (CompoundTag tag : saved) {
            require(tag.containsUuid("UUID") && player.getUuid().equals(tag.getUuid("UUID")), "Disk UUID differs");
            require(expected.equals(InventorySnapshot.fromTag(tag)), "Disk inventory/selection differs");
            require(raw == null ? !tag.contains(RecoveryRecord.KEY) : raw.equals(tag.get(RecoveryRecord.KEY)), "Disk raw recovery differs");
        }
    }

    static CompoundTag read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) { return NbtIo.readCompressed(input); }
    }

    static void write(Path path, CompoundTag tag) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) { NbtIo.writeCompressed(tag, output); }
    }

    static void json(Path path, Object value) throws IOException {
        Files.write(path, (new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, Object> check(String name, String detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name); value.put("status", "passed"); value.put("detail", detail);
        return value;
    }
}
