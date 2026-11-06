package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.arena.ArenaJournal;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.inventory.RecoveryHolder;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owned-world real I/O failure and actual RESTORING-boundary observation, excluded from production. */
public final class ArenaRecoveryChecks {
    private static boolean observing, restoringObserved;
    private ArenaRecoveryChecks() { }

    public static void beforeRestoreSave(ServerPlayerEntity player) {
        if (!observing) return;
        try {
            AcceptanceSupport.require("RESTORING".equals(AcceptanceSupport.phase(player)), "Not the actual RESTORING boundary");
            AcceptanceSupport.require(!TrainerMod.SESSIONS.restoreBeforeWorldOperation(player), "World operation bypassed RESTORING");
            restoringObserved = true;
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    static void run(ServerPlayerEntity player, InventorySnapshot baseline, List<Map<String, Object>> checks) throws Exception {
        run(player, baseline, checks, dev.hotbarswaptrainer.environment.TrainingEnvironment.STATIONARY);
    }
    static void run(ServerPlayerEntity player, InventorySnapshot baseline, List<Map<String, Object>> checks,
                    dev.hotbarswaptrainer.environment.TrainingEnvironment environment) throws Exception {
        Path world = player.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        byte[] journal = Files.readAllBytes(world.resolve(ArenaJournal.FILE));
        TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM, "default", 0, false, environment));
        AcceptanceSupport.require(InventoryService.isProtected(player), "Safety check did not begin a transaction");
        CompoundTag raw = ((RecoveryHolder) player).trainer$getRecovery().copy();
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(world);
        Set<PosixFilePermission> denied = EnumSet.copyOf(permissions);
        denied.remove(PosixFilePermission.OWNER_WRITE); denied.remove(PosixFilePermission.GROUP_WRITE); denied.remove(PosixFilePermission.OTHERS_WRITE);
        observing = true; restoringObserved = false;
        try {
            Files.setPosixFilePermissions(world, denied);
            TrainerMod.SESSIONS.stop(player, "Stopped");
            AcceptanceSupport.require("ERROR_LOCKED".equals(AcceptanceSupport.phase(player)), "Host write denial did not lock recovery");
            AcceptanceSupport.require(restoringObserved, "Actual RESTORING save boundary was not observed");
            AcceptanceSupport.require(!TrainerMod.SESSIONS.restoreBeforeWorldOperation(player), "World operation bypassed ERROR_LOCKED");
            TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.FULL_INVENTORY, "default", 0));
            AcceptanceSupport.require(raw.equals(((RecoveryHolder) player).trainer$getRecovery()), "START replaced unresolved recovery data");
            AcceptanceSupport.require(java.util.Arrays.equals(journal, Files.readAllBytes(world.resolve(ArenaJournal.FILE))), "Inventory failure changed arena ownership/state");
        } finally { observing = false; Files.setPosixFilePermissions(world, permissions); }
        TrainerMod.SESSIONS.stop(player, "Stopped"); AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("RESTORING and ERROR_LOCKED world gates", "Actual stop-save observation and host write denial; world operations rejected, raw transaction/journal retained; explicit recovery retry passed"));
    }
}
