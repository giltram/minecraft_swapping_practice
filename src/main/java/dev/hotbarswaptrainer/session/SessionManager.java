package dev.hotbarswaptrainer.session;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.RecoveryHolder;
import dev.hotbarswaptrainer.inventory.RecoveryRecord;
import dev.hotbarswaptrainer.input.PlayerActionTracker;
import dev.hotbarswaptrainer.network.ServerPackets;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileLoadResult;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import dev.hotbarswaptrainer.stats.SessionResult;
import dev.hotbarswaptrainer.stats.TaskResult;
import dev.hotbarswaptrainer.stats.ResultStore;
import dev.hotbarswaptrainer.task.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.util.*;

/** Sole owner of state transitions, validation, score freezing and recovery orchestration. */
public final class SessionManager {
    private static final Logger LOGGER = LogManager.getLogger("HotbarSwapTrainer");
    private final InventoryService inventory;
    private final ProfileRepository profiles;
    private final Map<UUID, TrainingSession> sessions = new HashMap<>();
    private final Set<UUID> announced = new HashSet<>();
    private final Set<UUID> recoveryFailed = new HashSet<>();
    private final ResultStore results;
    private final TaskValidator validator = new TaskValidator();
    private long revision;

    public SessionManager(InventoryService inventory, ProfileRepository profiles, ResultStore results) {
        this.inventory = inventory;
        this.profiles = profiles;
        this.results = results;
    }

    public void handle(ServerPlayerEntity player, TrainerProtocol.Request request) {
        if (request.action == TrainerProtocol.Action.START) { start(player, request); return; }
        TrainingSession session = sessions.get(player.getUuid());
        if (session == null) {
            if (request.action == TrainerProtocol.Action.STOP && InventoryService.isProtected(player)) recover(player);
            return;
        }
        if (!session.id.equals(request.sessionId) || request.revision <= 0 || request.revision > session.revision) return;
        if (request.action == TrainerProtocol.Action.STOP) { stop(player, "Stopped"); return; }
        if (session.task == null || !session.task.getId().equals(request.taskId)) return;
        switch (request.action) {
            case STOP: stop(player, "Stopped"); break;
            case FORBIDDEN: guardAction(player, "Item use or block interaction is not allowed"); break;
            case INTERRUPT:
                if (session.phase == TrainingSession.Phase.PREPARING) {
                    session.message = "Close screens, focus the game and release Swap Hands";
                    publish(player, session);
                } else invalidate(player, session, "Paused, unfocused or a screen was opened");
                break;
            case READY:
                if (session.phase == TrainingSession.Phase.PREPARING && prepared(player, session)) {
                    session.phase = TrainingSession.Phase.COUNTDOWN;
                    session.deadlineTick = tick(player) + 60;
                    publish(player, session);
                }
                break;
            case GO_ACK:
                if (session.phase == TrainingSession.Phase.GO && prepared(player, session)) {
                    session.startedNanos = System.nanoTime();
                    session.phase = TrainingSession.Phase.RUNNING;
                    publish(player, session);
                }
                break;
            default: break;
        }
    }

    private void start(ServerPlayerEntity player, TrainerProtocol.Request request) {
        TrainingSession existing = sessions.get(player.getUuid());
        if (existing != null) { publish(player, existing); return; }
        if (InventoryService.isProtected(player)) { errorLocked(player, "Recovery is active; press stop to retry it"); return; }
        MinecraftServer server = player.getServer();
        if (server.isDedicated() || server.isRemote() || !server.isHost(player.getGameProfile())
                || server.getPlayerManager().getCurrentPlayerCount() != 1) {
            error(player, "Training is restricted to a local single-player world with LAN closed"); return;
        }
        if (!Files.isRegularFile(server.getSavePath(WorldSavePath.ROOT).resolve(".hotbar-swap-trainer-test"))) {
            error(player, "Use a disposable world, then run /swaptrainer enable-test-world before training"); return;
        }
        if (player.currentScreenHandler != player.playerScreenHandler || !player.inventory.getCursorStack().isEmpty()
                || !player.isAlive() || player.isSpectator() || player.hasVehicle() || player.isUsingItem() || !player.isOnGround()
                || player.isTouchingWater() || player.isInLava()) {
            error(player, "Close screens and stand safely on dry ground before starting"); return;
        }
        net.minecraft.block.Block block = player.getServerWorld().getBlockState(player.getBlockPos()).getBlock();
        if (block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL) {
            error(player, "Leave the portal before starting training"); return;
        }
        if (request.goal < 0) { error(player, "Session length must be nonnegative (0 = unlimited)"); return; }
        ProfileLoadResult loaded = profiles.loadAll();
        LoadoutProfile selected = null;
        for (LoadoutProfile profile : loaded.getProfiles()) if (profile.getId().equals(request.profileId)) selected = profile;
        if (selected == null) { error(player, "Profile unavailable: " + String.join("; ", loaded.getErrors())); return; }
        TrainingSession session = new TrainingSession(player, selected, request.mode, request.goal);
        sessions.put(player.getUuid(), session);
        try {
            inventory.backup(player, session.id);
            prepare(player, session);
        } catch (Exception failure) {
            LOGGER.error("Training start failed", failure);
            stop(player, "Could not start: " + failure.getMessage());
        }
    }

