package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.arena.ArenaJournal;
import net.minecraft.client.MinecraftClient;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact server-side READY commit, before PracticeArenaService can publish readiness to the client. */
public final class ArenaReadyCut {
    private ArenaReadyCut() { }
    public static void observe(ArenaJournal.Record record) throws Exception {
        if (!"accept-arena-cut-ready".equals(System.getProperty("hotbarswaptrainer.smoke", ""))
                || record.phase != ArenaJournal.Phase.READY) return;
        Path run = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();
        Path world = run.resolve("saves").resolve(ArenaJournal.DIRECTORY);
        AcceptanceSupport.require(new ArenaJournal(world).read().equals(record), "READY cut is not in the owned original world");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "ready-to-halt91"); report.put("mode", "accept-arena-cut-ready");
        report.put("detail", "READY committed on server before service can publish client readiness");
        report.put("checks", java.util.Collections.emptyList());
        AcceptanceSupport.json(run.resolve("smoke/arena-cut.json"), report);
        Runtime.getRuntime().halt(91);
    }
}
