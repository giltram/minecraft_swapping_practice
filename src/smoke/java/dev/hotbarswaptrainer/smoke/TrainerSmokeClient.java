package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.ConfirmedPlayerSave;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.inventory.RecoveryHolder;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.stats.ResultStore;
import dev.hotbarswaptrainer.stats.SessionResult;
import dev.hotbarswaptrainer.stats.TaskResult;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelInfo;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional development mod, excluded from production. Reflection is deliberately confined
 * here: verification must not add test commands or inventory backdoors to the shipped mod.
 */
public final class TrainerSmokeClient implements ClientModInitializer {
    private static final String WORLD = "hst-smoke";
    private static final String OWNED = "Hotbar Swap Trainer automated disposable smoke world v1";
    private final String mode = System.getProperty("hotbarswaptrainer.smoke", "");
    private Path output;
    private boolean opened, ownedWorldRequested, seeded, firstNoOp, rejectedDrop;
    private volatile boolean finished;
    private int stage, waitTicks, sentTasks;
    private UUID lastTask;
    private volatile InventorySnapshot baseline;
    private String lastPhase = "", lastClientDiagnostic = "", lastScreen = "";
    private volatile InventoryTask diagnosticTask;
    private volatile PendingStart pendingStart;
    private volatile long lastStartAttemptNanos;
    private int startAttempts;
    private boolean awaitingStart;
    private int startWaitTicks;
    private long started;