    private void prepare(ServerPlayerEntity player, TrainingSession session) {
        long seed = session.random.nextLong();
        session.task = session.mode == TrainingMode.SINGLE_ITEM
                ? new SingleItemTaskGenerator().generate(session.profile, seed)
                : new FullInventoryTaskGenerator().generate(session.profile, seed);
        session.actions = new PlayerActionTracker();
        session.startedNanos = 0;
        session.elapsedNanos = 0;
        session.message = "Release Swap Hands and return to the game";
        session.phase = TrainingSession.Phase.PREPARING;
        session.deadlineTick = tick(player) + 1200;
        inventory.prepare(player, session.task.getInitial(), session.task.getInitialSelectedSlot());
        if (!publish(player, session)) throw new IllegalStateException("Could not send the prepared task to the client");
    }

    private boolean prepared(ServerPlayerEntity player, TrainingSession session) {
        return player.currentScreenHandler == player.playerScreenHandler
                && InventoryService.outsideTrainingAreaEmpty(player.inventory)
                && session.task.getInitial().equals(InventoryService.readLayout(player.inventory))
                && player.inventory.selectedSlot == session.task.getInitialSelectedSlot();
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (announced.add(player.getUuid())) {
                if (!InventoryService.isProtected(player)) idle(player, "Ready");
                else if (recoveryFailed.contains(player.getUuid())) errorLocked(player, "Recovery data is invalid; original record retained for inspection");
            }
            TrainingSession session = sessions.get(player.getUuid());
            if (session == null) {
                if (InventoryService.isProtected(player) && !recoveryFailed.contains(player.getUuid())) recover(player);
                continue;
            }
            try { session.environment.tick(player); }
            catch (RuntimeException unsafeEnvironment) { stop(player, unsafeEnvironment.getMessage()); continue; }
            if (server.isRemote() && session.phase != TrainingSession.Phase.ERROR_LOCKED) { stop(player, "LAN was opened"); continue; }
            int now = server.getTicks();
            switch (session.phase) {
                case COUNTDOWN:
                    if (now >= session.deadlineTick) {
                        session.phase = TrainingSession.Phase.GO;
                        session.deadlineTick = now + 200;
                        publish(player, session);
                    } else if (now % 20 == 0) publish(player, session);
                    break;
                case PREPARING: case GO:
                    if (now > session.deadlineTick) stop(player, "Preparation timed out");
                    break;
                case RUNNING:
                    if (!InventoryService.outsideTrainingAreaEmpty(player.inventory)
                            || validator.validate(session.task, InventoryService.readLayout(player.inventory)).isInvalid()) {
                        invalidate(player, session, "Inventory changed outside a legal swap");
                    } else if (now % 5 == 0) publish(player, session);
                    break;
                case SUCCESS: case INVALID:
                    if (now >= session.deadlineTick) {
                        if (session.goal > 0 && session.completed >= session.goal) stop(player, "Session complete");
                        else try { prepare(player, session); }
                        catch (Exception failure) { stop(player, "Task generation failed: " + failure.getMessage()); }
                    }
                    break;
                default: break;
            }
        }
    }

    public boolean beforeSwap(ServerPlayerEntity player) {
        if (!InventoryService.isProtected(player)) return true;
        TrainingSession session = sessions.get(player.getUuid());
        if (session != null && session.phase == TrainingSession.Phase.RUNNING && !player.isSpectator()) return true;
        guardAction(player, "Swap before GO or after task completion");
        return false;
    }

    public void afterSwap(ServerPlayerEntity player, InventoryLayout before) {
        TrainingSession session = sessions.get(player.getUuid());
        if (session == null || session.phase != TrainingSession.Phase.RUNNING) return;
        InventoryLayout after = InventoryService.readLayout(player.inventory);
        session.actions.swap(!before.equals(after));
        if (!InventoryService.outsideTrainingAreaEmpty(player.inventory)) { invalidate(player, session, "Non-training inventory changed"); return; }
        ValidationResult result = validator.validate(session.task, after);
        if (result.isInvalid()) invalidate(player, session, result.getReason());
        else if (result.isComplete()) {
            session.elapsedNanos = System.nanoTime() - session.startedNanos;
            session.results.add(result(session, TaskResult.Outcome.SUCCESS, ""));
            session.completed++;
            session.phase = TrainingSession.Phase.SUCCESS;
            session.message = "Success";
            session.deadlineTick = tick(player) + 15;
            publish(player, session); // Freeze success before the next packet in this same tick.
        }
    }

    public void afterSelectedSlot(ServerPlayerEntity player, int before) {
        if (before == player.inventory.selectedSlot || !InventoryService.isProtected(player)) return;
        TrainingSession session = sessions.get(player.getUuid());
        if (session != null && session.phase == TrainingSession.Phase.RUNNING) session.actions.selection();
        else if (session != null && session.phase == TrainingSession.Phase.PREPARING) {
            // A client may echo a held-slot update from restoration while setup is still synchronizing.
            // READY is the fence: before it, reassert setup and do not score initialization as input.
            player.inventory.selectedSlot = session.task.getInitialSelectedSlot();
            InventoryService.sync(player);
        }
        else guardAction(player, "Selected slot changed before GO");
    }

    public boolean guardAction(ServerPlayerEntity player, String reason) {
        if (!InventoryService.isProtected(player)) return false;
        TrainingSession session = sessions.get(player.getUuid());
        if (session != null && session.phase != TrainingSession.Phase.ERROR_LOCKED) {
            session.actions.forbidden();
            invalidate(player, session, reason);
        }
        InventoryService.sync(player);
        return true;
    }

    public boolean guardMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        if (!InventoryService.isProtected(player)) return false;
        TrainingSession session = sessions.get(player.getUuid());
        if (session != null) session.environment.observeMovement(player, packet);
        return true;
    }

    private void invalidate(ServerPlayerEntity player, TrainingSession session, String reason) {
        if (session.phase == TrainingSession.Phase.SUCCESS || session.phase == TrainingSession.Phase.INVALID
                || session.phase == TrainingSession.Phase.ERROR_LOCKED || session.task == null) return;
        session.elapsedNanos = session.startedNanos == 0 ? 0 : System.nanoTime() - session.startedNanos;
        session.results.add(result(session, TaskResult.Outcome.INVALID, reason));
        session.phase = TrainingSession.Phase.INVALID;
        session.message = reason;
        session.deadlineTick = tick(player) + 25;
        publish(player, session);
    }

    private TaskResult result(TrainingSession s, TaskResult.Outcome outcome, String reason) {
        return new TaskResult(s.task.getId(), outcome, s.elapsedNanos, s.actions.getAttempts(), s.actions.getEffective(),
                s.actions.getSelections(), s.actions.getNoOps(), s.actions.getForbidden(), reason);
    }

    public void stop(ServerPlayerEntity player, String reason) {
        TrainingSession session = sessions.get(player.getUuid());
        if (session != null) {
            if (session.phase == TrainingSession.Phase.RUNNING) {
                session.elapsedNanos = System.nanoTime() - session.startedNanos;
                session.results.add(result(session, TaskResult.Outcome.STOPPED, reason));
            }
            session.phase = TrainingSession.Phase.RESTORING;
            session.message = reason;
            publish(player, session);
        }
        try {
            inventory.restore(player);
            sessions.remove(player.getUuid());
            recoveryFailed.remove(player.getUuid());
            if (session != null) {
                SessionResult summary = new SessionResult(session.id, session.mode, session.profile.getHash(), session.goal, session.results);
                try { results.save(player.getUuid(), summary); }
                catch (java.io.IOException | RuntimeException statisticsFailure) {
                    LOGGER.error("Could not save statistics; inventory restoration already committed", statisticsFailure);
                }
                reason += String.format(Locale.ROOT, ": %d successes, average %.3fs, best %.3fs", summary.getSuccessCount(),
                        summary.getAverageNanos() / 1e9, summary.getBestNanos() / 1e9);
            }
            idle(player, reason);
        } catch (Exception failure) {
            LOGGER.error("Inventory recovery is locked; original NBT retained", failure);
            recoveryFailed.add(player.getUuid());
            if (session != null) { session.phase = TrainingSession.Phase.ERROR_LOCKED; session.message = failure.getMessage(); publish(player, session); }
            else errorLocked(player, failure.getMessage());
        }
    }

    public void onLoad(ServerPlayerEntity player, CompoundTag playerTag) {
        if (!playerTag.contains(RecoveryRecord.KEY)) return;
        CompoundTag raw = playerTag.getCompound(RecoveryRecord.KEY).copy();
        ((RecoveryHolder) player).trainer$setRecovery(raw);
        try { inventory.restoreLoaded(player, raw); }
        catch (Exception failure) {
            recoveryFailed.add(player.getUuid());
            LOGGER.error("Recovery data failed validation; player interactions are locked", failure);
        }
    }

    private void recover(ServerPlayerEntity player) { stop(player, "Original inventory recovered"); }
    public void disconnect(ServerPlayerEntity player) {
        if (InventoryService.isProtected(player)) stop(player, "Disconnected");
        sessions.remove(player.getUuid());
        announced.remove(player.getUuid());
        results.forget(player.getUuid());
    }
    public void shutdown(MinecraftServer server) {
        for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList()))
            if (InventoryService.isProtected(player)) stop(player, "Server stopping");
        announced.clear();
        sessions.clear();
        recoveryFailed.clear();
        results.clear();
    }

    private boolean publish(ServerPlayerEntity player, TrainingSession s) {
        s.revision = ++revision;
        TrainerProtocol.View.Builder view = new TrainerProtocol.View.Builder().session(s.id).revision(s.revision)
                .phase(s.phase.name()).message(limit(s.message)).profile(s.profile.getId()).mode(s.mode).progress(s.completed, s.goal)
                .countdown(s.phase == TrainingSession.Phase.COUNTDOWN ? Math.max(0, s.deadlineTick - tick(player)) : 0)
                .elapsed(s.phase == TrainingSession.Phase.RUNNING ? System.nanoTime() - s.startedNanos : s.elapsedNanos)
                .counts(s.actions.getAttempts(), s.actions.getEffective(), s.actions.getSelections(), s.actions.getNoOps(), s.actions.getForbidden());
        if (s.task != null) {
            List<String> hints = new ArrayList<>();
            int position = 0;
            for (ResolvedSlotRule slot : s.task.getSlotRules()) {
                SlotRule rule = slot.getRule();
                String hint = (position == 9 ? "Offhand" : "Hotbar " + (position + 1)) + ": " + rule.getKind().name();
                if (rule.getKind() == SlotRule.Kind.OPTIONAL) hint += slot.isPresent() ? " present" : " empty";
                List<String> alternatives = new ArrayList<>();
                for (StackSpec spec : rule.getAlternatives()) {
                    alternatives.add(spec.getTemplate().getItemId() + " x" + spec.getTemplate().getCount()
                            + (spec.getMatchNbt().isEmpty() ? "" : " " + spec.getMatchNbt()));
                }
                if (!alternatives.isEmpty()) hint += " | " + String.join(" or ", alternatives);
                hints.add(limit(hint));
                position++;
            }
            view.task(s.task.getId()).layouts(s.task.getInitial(), s.task.getTarget()).selectedSlot(s.task.getInitialSelectedSlot())
                    .destination(s.task.getDestination()).slotHints(hints);
        }
        return sendView(player, view.build());
    }
    private void idle(ServerPlayerEntity player, String message) {
        ProfileLoadResult loaded = profiles.loadAll();
        List<String> ids = profileIds(loaded);
        if (!loaded.getErrors().isEmpty()) message += " | " + String.join("; ", loaded.getErrors());
        sendView(player, new TrainerProtocol.View.Builder().revision(++revision).message(limit(message)).profileIds(ids).build());
    }
    private void error(ServerPlayerEntity player, String message) {
        LOGGER.info("Training request rejected: {}", message);
        sendView(player, new TrainerProtocol.View.Builder().revision(++revision).phase("ERROR").message(limit(message))
                .profileIds(profileIds(profiles.loadAll())).build());
    }
    private void errorLocked(ServerPlayerEntity player, String message) {
        sendView(player, new TrainerProtocol.View.Builder().revision(++revision).phase("ERROR_LOCKED").message(limit(message)).build());
    }
    private boolean sendView(ServerPlayerEntity player, TrainerProtocol.View view) {
        try { ServerPackets.send(player, view); return true; }
        catch (RuntimeException notificationFailure) {
            LOGGER.error("Could not send trainer HUD; recovery is independent of notifications", notificationFailure);
            return false;
        }
    }
    private static List<String> profileIds(ProfileLoadResult loaded) {
        List<String> ids = new ArrayList<>();
        for (LoadoutProfile profile : loaded.getProfiles()) if (ids.size() < 128) ids.add(profile.getId());
        return ids;
    }
    private static String limit(String text) { return text == null ? "Unknown error" : text.substring(0, Math.min(text.length(), 1900)); }
    private static int tick(ServerPlayerEntity player) { return player.getServer().getTicks(); }
}
