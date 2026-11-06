package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.arena.*;
import dev.hotbarswaptrainer.client.ArenaUiSmokeAccess;
import dev.hotbarswaptrainer.course.*;
import dev.hotbarswaptrainer.environment.*;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.stats.*;
import dev.hotbarswaptrainer.task.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.nio.file.*;
import java.util.*;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Excluded programmatic position driver, NOT physical input or human route acceptance. */
final class ParkourAcceptance {
    private final BasicCourse geometry = new BasicCourse(BasicCourse.SEED);
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private String mode;
    private Path run, output;
    private volatile int stage;
    private volatile boolean failed;
    private volatile boolean tamperObserved;
    private InventorySnapshot baseline;
    private long started;
    private int groundTicks;
    private String identity;

    void initialize(String mode) {
        this.mode = mode; started = System.nanoTime();
        run = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize(); output = run.resolve("smoke");
        try {
            require(run.getFileName().toString().startsWith("run-arena-") && run.toRealPath().equals(run)
                    && new String(Files.readAllBytes(run.resolve(".hst-production-owned")), java.nio.charset.StandardCharsets.UTF_8).trim()
                    .equals("Hotbar Swap Trainer isolated production smoke instance v1"), "Unowned Parkour test instance");
            Files.createDirectories(output);
        } catch (Exception e) { throw new IllegalStateException(e); }
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(this::serverTick);
    }
    private void clientTick(MinecraftClient client) {
        if (failed) return;
        client.options.pauseOnLostFocus = false;
        try {
            require(System.nanoTime() - started < 240_000_000_000L, "Parkour timeout stage=" + stage);
            if (stage == 0 && client.currentScreen instanceof TitleScreen) { ArenaUiSmokeAccess.enterViaTitleButton(); stage = 1; }
            else if (stage == 2) { ArenaUiSmokeAccess.convert(true); stage = 3; }
            else if (stage == 5) { ArenaUiSmokeAccess.reset(); stage = 6; }
            else if (stage == 8) { ArenaUiSmokeAccess.exit(); stage = 9; }
            else if (stage == 9 && client.world == null && client.getServer() == null && client.currentScreen instanceof TitleScreen) {
                ArenaUiSmokeAccess.enterViaTitleButton(); stage = 10;
            } else if (stage == 12) { ArenaUiSmokeAccess.convert(false); stage = 13; }
            else if (stage == 15) { ArenaUiSmokeAccess.exit(); stage = 16; }
            else if (stage == 16 && client.world == null && client.getServer() == null && client.currentScreen instanceof TitleScreen) {
                json(output.resolve("parkour-result.json"), report("passed", "Programmatic positions, same-path reopen, real exit; human completion remains pending"));
                stage = 17; client.scheduleStop();
            }
        } catch (Exception e) { fail(e); }
    }
    private void serverTick(MinecraftServer server) {
        if (failed || server.getPlayerManager().getPlayerList().isEmpty()) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
        try {
            Path world = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            require(world.equals(run.resolve("saves").resolve(ArenaJournal.DIRECTORY)), "Unexpected save");
            ArenaJournal.Record record = new ArenaJournal(world).read();
            if (mode.equals("accept-arena-parkour-cut-convert") && record.version == 2 && record.phase == ArenaJournal.Phase.RESETTING) halt("conversion pending after bounded batch");
            if (stage == 6 && mode.equals("accept-arena-parkour-cut-reset") && record.phase == ArenaJournal.Phase.RESETTING) halt("course reset pending after bounded batch");
            if (ArenaUiSmokeAccess.status().state == ArenaStatus.State.ERROR && !tamperObserved) throw new IllegalStateException("Arena error " + ArenaUiSmokeAccess.status().problem);
            if (stage == 1 && record.phase == ArenaJournal.Phase.READY && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY
                    && player.isOnGround() && ++groundTicks > 5) {
                identity = record.id;
                Path file = output.resolve("arena-baseline.nbt");
                if (Files.exists(file)) { baseline = InventorySnapshot.fromTag(read(file)); restored(player, baseline); }
                else {
                    ArenaAcceptance.seed(player); baseline = InventorySnapshot.capture(player.inventory); new ConfirmedPlayerSave().save(player);
                    net.minecraft.nbt.CompoundTag tag = baseline.toTag(); tag.putUuid("Player", player.getUuid()); write(file, tag);
                }
                stage = record.layout.parkour() ? 3 : 2;
            }
            if (stage == 3 && record.layout.parkour() && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY && player.isOnGround()) {
                require(record.version == 2 && record.id.equals(identity), "Conversion lost identity/version");
                checks.add(check("converted READY", "v2 exact course passed memory/save readback; stable world identity; legacy ownership archive retained"));
                if (mode.equals("accept-arena-parkour-disconnect")) {
                    start(player, TrainingMode.FULL_INVENTORY, 0);
                    move(player, player.getX(), player.getY() + 0.3, player.getZ(), false);
                    require(!player.isOnGround(), "Disconnect fixture is not airborne");
                    ExitObservation.arm(player, "disconnect", "RUNNING", output);
                    ParkourDisconnectChecks.arm(baseline, output);
                    stage = 18;
                    MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().getNetworkHandler().getConnection()
                            .disconnect(new net.minecraft.text.LiteralText("M3-C course disconnect")));
                    return;
                }
                suite(player);
                byte[] journalBefore = Files.readAllBytes(world.resolve(ArenaJournal.FILE));
                net.minecraft.util.math.BlockPos missing = new net.minecraft.util.math.BlockPos(0, BasicCourse.BLOCK_Y, 0);
                server.getOverworld().setBlockState(missing, net.minecraft.block.Blocks.AIR.getDefaultState());
                tamperObserved = true;
                TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM, "default", 1, false, TrainingEnvironment.PARKOUR));
                require(session(player) == null && !InventoryService.isProtected(player) && baseline.equals(InventorySnapshot.capture(player.inventory)), "Damaged course START touched inventory");
                require(server.getOverworld().getBlockState(missing).isAir() && Arrays.equals(journalBefore, Files.readAllBytes(world.resolve(ArenaJournal.FILE))), "Damaged course was silently repaired");
                checks.add(check("tampered course START", "Missing start support rejected before inventory backup; original inventory/journal/terrain retained; explicit reset required"));
                stage = 5;
            }
            if (stage == 6 && record.phase == ArenaJournal.Phase.READY && record.layout.parkour()
                    && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY && player.isOnGround()) {
                // Wait for the client request to reach the server rather than mistaking old READY for completion.
                if (groundTicks++ < 15) return;
                restored(player, baseline); require(record.id.equals(identity), "Reset changed identity");
                checks.add(check("course reset", "Bounded course reset retained layout/identity and original inventory")); tamperObserved = false; stage = 8;
            }
            if (stage == 10 && record.phase == ArenaJournal.Phase.READY && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY && player.isOnGround()) {
                restored(player, baseline); require(record.layout.parkour() && record.id.equals(identity), "Course failed same-path reopen");
                checks.add(check("course same-path reopen", "v2 journal/seal/saved blocks reused after actual save-close-load; recovery resolved first")); stage = 12;
            }
            if (stage == 13 && record.phase == ArenaJournal.Phase.READY && !record.layout.parkour()
                    && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY && player.isOnGround()) {
                restored(player, baseline); require(record.id.equals(identity), "Flat conversion changed identity");
                checks.add(check("return to flat", "Explicit UI confirmation rebuilt flat template and passed independent saved readback")); stage = 15;
            }
        } catch (Exception e) { fail(e); }
    }
    private void suite(ServerPlayerEntity player) throws Exception {
        int combination = 0;
        for (TrainingMode taskMode : TrainingMode.values()) for (int requested : new int[] {0, 1, 7}) {
            start(player, taskMode, requested); CourseAttempt attempt = course(player);
            require((Integer) field(session(player), "goal") == 1, "Parkour did not freeze one task");
            if (mode.equals("accept-arena-parkour-cut-training")) { new ConfirmedPlayerSave().save(player); halt("active course and same-path original inventory backup"); }
            if (combination++ % 2 == 0) {
                solve(player); String task = ((InventoryTask) field(session(player), "task")).getId().toString();
                TrainerMod.SESSIONS.tick(player.getServer()); require(phase(player).equals("SUCCESS"), "Inventory-first ended course");
                fall(player); require(phase(player).equals("SUCCESS") && ((InventoryTask) field(session(player), "task")).getId().toString().equals(task), "Fall replaced solved task");
                completeCourse(player);
            } else {
                completeCourse(player); require(phase(player).equals("RUNNING"), "Course-first ended inventory task");
                long frozen = attempt.elapsed(System.nanoTime()); TrainerMod.SESSIONS.tick(player.getServer());
                require(frozen == attempt.elapsed(System.nanoTime()), "Finish time continued while solving task"); solve(player);
            }
            set(session(player), "deadlineTick", player.getServer().getTicks() - 1); TrainerMod.SESSIONS.tick(player.getServer());
            restored(player, baseline); SessionResult result = ((ResultStore) field(TrainerMod.SESSIONS, "results")).findLatest(player.getUuid()).get();
            require(result.getRequestedSuccesses() == 1 && result.getSuccessCount() == 1 && result.getCourse().completed
                    && result.getMetadata().endState == SessionMetadata.EndState.COMPLETED, "Independent completion/history differs");
            checks.add(check(taskMode + " requested=" + requested, "One task; both completion orderings; vanilla swap solution; confirmed two-file original inventory; separate course result"));
        }
        start(player, TrainingMode.FULL_INVENTORY, 0);
        // Exercise an actual non-final partial solution, not just the pristine SETUP layout.
        InventoryTask partial = (InventoryTask) field(session(player), "task");
        InventoryLayout initial = InventoryService.readLayout(player.inventory);
        boolean moved = false;
        for (int slot = 0; slot < 9; slot++) {
            InventoryLayout after = initial.withSwap(slot);
            if (after.equals(initial) || new TaskValidator().validate(partial, after).isComplete()) continue;
            player.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(slot));
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN));
            moved = true; break;
        }
        require(moved && phase(player).equals("RUNNING") && !initial.equals(InventoryService.readLayout(player.inventory)), "Partial-solution fixture did not change inventory");
        dev.hotbarswaptrainer.input.PlayerActionTracker actions = (dev.hotbarswaptrainer.input.PlayerActionTracker) field(session(player), "actions");
        int swaps = actions.getAttempts(), selections = actions.getSelections();
        Object backup = ((RecoveryHolder) player).trainer$getRecovery().copy();
        long taskStarted = (Long) field(session(player), "startedNanos");
        Object id = ((InventoryTask) field(session(player), "task")).getId();
        InventorySnapshot training = InventorySnapshot.capture(player.inventory);
        fall(player);
        require(taskStarted == (Long) field(session(player), "startedNanos") && id.equals(((InventoryTask) field(session(player), "task")).getId())
                && training.equals(InventorySnapshot.capture(player.inventory)) && backup.equals(((RecoveryHolder) player).trainer$getRecovery())
                && actions.getAttempts() == swaps && actions.getSelections() == selections, "Fall changed partial task/timer/input/backup");
        require(!player.damage(DamageSource.OUT_OF_WORLD, 1000) && player.isAlive(), "Training death protection failed");
        signal(player, TrainerProtocol.Action.INTERRUPT); require(phase(player).equals("INVALID"), "Interruption not invalidated");
        set(session(player), "deadlineTick", player.getServer().getTicks() - 1); TrainerMod.SESSIONS.tick(player.getServer()); running(player);
        require(backup.equals(((RecoveryHolder) player).trainer$getRecovery()) && course(player).falls() == 1, "Interruption changed backup/course progress");
        TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
        checks.add(check("fall and interruption", "Actual partial solution/input counts/task/GO_ACK start/backup preserved across fall; duplicate/stale teleport rejection; lethal damage blocked; interruption re-SETUP; exact stop restore"));

        start(player, TrainingMode.SINGLE_ITEM, 1); CourseAttempt attempt = course(player);
        player.networkHandler.requestTeleport(geometry.finish().centerX(), BasicCourse.FEET_Y, geometry.finish().centerZ(), 0, 0);
        confirm(player); move(player, player.getX(), player.getY(), player.getZ(), true);
        player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket((Integer) field(player.networkHandler, "field_14123")));
        require(attempt.platform() == 0 && attempt.phase() != CourseAttempt.Phase.FINISHED, "Teleport to finish awarded completion");
        TrainerMod.SESSIONS.tick(player.getServer()); confirm(player); move(player, player.getX(), player.getY(), player.getZ(), true);
        TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
        checks.add(check("teleport cannot finish", "Actual server teleport/confirmation to finish did not advance; returned to last authorized platform"));
        ArenaRecoveryChecks.run(player, baseline, checks, TrainingEnvironment.PARKOUR);
    }
    private void start(ServerPlayerEntity player, TrainingMode mode, int requested) throws Exception {
        player.networkHandler.requestTeleport(0.5, BasicCourse.FEET_Y, 0.5, 0, 0); confirm(player); player.setOnGround(true);
        TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(mode, "default", requested, false, TrainingEnvironment.PARKOUR));
        require(phase(player).equals("PREPARING"), "Parkour START refused"); confirm(player); player.setOnGround(true); running(player);
    }
    private void running(ServerPlayerEntity player) throws Exception {
        signal(player, TrainerProtocol.Action.READY); require(phase(player).equals("COUNTDOWN"), "READY refused");
        set(session(player), "deadlineTick", player.getServer().getTicks() - 1); TrainerMod.SESSIONS.tick(player.getServer());
        signal(player, TrainerProtocol.Action.GO_ACK); require(phase(player).equals("RUNNING"), "GO_ACK refused");
    }
    private CourseAttempt course(ServerPlayerEntity player) throws Exception { return ((SessionEnvironment) field(session(player), "environment")).courseAttempt(); }
    private void completeCourse(ServerPlayerEntity player) throws Exception {
        CourseAttempt attempt = course(player);
        for (int i = attempt.platform() + 1; i < geometry.platforms.size(); i++) {
            BasicCourse.Platform from = geometry.platforms.get(i - 1), to = geometry.platforms.get(i);
            for (int step = 1; step <= 10; step++) {
                double t = step / 10.0;
                move(player, from.centerX() + (to.x - from.x) * t, BasicCourse.FEET_Y + (step == 10 ? 0 : 0.8),
                        from.centerZ() + (to.z - from.z) * t, step == 10);
            }
            require(attempt.platform() == i, "Accepted route step did not advance " + i + ", got=" + attempt.platform() + ", phase=" + attempt.phase() + ", pos=" + player.getPos());
        }
        require(attempt.phase() == CourseAttempt.Phase.FINISHED, "Course did not finish");
    }
    private void fall(ServerPlayerEntity player) throws Exception {
        CourseAttempt attempt = course(player); int falls = attempt.falls(), checkpoint = attempt.platform();
        double x = player.getX(), z = player.getZ(); BasicCourse.Platform next = geometry.platforms.get(1);
        double dx = -next.z / 4.0, dz = next.x / 4.0;
        for (int i = 1; i <= 6; i++) move(player, x + dx * i * 0.5, BasicCourse.FEET_Y, z + dz * i * 0.5, false);
        for (int i = 1; i <= 4; i++) move(player, player.getX(), BasicCourse.FEET_Y - i * 0.4, player.getZ(), false);
        TrainerMod.SESSIONS.tick(player.getServer());
        require(attempt.falls() == falls + 1 && attempt.phase() == CourseAttempt.Phase.RETURNING, "Fall not detected " + player.getPos());
        int teleport = (Integer) field(player.networkHandler, "field_14123");
        player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(teleport - 1));
        move(player, geometry.finish().centerX(), BasicCourse.FEET_Y, geometry.finish().centerZ(), true);
        require(!attempt.isConfirmed() && attempt.platform() == checkpoint, "Old position/stale confirm advanced course");
        confirm(player); move(player, player.getX(), player.getY(), player.getZ(), true);
        TrainerMod.SESSIONS.tick(player.getServer());
        require(attempt.phase() == CourseAttempt.Phase.RUNNING && attempt.falls() == falls + 1 && attempt.platform() == checkpoint, "Return re-arming failed");
    }
    private static void move(ServerPlayerEntity p, double x, double y, double z, boolean ground) { p.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.Both(x, y, z, p.yaw, p.pitch, ground)); }
    private static void confirm(ServerPlayerEntity p) throws Exception {
        if (field(p.networkHandler, "field_14119") != null) p.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket((Integer) field(p.networkHandler, "field_14123")));
        p.networkHandler.syncWithPlayerPosition();
    }
    private Map<String, Object> report(String status, String detail) { Map<String, Object> r = new LinkedHashMap<>(); r.put("status", status); r.put("mode", mode); r.put("detail", detail); r.put("checks", checks); return r; }
    private void halt(String detail) throws Exception { json(output.resolve("parkour-cut.json"), report("ready-to-halt91", detail)); Runtime.getRuntime().halt(91); }
    private void fail(Exception e) {
        failed = true;
        try { json(output.resolve("parkour-result.json"), report("failed", e.toString())); } catch (Exception write) { e.addSuppressed(write); }
        org.apache.logging.log4j.LogManager.getLogger("HotbarSwapTrainer").error("Parkour acceptance failed; retaining evidence", e);
        MinecraftClient.getInstance().execute(MinecraftClient.getInstance()::scheduleStop);
    }
}
