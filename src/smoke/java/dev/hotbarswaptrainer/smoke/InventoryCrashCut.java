package dev.hotbarswaptrainer.smoke;

import com.google.gson.GsonBuilder;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Prepares explicit inventory-module crash boundaries; caller records readiness then halts. */
final class InventoryCrashCut {
    private InventoryCrashCut() { }

    static String prepare(ServerPlayerEntity player, InventorySnapshot baseline, String mode, Path output) throws Exception {
        require(Arrays.asList("cut-backup", "cut-setup", "cut-restored", "cut-cleared").contains(mode), "Unknown cut mode");
        Path world = player.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path marker = world.resolve(".hst-smoke-owned");
        require(world.getFileName().toString().equals("hst-smoke") && Files.isRegularFile(marker)
                && new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim()
                .equals("Hotbar Swap Trainer automated disposable smoke world v1"), "Unowned crash-test world");
        require(!InventoryService.isProtected(player), "Earlier recovery is still active");
        require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Cut does not begin at the seeded baseline");

        InventoryService inventory = new InventoryService();
        inventory.backup(player, UUID.randomUUID());
        CompoundTag active = ((RecoveryHolder) player).trainer$getRecovery();
        require(active != null, "Backup did not attach an active recovery record");
        require(baseline.equals(RecoveryRecord.fromTag(active, player.getUuid(), InventoryService.worldIdentity(player)).getOriginal()),
                "Active original differs from baseline");
        InventorySnapshot diskExpected = baseline;
        InventorySnapshot memoryExpected = baseline;
        CompoundTag expectedRecovery = active;
        String detail = "Confirmed baseline plus active backup in both files";

        if (!mode.equals("cut-backup")) {
            inventory.prepare(player, trainingLayout(), 0);
            InventorySnapshot training = InventorySnapshot.capture(player.inventory);
            require(!training.equals(baseline), "Training setup did not replace baseline");
            memoryExpected = training;
            detail = "Training inventory in memory; confirmed baseline plus active backup remains in both files";
            if (mode.equals("cut-restored") || mode.equals("cut-cleared")) {
                new ConfirmedPlayerSave().save(player);
                diskExpected = training;
                if (mode.equals("cut-restored")) {
                    inventory.restoreLoaded(player, active);
                    memoryExpected = baseline;
                    detail = "Original replayed in memory with active record retained; confirmed training plus active backup remains in both files";
                } else {
                    inventory.restore(player);
                    memoryExpected = baseline;
                    diskExpected = baseline;
                    expectedRecovery = null;
                    detail = "Actual restore completed; original inventory and cleared recovery confirmed in both files";
                }
            }
        }

        require(memoryExpected.equals(InventorySnapshot.capture(player.inventory)), "Unexpected inventory at cut boundary");
        CompoundTag memoryRecovery = ((RecoveryHolder) player).trainer$getRecovery();
        require(expectedRecovery == null ? memoryRecovery == null : expectedRecovery.equals(memoryRecovery), "Unexpected memory recovery at cut");
        Path playerPath = player.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat");
        Path levelPath = player.getServer().getSavePath(WorldSavePath.LEVEL_DAT);
        CompoundTag savedPlayer = read(playerPath);
        CompoundTag savedHost = read(levelPath).getCompound("Data").getCompound("Player");
        verify(savedPlayer, player, diskExpected, expectedRecovery);
        verify(savedHost, player, diskExpected, expectedRecovery);

        Path evidence = output.resolve(mode);
        Files.createDirectories(evidence);
        Path playerCopy = evidence.resolve("uuid-player-before-halt.dat");
        Path levelCopy = evidence.resolve("host-level-before-halt.dat");
        Files.copy(playerPath, playerCopy, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(levelPath, levelCopy, StandardCopyOption.REPLACE_EXISTING);
        CompoundTag state = new CompoundTag();
        state.putString("mode", mode);
        state.put("baseline", baseline.toTag());
        state.put("memoryInventory", InventorySnapshot.capture(player.inventory).toTag());
        if (memoryRecovery != null) state.put("memoryRecovery", memoryRecovery);
        state.put("uuidPlayer", savedPlayer);
        state.put("hostPlayer", savedHost);
        Path statePath = evidence.resolve("state-before-halt.nbt");
        try (OutputStream stream = Files.newOutputStream(statePath)) { NbtIo.writeCompressed(state, stream); }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("status", "ready");
        report.put("scope", "Direct inventory-module boundary, not a SessionManager.stop injection");
        report.put("detail", detail);
        report.put("player", player.getUuid().toString());
        report.put("memoryMatchesBaseline", memoryExpected.equals(baseline));
        report.put("diskMatchesBaseline", diskExpected.equals(baseline));
        report.put("activeRecovery", expectedRecovery != null);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Path path : new Path[] {playerCopy, levelCopy, statePath}) hashes.put(path.getFileName().toString(), sha256(path));
        require(sha256(playerPath).equals(hashes.get(playerCopy.getFileName().toString())), "UUID evidence copy differs from disk");
        require(sha256(levelPath).equals(hashes.get(levelCopy.getFileName().toString())), "Host evidence copy differs from disk");
        report.put("sha256", hashes);
        Files.write(output.resolve(mode + "-checks.json"), (new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
        return detail;
    }

    private static InventoryLayout trainingLayout() {
        List<StackValue> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("CrashCutTrainingSlot", i);
            slots.add(StackValue.of("minecraft:stone", i + 1, tag));
        }
        return new InventoryLayout(slots);
    }

    private static CompoundTag read(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) { return NbtIo.readCompressed(input); }
    }

    private static void verify(CompoundTag saved, ServerPlayerEntity player, InventorySnapshot expected, CompoundTag recovery) {
        require(saved.containsUuid("UUID") && player.getUuid().equals(saved.getUuid("UUID")), "Saved UUID differs");
        require(expected.equals(InventorySnapshot.fromTag(saved)), "Saved inventory or selection differs at cut");
        require(recovery == null ? !saved.contains(RecoveryRecord.KEY) : recovery.equals(saved.getCompound(RecoveryRecord.KEY)), "Saved recovery differs at cut");
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder value = new StringBuilder();
        for (byte part : digest) value.append(String.format("%02x", part & 255));
        return value.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
