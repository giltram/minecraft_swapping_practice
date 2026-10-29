package dev.hotbarswaptrainer.smoke;

import com.google.gson.GsonBuilder;
import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.ConfirmedPlayerSave;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.inventory.RecoveryHolder;
import dev.hotbarswaptrainer.inventory.RecoveryRecord;
import dev.hotbarswaptrainer.profile.StackValue;
import dev.hotbarswaptrainer.task.InventoryLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Direct inventory-module acceptance in the separate smoke mod; never shipped. */
public final class FaultAcceptance {
    private FaultAcceptance() { }

    public static String run(ServerPlayerEntity player, Path output) throws Exception {
        Path world = player.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path marker = world.resolve(".hst-smoke-owned");
        require(world.getFileName().toString().equals("hst-smoke") && Files.isRegularFile(marker)
                && new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim()
                .equals("Hotbar Swap Trainer automated disposable smoke world v1"), "Unowned world");
        require(world.toRealPath().equals(world), "Refusing a symlinked world path");
        Path playerData = player.getServer().getSavePath(WorldSavePath.PLAYERDATA).toAbsolutePath().normalize();
        require(playerData.toRealPath().equals(world.resolve("playerdata")), "Unexpected playerdata path");
        require(!InventoryService.isProtected(player), "An earlier transaction still needs recovery");
        InventorySnapshot baseline = InventorySnapshot.capture(player.inventory);
        require(baseline.toTag().getList("Inventory", 10).size() == 41, "Expected a seeded 41-slot baseline");
        InventoryService inventory = new InventoryService();
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "Direct inventory transactions in the owned smoke world; real POSIX write failures");
        report.put("checks", checks);
        try {
            new ConfirmedPlayerSave().save(player);
            inventory.backup(player, UUID.randomUUID());
            CompoundTag raw = recovery(player);
            verifyDisk(player, baseline, raw);
            saveRecord(output.resolve("fault-valid-recovery.nbt"), raw);
            inventory.prepare(player, trainingLayout(), 0);
            require(!baseline.equals(InventorySnapshot.capture(player.inventory)), "Setup did not replace the inventory");
            inventory.restoreLoaded(player, raw);
            require(baseline.equals(InventorySnapshot.capture(player.inventory)), "First active replay differs from baseline");
            inventory.restoreLoaded(player, raw);
            require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Second active replay differs from baseline");
            require(raw.equals(recovery(player)), "Replay changed the active raw record");
            inventory.restore(player);
            verifyDisk(player, baseline, null);
            checks.add(check("active replay twice and confirmed clear", "passed", "Both authoritative paths match the original 41 slots and selection"));

            for (Path blocked : new Path[] { playerData, world }) {
                String label = blocked.equals(world) ? "host level.dat directory" : "UUID playerdata directory";
                exerciseWriteFailure(player, output, inventory, baseline, blocked, label, false, checks);
                exerciseWriteFailure(player, output, inventory, baseline, blocked, label, true, checks);
            }
            require(!InventoryService.isProtected(player), "Recovery remained active after retry");
            require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Final inventory differs from baseline");
            verifyDisk(player, baseline, null);
            report.put("status", "passed");
            report.put("finalSaveSha256", saveHashes(player));
            writeReport(output, report);
            return "Active replay twice, real UUID/host write failures before setup and during clear, retained recovery guards and successful retries passed";
        } catch (Exception failure) {
            report.put("status", "failed");
            report.put("failure", failure.toString());
            report.put("protectedAtFailure", InventoryService.isProtected(player));
            report.put("baselineMatchesAtFailure", baseline.equals(InventorySnapshot.capture(player.inventory)));
            CompoundTag retained = ((RecoveryHolder) player).trainer$getRecovery();
            if (retained != null) saveRecord(output.resolve("fault-retained-at-failure.nbt"), retained);
            try {
                if (InventoryService.isProtected(player)) inventory.restore(player);
                report.put("cleanup", "Original inventory restored after permissions were reset");
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                report.put("cleanupFailure", cleanupFailure.toString());
            }
            writeReport(output, report);
            throw failure;
        }
    }

    private static void exerciseWriteFailure(ServerPlayerEntity player, Path output, InventoryService inventory,
            InventorySnapshot baseline, Path blocked, String label, boolean clearing,
            List<Map<String, Object>> checks) throws Exception {
        String name = (clearing ? "failed clear commit: " : "failed initial backup: ") + label;
        CompoundTag expected = null;
        if (clearing) {
            inventory.backup(player, UUID.randomUUID());
            expected = recovery(player);
            inventory.prepare(player, trainingLayout(), 0);
        }
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(blocked);
        Set<PosixFilePermission> readOnly = EnumSet.copyOf(original);
        readOnly.remove(PosixFilePermission.OWNER_WRITE);
        readOnly.remove(PosixFilePermission.GROUP_WRITE);
        readOnly.remove(PosixFilePermission.OTHERS_WRITE);
        IOException observed = null;
        try {
            Files.setPosixFilePermissions(blocked, readOnly);
            proveWriteDenied(blocked);
            try {
                if (clearing) inventory.restore(player);
                else inventory.backup(player, UUID.randomUUID());
            } catch (IOException failure) { observed = failure; }
            require(observed != null, "Persistence unexpectedly accepted " + name);
            require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Failure changed original inventory: " + name);
            require(InventoryService.isProtected(player), "Failure released recovery guards: " + name);
            CompoundTag retained = recovery(player);
            RecoveryRecord.fromTag(retained, player.getUuid(), InventoryService.worldIdentity(player));
            if (expected != null) require(expected.equals(retained), "Clear failure changed the raw recovery record");
            require(!TrainerMod.SESSIONS.beforeSwap(player), "Recovery allowed a vanilla swap after " + name);
            saveRecord(output.resolve("fault-" + (clearing ? "clear-" : "backup-")
                    + (blocked.getFileName().toString().equals("playerdata") ? "uuid" : "host") + ".nbt"), retained);
        } finally {
            Files.setPosixFilePermissions(blocked, original);
        }
        require(Files.getPosixFilePermissions(blocked).equals(original), "Original permissions were not restored");
        inventory.restore(player);
        verifyDisk(player, baseline, null);
        checks.add(check(name, "passed", observed.toString() + "; permissions reset, exact restoration and cleared records confirmed"));
    }

    private static void proveWriteDenied(Path directory) throws IOException {
        Path probe = directory.resolve(".hst-fault-write-probe-" + UUID.randomUUID());
        boolean denied = false;
        try { Files.createFile(probe); }
        catch (IOException failure) {
            if (Files.isWritable(directory)) throw new IOException("Write probe failed for a reason other than read-only permissions", failure);
            denied = true;
        } finally { Files.deleteIfExists(probe); }
        require(denied, "Filesystem permission injection did not prevent a real write");
    }

    private static InventoryLayout trainingLayout() {
        List<StackValue> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("FaultTrainingSlot", i);
            slots.add(StackValue.of("minecraft:stone", i + 1, tag));
        }
        return new InventoryLayout(slots);
    }

    private static CompoundTag recovery(ServerPlayerEntity player) {
        CompoundTag record = ((RecoveryHolder) player).trainer$getRecovery();
        require(record != null, "Expected the active original recovery record");
        return record;
    }

    private static void verifyDisk(ServerPlayerEntity player, InventorySnapshot expected, CompoundTag recovery) throws IOException {
        Path uuid = player.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat");
        Path level = player.getServer().getSavePath(WorldSavePath.LEVEL_DAT);
        verifyPlayer(read(uuid), player, expected, recovery);
        verifyPlayer(read(level).getCompound("Data").getCompound("Player"), player, expected, recovery);
    }

    private static void verifyPlayer(CompoundTag saved, ServerPlayerEntity player, InventorySnapshot expected, CompoundTag recovery) {
        require(saved.containsUuid("UUID") && player.getUuid().equals(saved.getUuid("UUID")), "Saved UUID differs");
        require(expected.equals(InventorySnapshot.fromTag(saved)), "Saved inventory or selection differs");
        require(recovery == null ? !saved.contains(RecoveryRecord.KEY) : recovery.equals(saved.getCompound(RecoveryRecord.KEY)), "Saved recovery record differs");
    }

    private static CompoundTag read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) { return NbtIo.readCompressed(input); }
    }

    private static void saveRecord(Path path, CompoundTag record) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) { NbtIo.writeCompressed(record, output); }
    }

    private static Map<String, String> saveHashes(ServerPlayerEntity player) throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Path path : new Path[] {player.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat"),
                player.getServer().getSavePath(WorldSavePath.LEVEL_DAT)}) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder text = new StringBuilder();
            for (byte value : digest) text.append(String.format("%02x", value & 255));
            hashes.put(path.getFileName().toString(), text.toString());
        }
        return hashes;
    }

    private static Map<String, Object> check(String name, String status, String detail) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name); check.put("status", status); check.put("detail", detail);
        return check;
    }

    private static void writeReport(Path output, Map<String, Object> report) throws IOException {
        Files.write(output.resolve("fault-checks.json"), (new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
