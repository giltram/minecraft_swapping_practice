package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.*;

/** Phase guards and server-side open-screen gates, independent of human input/HUD acceptance. */
final class PhaseGuardAcceptance {
    private PhaseGuardAcceptance() { }

    static String run(ServerPlayerEntity player, InventorySnapshot baseline, Path output) throws Exception {
        List<Map<String, Object>> checks = new ArrayList<>();
        for (String phase : Arrays.asList("PREPARING", "COUNTDOWN", "SUCCESS")) {
            AcceptanceSupport.startAt(player, phase);
            InventorySnapshot before = InventorySnapshot.capture(player.inventory);
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            AcceptanceSupport.require(before.equals(InventorySnapshot.capture(player.inventory)), "Non-running swap changed inventory");
            String expected = phase.equals("SUCCESS") ? "SUCCESS" : "INVALID";
            AcceptanceSupport.require(AcceptanceSupport.phase(player).equals(expected), "Non-running swap has wrong phase");
            TrainerMod.SESSIONS.stop(player, "Phase guard complete");
            AcceptanceSupport.restored(player, baseline);
            checks.add(AcceptanceSupport.check("swap at " + phase, "Blocked actual vanilla handler; success stays frozen; exact restore"));
        }
        for (String phase : Arrays.asList("PREPARING", "COUNTDOWN", "RUNNING")) {
            AcceptanceSupport.startAt(player, phase);
            AcceptanceSupport.signal(player, TrainerProtocol.Action.INTERRUPT);
            AcceptanceSupport.require(AcceptanceSupport.phase(player).equals(phase.equals("PREPARING") ? "PREPARING" : "INVALID"), "Interruption phase differs");
            TrainerMod.SESSIONS.stop(player, "Interruption protocol complete");
            AcceptanceSupport.restored(player, baseline);
            checks.add(AcceptanceSupport.check("INTERRUPT protocol at " + phase, "Server invalidation/preparation gate; no physical focus claim"));
        }
        AcceptanceSupport.startAt(player, "SUCCESS");
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync, inventory, owner) ->
                GenericContainerScreenHandler.createGeneric9x3(sync, inventory, new SimpleInventory(27)), new LiteralText("Acceptance screen")));
        AcceptanceSupport.set(AcceptanceSupport.session(player), "deadlineTick", player.getServer().getTicks() - 1);
        TrainerMod.SESSIONS.tick(player.getServer());
        AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("PREPARING"), "Feedback did not prepare a replacement");
        AcceptanceSupport.signal(player, TrainerProtocol.Action.READY);
        AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("PREPARING"), "Open screen bypassed READY");
        player.closeHandledScreen();
        AcceptanceSupport.signal(player, TrainerProtocol.Action.READY);
        AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("COUNTDOWN"), "Closed screen failed READY");
        TrainerMod.SESSIONS.stop(player, "Open-screen gate complete");
        AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("screen through feedback into next task", "Actual server screen blocks READY until close; no subjective GUI/readability claim"));
        AcceptanceSupport.startAt(player, "RUNNING");
        World original = player.world;
        player.changeDimension(player.getServer().getWorld(World.NETHER));
        AcceptanceSupport.require(player.world == original && AcceptanceSupport.phase(player).equals("INVALID"), "Dimension travel guard failed");
        TrainerMod.SESSIONS.stop(player, "Dimension guard complete");
        AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("dimension travel", "Actual changeDimension hook blocked travel without loading terrain"));
        AcceptanceSupport.json(output.resolve("phase-guard-checks.json"), checks);
        return checks.size() + " phase/protocol/open-screen/dimension checks passed";
    }
}
