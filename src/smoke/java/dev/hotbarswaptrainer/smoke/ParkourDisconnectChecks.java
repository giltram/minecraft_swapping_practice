package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.stats.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.nio.file.Path;
import java.util.*;

/** Actual disconnect-tail assertion; no production lifecycle dependency on this fixture. */
public final class ParkourDisconnectChecks {
    private static InventorySnapshot baseline;
    private static Path output;
    private ParkourDisconnectChecks() { }
    static void arm(InventorySnapshot original, Path destination) { baseline = original; output = destination; }
    public static void after(ServerPlayerEntity player) {
        if (output == null) return;
        try {
            AcceptanceSupport.restored(player, baseline);
            HistoryStore store = (HistoryStore) AcceptanceSupport.field(TrainerMod.SESSIONS, "results");
            SessionResult result = store.read(player.getUuid()).records.get(0);
            AcceptanceSupport.require(result.getCourse() != null && !result.getCourse().completed
                    && result.getMetadata().endState == SessionMetadata.EndState.DISCONNECTED, "Disconnect course result differs");
            Map<String, Object> report = new LinkedHashMap<>(); report.put("status", "passed"); report.put("mode", "accept-arena-parkour-disconnect");
            report.put("checks", Collections.singletonList(AcceptanceSupport.check("actual airborne disconnect", "Actual client connection close reached session disconnect; both saved inventories restored; course unfinished; DISCONNECTED history")));
            AcceptanceSupport.json(output.resolve("parkour-result.json"), report); output = null;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
