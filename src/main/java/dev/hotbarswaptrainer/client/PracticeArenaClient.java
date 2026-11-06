package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.arena.ArenaJournal;
import dev.hotbarswaptrainer.arena.ArenaLaunch;
import dev.hotbarswaptrainer.arena.ArenaSpec;
import dev.hotbarswaptrainer.arena.ArenaStatus;
import dev.hotbarswaptrainer.network.ArenaPackets;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.level.LevelInfo;
import org.apache.logging.log4j.LogManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Client-thread launch/progress/exit coordinator. Never reads server session or inventory objects. */
final class PracticeArenaClient {
    private ArenaStatus status = ArenaStatus.of(ArenaStatus.State.NONE, 0);
    private CompletableFuture<Boolean> preparation;
    private boolean launching, inArena, exiting, exitRequested;
    private Object connectionPlayer;
    private ArenaPackets.Action pendingAction;

    void initialize() {
        ClientSidePacketRegistry.INSTANCE.register(ArenaPackets.STATUS, (context, buffer) -> {
            final ArenaStatus next;
            try { next = ArenaPackets.readStatus(buffer); }
            catch (RuntimeException malformed) { return; }
            Object player = context.getPlayer();
            context.getTaskQueue().execute(() -> {
                if (MinecraftClient.getInstance().player == player) accept(next);
            });
        });
    }

    void enter() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (preparation != null || launching || client.world != null || client.getServer() != null) return;
        exitRequested = false;
        status = ArenaStatus.of(ArenaStatus.State.CHECKING, 0);
        client.openScreen(new PracticeArenaScreen(this));
        preparation = CompletableFuture.supplyAsync(() -> {
            try { return ArenaLaunch.prepare(client.runDirectory.toPath().resolve("saves")); }
            catch (Exception failure) { throw new CompletionException(failure); }
        });
    }

    void tick(MinecraftClient client) {
        if (preparation != null && preparation.isDone()) {
            CompletableFuture<Boolean> result = preparation; preparation = null;
            try {
                boolean create = result.join(); launching = true;
                if (create) {
                    LevelInfo level = new LevelInfo("Hotbar Swap Trainer", GameMode.SURVIVAL, false,
                            Difficulty.PEACEFUL, false, ArenaSpec.rules(), DataPackSettings.SAFE_MODE);
                    client.method_29607(ArenaJournal.DIRECTORY, level, RegistryTracker.create(), ArenaSpec.generator());
                } else client.startIntegratedServer(ArenaJournal.DIRECTORY);
                if (client.getServer() == null) throw new IllegalStateException("Vanilla did not start the arena server");
            } catch (RuntimeException failure) {
                boolean loadingFailed = launching;
                launching = false;
                LogManager.getLogger("HotbarSwapTrainer").error("Cannot open the owned practice arena", failure);
                status = ArenaStatus.error(loadingFailed ? ArenaStatus.Problem.LOAD : ArenaStatus.Problem.OWNERSHIP, false);
                client.openScreen(new PracticeArenaScreen(this));
            }
        }
        if (client.player != connectionPlayer) {
            connectionPlayer = client.player;
            if (client.player != null) request(ArenaPackets.Action.STATUS);
            else if (!launching) { inArena = false; exiting = false; exitRequested = false; pendingAction = null; status = ArenaStatus.of(ArenaStatus.State.NONE, 0); }
        }
        if (pendingAction != null && client.getNetworkHandler() != null) {
            ArenaPackets.Action action = pendingAction; pendingAction = null;
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            ArenaPackets.writeAction(buffer, action);
            ClientSidePacketRegistry.INSTANCE.sendToServer(ArenaPackets.REQUEST, buffer);
        }
    }

    private void accept(ArenaStatus next) {
        MinecraftClient client = MinecraftClient.getInstance();
        inArena = true; launching = false; status = next;
        if (next.state == ArenaStatus.State.EXIT_RESTORED) {
            if (exiting) request(ArenaPackets.Action.EXIT_COMMIT);
        } else if (next.state == ArenaStatus.State.EXIT_READY) {
            if (!exiting) return;
            // Vanilla waits for actual server-thread termination while continuing to render.
            client.world.disconnect();
            client.disconnect(new SaveLevelScreen(new TranslatableText("menu.savingLevel")));
            inArena = false; exiting = false; exitRequested = false;
            status = ArenaStatus.of(ArenaStatus.State.NONE, 0);
            client.openScreen(new TitleScreen());
        } else if (next.state == ArenaStatus.State.READY) {
            if (exitRequested && !exiting) { exit(); return; }
            if (!exiting && client.currentScreen instanceof PracticeArenaScreen) client.openScreen(null);
        } else {
            if (next.state == ArenaStatus.State.ERROR) exiting = false;
            if (!(client.currentScreen instanceof PracticeArenaScreen)) client.openScreen(new PracticeArenaScreen(this));
        }
    }

    void request(ArenaPackets.Action action) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        // END_CLIENT_TICK follows vanilla's selected-slot echo and all earlier input packets.
        pendingAction = action;
    }

    void reset() { status = ArenaStatus.of(ArenaStatus.State.BUILDING, 0); showAndRequest(ArenaPackets.Action.RESET); }
    void retry() {
        if (MinecraftClient.getInstance().world == null) { enter(); return; }
        if (status.problem == ArenaStatus.Problem.RECOVERY) {
            TrainerClient.retryPracticeRecovery(); request(ArenaPackets.Action.STATUS);
        } else if (exitRequested) {
            exit();
        } else {
            status = ArenaStatus.of(ArenaStatus.State.CHECKING, 0); showAndRequest(ArenaPackets.Action.RETRY);
        }
    }
    void exit() {
        if (exiting) return;
        if (MinecraftClient.getInstance().world == null) { MinecraftClient.getInstance().openScreen(new TitleScreen()); return; }
        exitRequested = true; exiting = true; showAndRequest(ArenaPackets.Action.EXIT);
    }
    private void showAndRequest(ArenaPackets.Action action) {
        MinecraftClient.getInstance().openScreen(new PracticeArenaScreen(this)); request(action);
    }

    ArenaStatus status() { return status; }
    boolean present() { return inArena; }
    boolean busy() { return preparation != null || launching || exiting; }
    boolean resetAvailable() { return status.state == ArenaStatus.State.READY || status.resetAllowed; }
    boolean canTrain() { return !exitRequested && (!inArena || status.state == ArenaStatus.State.READY); }
}
