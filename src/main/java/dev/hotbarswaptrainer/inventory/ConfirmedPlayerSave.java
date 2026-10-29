package dev.hotbarswaptrainer.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vanilla swallows some I/O errors; a save call alone is never a transaction acknowledgment. */
public final class ConfirmedPlayerSave {
    public void save(ServerPlayerEntity player) throws IOException {
        MinecraftServer server = player.getServer();
        InventorySnapshot expected = InventorySnapshot.capture(player.inventory);
        CompoundTag recovery = ((RecoveryHolder) player).trainer$getRecovery();
        // IntegratedPlayerManager updates its host userData here, before level.dat is saved.
        server.getPlayerManager().saveAllPlayerData();
        server.save(false, true, false);
        Path playerPath = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat");
        verify(read(playerPath), expected, recovery, player.getUuid(), playerPath);
        if (server.isHost(player.getGameProfile())) {
            Path levelPath = server.getSavePath(WorldSavePath.LEVEL_DAT);
            verify(read(levelPath).getCompound("Data").getCompound("Player"), expected, recovery, player.getUuid(), levelPath);
        }
    }

    private static CompoundTag read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Save was not created: " + path);
        try (java.io.InputStream stream = Files.newInputStream(path)) { return NbtIo.readCompressed(stream); }
    }

    private static void verify(CompoundTag saved, InventorySnapshot expected, CompoundTag recovery, java.util.UUID playerId, Path path) throws IOException {
        try {
            if (!saved.containsUuid("UUID") || !playerId.equals(saved.getUuid("UUID"))) throw new IOException("Player UUID readback mismatch: " + path);
            if (!expected.equals(InventorySnapshot.fromTag(saved))) throw new IOException("Inventory readback mismatch: " + path);
            if (recovery == null ? saved.contains(RecoveryRecord.KEY) : !recovery.equals(saved.getCompound(RecoveryRecord.KEY))) {
                throw new IOException("Recovery readback mismatch: " + path);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid player save at " + path, invalid);
        }
    }
}
