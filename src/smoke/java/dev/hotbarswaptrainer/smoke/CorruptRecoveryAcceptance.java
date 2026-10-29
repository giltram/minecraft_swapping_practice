package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Corruption is written only into the owned test save, and repaired from its archived valid record. */
final class CorruptRecoveryAcceptance {
    private CorruptRecoveryAcceptance() { }

    static void prepare(ServerPlayerEntity player, InventorySnapshot baseline, String variant, Path output) throws Exception {
        AcceptanceSupport.startAt(player, "RUNNING");
        new ConfirmedPlayerSave().save(player);
        CompoundTag valid = ((RecoveryHolder) player).trainer$getRecovery();
        CompoundTag artifact = new CompoundTag();
        artifact.putString("variant", variant);
        artifact.putUuid("Player", player.getUuid());
        artifact.put("valid", valid);
        artifact.put("training", InventorySnapshot.capture(player.inventory).toTag());
        Tag corrupt;
        if (variant.equals("wrong-type")) corrupt = StringTag.of("keep this exact malformed recovery value");
        else {
            CompoundTag body = valid.copy();
            switch (variant) {
                case "schema": body.putInt("schemaVersion", 999); resign(body); break;
                case "checksum": body.putString("checksum", "damaged checksum"); break;
                case "player": body.putString("player", UUID.randomUUID().toString()); resign(body); break;
                case "world": body.putString("world", InventoryService.worldIdentity(player) + "-wrong"); resign(body); break;
                case "truncated": body.remove("original"); resign(body); break;
                case "snapshot-type": body.getCompound("original").putString("Inventory", "wrong type"); resign(body); break;
                case "inactive": body.putBoolean("active", false); resign(body); break;
                default: throw new IllegalArgumentException("Unknown corruption variant " + variant);
            }
            corrupt = body;
        }
        artifact.put("corrupt", corrupt);
        AcceptanceSupport.write(output.resolve("corruption.nbt"), artifact);
        // Deliberately bypass serialization only for the corrupt test fixture, after archiving it.
        Path uuid = AcceptanceSupport.uuidPath(player);
        CompoundTag saved = AcceptanceSupport.read(uuid);
        saved.put(RecoveryRecord.KEY, corrupt.copy());
        AcceptanceSupport.write(uuid, saved);
        Path level = player.getServer().getSavePath(WorldSavePath.LEVEL_DAT);
        CompoundTag host = AcceptanceSupport.read(level);
        host.getCompound("Data").getCompound("Player").put(RecoveryRecord.KEY, corrupt.copy());
        AcceptanceSupport.write(level, host);
        AcceptanceSupport.verifyDisk(player, InventorySnapshot.capture(player.inventory), corrupt);
        AcceptanceSupport.json(output.resolve("accept-cut.json"), AcceptanceSupport.check("corrupt " + variant,
                "Both authoritative saves contain exact malformed raw data; next launch must lock"));
        Runtime.getRuntime().halt(91);
    }

    static String check(ServerPlayerEntity player, Path output) throws Exception {
        CompoundTag artifact = AcceptanceSupport.read(output.resolve("corruption.nbt"));
        AcceptanceSupport.require(artifact.getUuid("Player").equals(player.getUuid()), "Corruption fixture UUID differs");
        Tag corrupt = artifact.get("corrupt");
        InventorySnapshot training = InventorySnapshot.fromTag(artifact.getCompound("training"));
        AcceptanceSupport.require(InventoryService.isProtected(player), "Corrupt load released protection");
        AcceptanceSupport.require(training.equals(InventorySnapshot.capture(player.inventory)), "Corrupt data was replayed into inventory");
        AcceptanceSupport.require(corrupt.equals(player.toTag(new CompoundTag()).get(RecoveryRecord.KEY)), "Raw corruption was lost on load");
        AcceptanceSupport.require(!TrainerMod.SESSIONS.beforeSwap(player), "Corrupt recovery allowed a swap");
        TrainerMod.SESSIONS.stop(player, "Acceptance corrupt stop must fail closed");
        AcceptanceSupport.require(InventoryService.isProtected(player), "Failed stop released protection");
        AcceptanceSupport.require(corrupt.equals(player.toTag(new CompoundTag()).get(RecoveryRecord.KEY)), "Failed stop replaced raw corruption");
        player.getServer().getPlayerManager().saveAllPlayerData();
        player.getServer().save(false, true, false);
        AcceptanceSupport.verifyDisk(player, training, corrupt);
        CompoundTag locked = player.toTag(new CompoundTag());
        AcceptanceSupport.write(output.resolve("corruption-locked-player.nbt"), locked);
        // The only repair source is this exact test's retained, validated backup.
        CompoundTag valid = artifact.getCompound("valid");
        InventorySnapshot baseline = RecoveryRecord.fromTag(valid, player.getUuid(), InventoryService.worldIdentity(player)).getOriginal();
        ((RecoveryHolder) player).trainer$setRecovery(valid);
        TrainerMod.SESSIONS.stop(player, "Acceptance injected data repaired from archived valid backup");
        AcceptanceSupport.restored(player, baseline);
        AcceptanceSupport.json(output.resolve("corruption-checks.json"), AcceptanceSupport.check(
                artifact.getString("variant"), "Real load locked; exact raw data survived failed stop and both saves; archived valid repair restored all41/selection"));
        return "Corrupt " + artifact.getString("variant") + " load/guards/raw preservation and exact repaired recovery passed";
    }

    private static void resign(CompoundTag tag) throws Exception {
        tag.remove("checksum");
        Method checksum = RecoveryRecord.class.getDeclaredMethod("checksum", Tag.class);
        checksum.setAccessible(true);
        tag.putString("checksum", (String) checksum.invoke(null, tag));
    }
}
