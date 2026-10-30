package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.nio.file.Path;
import java.util.*;

/** Dispatches bounded acceptance cases in the exact disposable smoke world. */
final class RemainingAcceptance {
    private RemainingAcceptance() { }

    static String run(ServerPlayerEntity player, InventorySnapshot baseline, String mode, Path output) throws Exception {
        if (mode.equals("accept-history")) return HistoryAcceptance.run(player, baseline, output);
        if (mode.equals("accept-protection")) return ProtectionAcceptance.run(player, baseline, output);
        if (mode.equals("accept-phase-guards")) return PhaseGuardAcceptance.run(player, baseline, output);
        if (mode.equals("accept-lan")) {
            AcceptanceSupport.startAt(player, "RUNNING");
            AcceptanceSupport.require(((net.minecraft.server.integrated.IntegratedServer) player.getServer())
                    .openToLan(net.minecraft.world.GameMode.SURVIVAL, false, 0), "LAN publication failed");
            TrainerMod.SESSIONS.tick(player.getServer());
            AcceptanceSupport.restored(player, baseline);
            TrainerMod.SESSIONS.handle(player, dev.hotbarswaptrainer.network.TrainerProtocol.Request.start(
                    dev.hotbarswaptrainer.task.TrainingMode.SINGLE_ITEM, "default", 0));
            AcceptanceSupport.require(AcceptanceSupport.session(player) == null && !InventoryService.isProtected(player), "LAN allowed a new transaction");
            return "Actual LAN publication stopped/restored training; subsequent start rejected";
        }
        if (mode.startsWith("accept-corrupt-")) {
            CorruptRecoveryAcceptance.prepare(player, baseline, mode.substring("accept-corrupt-".length()), output);
            return null;
        }
        if (mode.startsWith("accept-exit-")) {
            String[] parts = mode.substring("accept-exit-".length()).split("-", 2);
            AcceptanceSupport.startAt(player, parts[1].toUpperCase(Locale.ROOT));
            if (parts[1].equals("countdown") || parts[1].equals("success")) {
                // Preserve the requested observation phase while vanilla saves and the
                // client processes the real exit; normal feedback deadlines are untouched.
                AcceptanceSupport.set(AcceptanceSupport.session(player), "deadlineTick", player.getServer().getTicks() + 1200);
            }
            new ConfirmedPlayerSave().save(player);
            ExitObservation.arm(player, parts[0], parts[1].toUpperCase(Locale.ROOT), output);
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (parts[0].equals("shutdown")) client.scheduleStop();
                else client.getNetworkHandler().getConnection().disconnect(new LiteralText("Acceptance disconnect"));
            });
            return null;
        }
        if (mode.startsWith("accept-write-") || mode.startsWith("accept-stop-")) {
            AcceptanceSupport.startAt(player, "RUNNING");
            new ConfirmedPlayerSave().save(player);
            SaveWriteFault.arm(player, mode, output);
            if (mode.startsWith("accept-stop-")) TrainerMod.SESSIONS.stop(player, "Acceptance actual stop cut");
            else new ConfirmedPlayerSave().save(player);
            throw new IllegalStateException("Armed save cut did not fire: " + mode);
        }
        throw new IllegalArgumentException("Unknown remaining acceptance mode " + mode);
    }
}
