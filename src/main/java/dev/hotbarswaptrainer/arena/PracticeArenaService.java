package dev.hotbarswaptrainer.arena;

import dev.hotbarswaptrainer.inventory.ConfirmedPlayerSave;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/** One integrated-server lifecycle; no session/statistics dependencies and no global registry. */
public final class PracticeArenaService {
    private final BiConsumer<ServerPlayerEntity, ArenaStatus> publish;
    private MinecraftServer server;
    private ArenaJournal journal;
    private ArenaLifecycle operation;
    private ArenaJournal.Record approved;
    private InventorySnapshot exitInventory;
    private java.util.UUID exitingPlayer;
    private ArenaStatus status = ArenaStatus.of(ArenaStatus.State.NONE, 0);
    private boolean candidate, attempted, announced;

    public PracticeArenaService(BiConsumer<ServerPlayerEntity, ArenaStatus> publish) { this.publish = publish; }

    public void opened(MinecraftServer server) {
        this.server = server; operation = null; approved = null; exitInventory = null; exitingPlayer = null; attempted = false; announced = false;
        Path directory = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        candidate = Files.exists(directory.resolve(ArenaJournal.FILE), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(directory.resolve("data/" + ArenaSeal.NAME + ".dat"), LinkOption.NOFOLLOW_LINKS);
        journal = candidate ? new ArenaJournal(directory) : null;
        status = ArenaStatus.of(candidate ? ArenaStatus.State.CHECKING : ArenaStatus.State.NONE, 0);
    }

    public void closed(MinecraftServer current) {
        if (current != server) return;
        server = null; journal = null; operation = null; approved = null; exitInventory = null; exitingPlayer = null; candidate = false;
        status = ArenaStatus.of(ArenaStatus.State.NONE, 0);
    }

    public void tick(MinecraftServer current) {
        if (current != server || !candidate || current.getPlayerManager().getPlayerList().isEmpty()) return;
        ServerPlayerEntity owner = current.getPlayerManager().getPlayerList().get(0);
        if (status.state == ArenaStatus.State.EXIT_RESTORED || status.state == ArenaStatus.State.EXIT_READY) {
            if (current.getTicks() % 40 == 0) emit(owner, status);
            return;
        }
        if (attempted && operation == null && status.state == ArenaStatus.State.READY) return;
        if (InventoryService.isProtected(owner)) {
            recoveryBlocked(owner);
            return; // SessionManager runs first and is the only recovery authority.
        }
        if (!localOwner(owner)) { send(owner, ArenaStatus.error(ArenaStatus.Problem.LOCAL_ONLY, false)); return; }
        if (!attempted) {
            attempted = true;
            try { operation = new ArenaLifecycle(journal, new MinecraftArenaTerrain(owner, journal.world()), false); }
            catch (IOException | RuntimeException failure) { fail(owner, ArenaStatus.Problem.OWNERSHIP, failure); }
        }
        if (operation != null && status.state != ArenaStatus.State.ERROR && status.state != ArenaStatus.State.EXIT_READY
                && status.state != ArenaStatus.State.EXIT_RESTORED) {
            try {
                ArenaStatus next = operation.tick();
                if (next.state == ArenaStatus.State.READY) {
                    approved = journal.read();
                    operation = null;
                    // Arrival follows recovery and inspection. STOP during Moving still never teleports.
                    owner.teleport(current.getOverworld(), 0.5, ArenaSpec.SPAWN_Y, 0.5, owner.yaw, owner.pitch);
                    owner.setVelocity(0, 0, 0); owner.fallDistance = 0;
                }
                send(owner, next);
            } catch (ArenaLifecycle.ChangedTerrain changed) { fail(owner, ArenaStatus.Problem.RESET_REQUIRED, changed); }
            catch (IOException | RuntimeException failure) { fail(owner, ArenaStatus.Problem.SAVE, failure); }
        } else if (!announced || current.getTicks() % 40 == 0) send(owner, status);
    }

    public boolean trainingAllowed(ServerPlayerEntity player) {
        if (player.getServer() != server) return false;
        if (candidate) {
            if (status.state != ArenaStatus.State.READY || operation != null || approved == null) return false;
            try { return approved.equals(journal.read()); }
            catch (IOException invalidOwnership) { return false; }
        }
        return Files.isRegularFile(server.getSavePath(WorldSavePath.ROOT).resolve(".hotbar-swap-trainer-test"));
    }

    public boolean manages(ServerPlayerEntity player) { return candidate && player.getServer() == server; }
    private boolean localOwner(ServerPlayerEntity player) {
        return player.getServer() == server && !server.isDedicated() && !server.isRemote()
                && server.isHost(player.getGameProfile()) && server.getPlayerManager().getCurrentPlayerCount() == 1;
    }

    public void refresh(ServerPlayerEntity player) { if (manages(player)) emit(player, status); }
    public void recoveryBlocked(ServerPlayerEntity player) {
        emit(player, ArenaStatus.error(ArenaStatus.Problem.RECOVERY, false));
    }

    /** Caller has already stopped the session and confirmed recovery; no client claim is trusted. */
    public void reset(ServerPlayerEntity player, boolean retry) {
        if (!manages(player)) return;
        if (!localOwner(player)) { emit(player, ArenaStatus.error(ArenaStatus.Problem.LOCAL_ONLY, false)); return; }
        if (InventoryService.isProtected(player)) { recoveryBlocked(player); return; }
        if (exitInventory != null) { emit(player, status); return; }
        if (operation != null && status.state != ArenaStatus.State.ERROR) return;
        try {
            // A retry never turns a READY arena into a destructive reset.
            boolean reset = !retry;
            operation = new ArenaLifecycle(journal, new MinecraftArenaTerrain(player, journal.world()), reset);
            attempted = true;
            send(player, ArenaStatus.of(reset ? ArenaStatus.State.BUILDING : ArenaStatus.State.CHECKING, 0));
        } catch (IOException | RuntimeException failure) { fail(player, ArenaStatus.Problem.OWNERSHIP, failure); }
    }

    public void exit(ServerPlayerEntity player) {
        if (!manages(player) || InventoryService.isProtected(player)) { recoveryBlocked(player); return; }
        if (exitInventory == null) {
            exitInventory = InventorySnapshot.capture(player.inventory);
            exitingPlayer = player.getUuid();
        }
        send(player, ArenaStatus.of(ArenaStatus.State.EXIT_RESTORED, 100));
    }

    public void commitExit(ServerPlayerEntity player) {
        if (!manages(player) || InventoryService.isProtected(player) || exitInventory == null
                || !player.getUuid().equals(exitingPlayer) || status.state != ArenaStatus.State.EXIT_RESTORED) return;
        try {
            if (!exitInventory.equals(InventorySnapshot.capture(player.inventory)))
                throw new IOException("Inventory changed after the confirmed restore; exit remains blocked");
            // Can also leave a broken arena safely. Never repair its ownership or mark it ready here.
            new ConfirmedPlayerSave().save(player);
            verifySavedExitTerrain();
            send(player, ArenaStatus.of(ArenaStatus.State.EXIT_READY, 100));
        } catch (IOException | RuntimeException failure) { fail(player, ArenaStatus.Problem.SAVE, failure); }
    }

    private void verifySavedExitTerrain() throws IOException {
        try { ArenaSeal.check(journal.world(), journal.read(), false); }
        catch (IOException unverifiedOwnership) {
            // An ownership-error screen must still allow ordinary vanilla exit after inventory recovery.
            // No arena ownership/state/terrain is changed or declared ready in this path.
            LogManager.getLogger("HotbarSwapTrainer").warn("Exiting without managed-terrain confirmation because ownership is invalid; inventory save confirmed", unverifiedOwnership);
            return;
        }
        for (int index = 0; index < ArenaSpec.TOTAL_CHUNKS; index++) {
            net.minecraft.world.chunk.WorldChunk chunk = server.getOverworld().getChunkManager().getWorldChunk(
                    ArenaSpec.CHUNK_MIN + index % ArenaSpec.CHUNK_COUNT, ArenaSpec.CHUNK_MIN + index / ArenaSpec.CHUNK_COUNT);
            if (chunk == null) throw new IOException("Cannot confirm an unloaded arena chunk before exit");
            // Preserve current player changes; exit does not reset the arena to its template.
            ArenaSavedChunks.checkCurrent(journal.world(), index, chunk);
        }
    }

    private void fail(ServerPlayerEntity player, ArenaStatus.Problem problem, Exception failure) {
        LogManager.getLogger("HotbarSwapTrainer").error("Practice arena operation stopped; original ownership data retained", failure);
        boolean resetAllowed = false;
        try { resetAllowed = journal.read().phase == ArenaJournal.Phase.READY; }
        catch (IOException ignored) { /* Invalid ownership never authorizes a reset. */ }
        if (problem == ArenaStatus.Problem.RESET_REQUIRED && !resetAllowed) problem = ArenaStatus.Problem.SAVE;
        operation = null;
        send(player, ArenaStatus.error(problem, resetAllowed && problem == ArenaStatus.Problem.RESET_REQUIRED));
    }

    private void send(ServerPlayerEntity player, ArenaStatus next) {
        boolean changed = status.state != next.state || status.problem != next.problem || status.percent != next.percent;
        status = next;
        if (changed || !announced || server.getTicks() % 20 == 0) {
            announced = true;
            emit(player, next);
        }
    }

    private void emit(ServerPlayerEntity player, ArenaStatus next) {
        try { publish.accept(player, next); }
        catch (RuntimeException notificationFailure) {
            LogManager.getLogger("HotbarSwapTrainer").warn("Could not send arena progress; inventory recovery is independent", notificationFailure);
        }
    }
}