    @Override public void onInitializeClient() {
        if (mode.isEmpty()) return;
        if (mode.startsWith("accept-") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
            throw new IllegalArgumentException("Remaining acceptance modes require the independent production launcher");
        }
        if (!mode.startsWith("accept-") && !Arrays.asList("normal", "normal-crash", "crash", "recover", "faults",
                "cut-backup", "cut-setup", "cut-restored", "cut-cleared").contains(mode)) throw new IllegalArgumentException("Unknown smoke mode " + mode);
        output = MinecraftClient.getInstance().runDirectory.toPath().resolve("smoke");
        try { Files.createDirectories(output); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
        started = System.nanoTime();
        event("boot", mode);
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(this::serverTick);
    }

    private void clientTick(MinecraftClient client) {
        if (finished) return;
        // Options exist by the first tick, after the client initializer. This affects
        // only this isolated process and is never written to the options file here.
        if (inventoryOnly()) client.options.pauseOnLostFocus = false;
        String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName()
                + ": " + client.currentScreen.getTitle().getString();
        if (!screen.equals(lastScreen)) { lastScreen = screen; event("screen", screen); }
        if (confirmOwnedWorld(client)) return;
        if (client.player != null) {
            ownedWorldRequested = false;
            PendingStart request = pendingStart;
            long now = System.nanoTime();
            if (request != null && (lastStartAttemptNanos == 0 || now - lastStartAttemptNanos >= 1_000_000_000L)
                    && client.getNetworkHandler() != null && client.player.isOnGround()
                    && client.currentScreen == null && client.isWindowFocused() && !client.options.keySwapHands.isPressed()
                    && baseline != null && baseline.equals(InventorySnapshot.capture(client.player.inventory))) {
                // Match a real P press: wait for restore/initial sync, then send START from
                // client END tick after vanilla has queued any held-slot echo for this frame.
                try {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    TrainerProtocol.writeRequest(buf, TrainerProtocol.Request.start(request.mode, "default", request.goal));
                    lastStartAttemptNanos = now;
                    ClientSidePacketRegistry.INSTANCE.sendToServer(TrainerProtocol.REQUEST, buf);
                    event("start-request", request.mode + " goal=" + request.goal + " attempt=" + (++startAttempts)
                            + " onGround=" + client.player.isOnGround());
                } catch (RuntimeException failure) { fail(failure); }
            }
            InventoryTask task = diagnosticTask;
            String diagnostic = "focused=" + client.isWindowFocused() + ", paused=" + client.isPaused()
                    + ", screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName())
                    + ", swapHeld=" + client.options.keySwapHands.isPressed()
                    + ", onGround=" + client.player.isOnGround()
                    + ", selected=" + client.player.inventory.selectedSlot
                    + ", initialMatches=" + (task != null && task.getInitial().equals(InventoryService.readLayout(client.player.inventory)));
            if (!diagnostic.equals(lastClientDiagnostic)) {
                lastClientDiagnostic = diagnostic;
                event("client-state", diagnostic);
            }
        }
        if (opened || !(client.currentScreen instanceof TitleScreen)) return;
        opened = true;
        try {
            Path world = client.runDirectory.toPath().resolve("saves").resolve(WORLD);
            Path marker = world.resolve(".hst-smoke-owned");
            if (Files.exists(world)) {
                if (!Files.isRegularFile(marker) || !new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim().equals(OWNED)) {
                    throw new IllegalStateException("Refusing existing world without exact smoke ownership marker: " + world);
                }
                ownedWorldRequested = true;
                client.startIntegratedServer(WORLD);
            } else {
                if (mode.equals("recover")) throw new IllegalStateException("Recovery mode requires the previous crash-mode save");
                Files.createDirectories(world);
                Files.write(marker, Arrays.asList(OWNED), StandardCharsets.UTF_8);
                GameRules rules = new GameRules();
                rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
                GeneratorOptions defaults = GeneratorOptions.getDefaultOptions();
                GeneratorOptions flat = new GeneratorOptions(731001L, false, false,
                        GeneratorOptions.method_28608(defaults.getDimensionMap(), new FlatChunkGenerator(FlatChunkGeneratorConfig.getDefaultConfig())));
                LevelInfo info = new LevelInfo("HST automated smoke", GameMode.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, rules, DataPackSettings.SAFE_MODE);
                client.method_29607(WORLD, info, RegistryTracker.create(), flat);
            }
            event("world-opened", world.toString());
        } catch (Throwable failure) { fail(failure); }
    }

    /** Accept only the experimental warning raised by our verified disposable-world request. */
    private boolean confirmOwnedWorld(MinecraftClient client) {
        if (!ownedWorldRequested || !(client.currentScreen instanceof BackupPromptScreen)
                || !(client.currentScreen.getTitle() instanceof TranslatableText)
                || !((TranslatableText) client.currentScreen.getTitle()).getKey().equals("selectWorld.backupQuestion.experimental")) return false;
        for (Element child : client.currentScreen.children()) {
            if (!(child instanceof ButtonWidget)) continue;
            ButtonWidget button = (ButtonWidget) child;
            if (button.getMessage() instanceof TranslatableText
                    && ((TranslatableText) button.getMessage()).getKey().equals("selectWorld.backupJoinSkipButton")) {
                event("owned-world-confirmed", "Continue loading the verified disposable world; keep erase-cache disabled");
                ownedWorldRequested = false;
                button.onPress();
                return true;
            }
        }
        return false;
    }

    private void serverTick(MinecraftServer server) {
        if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
        try {
            requireOwnedWorld(server);
            ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
            if (++waitTicks > 20 * 240) throw new IllegalStateException("Smoke timed out; keep the game window focused for real READY/GO handshakes");
            if (mode.startsWith("accept-")) {
                if (!seeded) {
                    if (!player.isOnGround()) return;
                    // Vanilla LAN publication also updates the local client's permission level.
                    if (mode.equals("accept-lan") && MinecraftClient.getInstance().player == null) return;
                    if (mode.equals("accept-corrupt-check")) {
                        succeed(mode, CorruptRecoveryAcceptance.check(player, output));
                        return;
                    }
                    if (InventoryService.isProtected(player)) throw new IllegalStateException("Recover before a new acceptance case");
                    seedInventory(player);
                    seeded = true;
                    Files.write(server.getSavePath(WorldSavePath.ROOT).resolve(".hotbar-swap-trainer-test"), Arrays.asList("Automated disposable smoke test"), StandardCharsets.UTF_8);
                    String detail = RemainingAcceptance.run(player, baseline, mode, output);
                    if (detail != null) succeed(mode, detail);
                    else finished = true; // A checked intentional halt or real client exit is scheduled.
                }
                return;
            }
            if (mode.equals("recover")) { checkRecovered(player); return; }
            if (!seeded) {
                if (!inventoryOnly() && (!player.isOnGround() || player.isTouchingWater()
                        || player.currentScreenHandler != player.playerScreenHandler)) return;
                if (InventoryService.isProtected(player)) throw new IllegalStateException("Normal/crash mode cannot overwrite an active recovery; run recover first");
                seedInventory(player);
                seeded = true;
                Files.write(server.getSavePath(WorldSavePath.ROOT).resolve(".hotbar-swap-trainer-test"), Arrays.asList("Automated disposable smoke test"), StandardCharsets.UTF_8);
                if (mode.equals("faults")) {
                    succeed("faults", FaultAcceptance.run(player, output));
                    return;
                }
                if (mode.startsWith("cut-")) {
                    String detail = InventoryCrashCut.prepare(player, baseline, mode, output);
                    writeResult(mode, "ready", detail);
                    event("crash-ready", mode + ": " + detail);
                    Runtime.getRuntime().halt(91);
                    return;
                }
                begin(player, TrainingMode.SINGLE_ITEM, 3);
                return;
            }
            Object session = session(player);
            if (awaitingStart) {
                if (session == null) {
                    if (++startWaitTicks > 20 * 120) throw new IllegalStateException("Smoke START was not accepted; inspect client focus/sync and server rejection logs");
                    return;
                }
                awaitingStart = false;
                pendingStart = null;
            }
            if (session == null) {
                verifySessionFinished(player);
                return;
            }
            String phase = field(session, "phase").toString();
            InventoryTask observedTask = (InventoryTask) field(session, "task");
            diagnosticTask = observedTask;
            String phaseKey = phase + ":" + (observedTask == null ? "none" : observedTask.getId());
            if (!phaseKey.equals(lastPhase)) {
                lastPhase = phaseKey;
                event("phase", phase + " task=" + (observedTask == null ? "none" : observedTask.getId())
                        + " message=" + field(session, "message"));
            }
            if (phase.equals("ERROR_LOCKED")) throw new IllegalStateException("Recovery locked: " + field(session, "message"));
            if (phase.equals("INVALID")) event("invalid", field(session, "message").toString());
            if ((mode.equals("crash") || (mode.equals("normal-crash") && stage == 4)) && phase.equals("RUNNING")) {
                new ConfirmedPlayerSave().save(player);
                if (!InventoryService.isProtected(player)) throw new IllegalStateException("Crash point lacks active recovery");
                event("crash-ready", "Both authoritative files contain training inventory and the active original snapshot");
                writeResult("crash", "ready", "Runtime.halt(91) after confirmed training save");
                Runtime.getRuntime().halt(91);
            }
            if (stage == 3 && phase.equals("SUCCESS") && ((Integer) field(session, "completed")) >= 2) {
                TrainerMod.SESSIONS.stop(player, "Smoke unlimited stop");
                return;
            }
            if (!phase.equals("RUNNING")) return;
            InventoryTask task = (InventoryTask) field(session, "task");
            if (task.getId().equals(lastTask)) return;
            lastTask = task.getId();
            if (stage == 2 && !rejectedDrop) {
                rejectedDrop = true;
                sendPackets(task, true, false);
                return;
            }
            boolean noOp = stage == 0 && !firstNoOp;
            firstNoOp |= noOp;
            sentTasks++;
            sendPackets(task, false, noOp);
        } catch (Throwable failure) { fail(failure); }
    }

    private boolean inventoryOnly() {
        return mode.equals("recover") || mode.equals("faults") || mode.startsWith("cut-") || mode.startsWith("accept-");
    }

    private void begin(ServerPlayerEntity player, TrainingMode training, int goal) throws Exception {
        lastTask = null;
        lastPhase = "";
        event("begin", training + " goal=" + goal + " stage=" + stage);
        awaitingStart = true;
        startWaitTicks = 0;
        lastStartAttemptNanos = 0;
        pendingStart = new PendingStart(training, goal);
    }

    private static final class PendingStart {
        final TrainingMode mode;
        final int goal;
        PendingStart(TrainingMode mode, int goal) { this.mode = mode; this.goal = goal; }
    }

    private void seedInventory(ServerPlayerEntity player) throws IOException {
        player.inventory.clear();
        for (int i = 0; i < 36; i++) player.inventory.main.set(i, stack(Items.COBBLESTONE, i + 1, i));
        Item[] armor = {Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET};
        for (int i = 0; i < armor.length; i++) player.inventory.armor.set(i, stack(armor[i], 1, 36 + i));
        player.inventory.offHand.set(0, stack(Items.TOTEM_OF_UNDYING, 1, 40));
        player.inventory.selectedSlot = 6;
        player.inventory.setCursorStack(ItemStack.EMPTY);
        baseline = InventorySnapshot.capture(player.inventory);
        CompoundTag baselineTag = baseline.toTag();
        baselineTag.putUuid("Player", player.getUuid());
        try (OutputStream stream = Files.newOutputStream(output.resolve("baseline.nbt"))) { NbtIo.writeCompressed(baselineTag, stream); }
        new ConfirmedPlayerSave().save(player);
        InventoryService.sync(player);
        event("baseline", "41 distinct stack values and selected hotbar slot 6 saved");
    }

    private static ItemStack stack(Item item, int count, int position) {
        ItemStack value = new ItemStack(item, count);
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("SmokeSlot", position);
        nbt.putString("SmokeText", "exact original stack " + position);
        value.setTag(nbt);
        return value;
    }

    private void sendPackets(InventoryTask task, boolean drop, boolean noOp) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null || client.getNetworkHandler() == null || finished) return;
            if (drop) {
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                return;
            }
            if (noOp) {
                int empty = 0;
                while (!task.getInitial().get(empty).isEmpty()) empty++;
                client.player.inventory.selectedSlot = empty;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(empty));
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            }
            // One client queue task intentionally sends every legal witness operation together.
            // This exercises the exact vanilla packet hooks, including same-tick success capture.
            for (int selected : task.getSolution()) {
                client.player.inventory.selectedSlot = selected;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selected));
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            }
            // A further swap must not erase a success reached earlier in this packet batch.
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        });
    }

    private void verifySessionFinished(ServerPlayerEntity player) throws Exception {
        if (InventoryService.isProtected(player) || !baseline.equals(InventorySnapshot.capture(player.inventory))) {
            throw new IllegalStateException("Original 41 slots/selection were not restored at stage " + stage);
        }
        ResultStore store = (ResultStore) field(TrainerMod.SESSIONS, "results");
        SessionResult result = store.findLatest(player.getUuid()).orElseThrow(() -> new IllegalStateException("Missing session result"));
        int expected = stage < 2 ? 3 : stage == 2 ? 1 : 2;
        if (result.getSuccessCount() != expected) throw new IllegalStateException("Expected " + expected + " successes, got " + result.getSuccessCount());
        if (stage == 0 && result.getTasks().stream().mapToInt(TaskResult::getNoOpSwaps).sum() != 1) throw new IllegalStateException("No-op accounting differs from the injected one no-op");
        if (stage == 2 && result.getTasks().stream().filter(r -> r.getOutcome() == TaskResult.Outcome.INVALID).count() != 1) throw new IllegalStateException("Forbidden drop did not create exactly one invalid task");
        new ConfirmedPlayerSave().save(player);
        event("session-passed", "stage=" + stage + " successes=" + expected + " restored all 41 slots/selection");
        stage++;
        if (stage == 1) begin(player, TrainingMode.FULL_INVENTORY, 3);
        else if (stage == 2) begin(player, TrainingMode.SINGLE_ITEM, 1);
        else if (stage == 3) begin(player, TrainingMode.SINGLE_ITEM, 0);
        else {
            String detail = "Single3, Full3, forbidden-drop replacement, unlimited-stop, no-op, same-batch swaps and complete restoration passed; sent tasks=" + sentTasks;
            if (mode.equals("normal-crash")) {
                writeResult("normal", "passed", detail);
                event("normal-passed", detail);
                begin(player, TrainingMode.SINGLE_ITEM, 1);
            } else succeed("normal", detail);
        }
    }

    private void checkRecovered(ServerPlayerEntity player) throws Exception {
        if (baseline == null) {
            CompoundTag tag;
            try (InputStream stream = Files.newInputStream(output.resolve("baseline.nbt"))) { tag = NbtIo.readCompressed(stream); }
            if (!tag.getUuid("Player").equals(player.getUuid())) throw new IllegalStateException("Recovery launch used a different player UUID; launch both modes with --username SmokePlayer");
            baseline = InventorySnapshot.fromTag(tag);
        }
        if (InventoryService.isProtected(player)) return;
        if (!baseline.equals(InventorySnapshot.capture(player.inventory))) throw new IllegalStateException("Crash recovery did not restore the exact original snapshot");
        if (((RecoveryHolder) player).trainer$getRecovery() != null) throw new IllegalStateException("Recovery record was not cleared");
        new ConfirmedPlayerSave().save(player);
        succeed("recover", "Crash-saved training inventory restored all41 slots/selection and active marker cleared in both authoritative files");
    }

    private static void requireOwnedWorld(MinecraftServer server) throws IOException {
        Path root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        Path marker = root.resolve(".hst-smoke-owned");
        if (!root.getFileName().toString().equals(WORLD) || !Files.isRegularFile(marker)
                || !new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim().equals(OWNED)) {
            throw new IllegalStateException("Smoke driver refuses any non-owned world: " + root);
        }
    }

    @SuppressWarnings("unchecked") private static Object session(ServerPlayerEntity player) throws Exception {
        return ((Map<UUID, ?>) field(TrainerMod.SESSIONS, "sessions")).get(player.getUuid());
    }
    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private void succeed(String phase, String detail) {
        finished = true;
        writeResult(phase, "passed", detail);
        event("passed", detail);
        if (Boolean.parseBoolean(System.getProperty("hotbarswaptrainer.smoke.exit", "true"))) {
            MinecraftClient.getInstance().execute(MinecraftClient.getInstance()::scheduleStop);
        }
    }
    private void fail(Throwable failure) {
        if (finished) return;
        finished = true;
        LogManager.getLogger("HotbarSwapTrainerSmoke").error("Smoke failed", failure);
        writeResult(mode, "failed", failure.toString());
        event("failed", failure.toString());
        // Leave the client/save available for inspection; never delete failed test data.
    }
    private void writeResult(String phase, String status, String detail) {
        String json = "{\"mode\":" + quote(phase) + ",\"status\":" + quote(status) + ",\"detail\":" + quote(detail)
                + ",\"elapsedSeconds\":" + (System.nanoTime() - started) / 1e9 + "}\n";
        try { Files.write(output.resolve("result-" + phase + ".json"), json.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    private void event(String kind, String detail) {
        String json = "{\"event\":" + quote(kind) + ",\"detail\":" + quote(detail) + "}\n";
        try { Files.write(output.resolve("events.jsonl"), json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
