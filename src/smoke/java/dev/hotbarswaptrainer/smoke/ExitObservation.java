package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.Map;

/** Records the phase at the real lifecycle hook, not merely before queueing an exit. */
public final class ExitObservation {
    private static String kind, expected;
    private static Path output;
    private static boolean observed;

    static void arm(ServerPlayerEntity player, String exitKind, String phase, Path destination) throws Exception {
        kind = exitKind; expected = phase; output = destination; observed = false;
        AcceptanceSupport.require(InventoryService.isProtected(player), "Exit lacks an active recovery transaction");
        AcceptanceSupport.require(AcceptanceSupport.phase(player).equals(expected), "Exit requested in the wrong phase");
        AcceptanceSupport.json(output.resolve("exit-requested.json"), AcceptanceSupport.check(kind + " from " + expected,
                "Server confirmed the request phase and active recovery before queueing the real client exit"));
    }

    public static void observe(ServerPlayerEntity player, String hook) {
        if (output == null || observed || (!kind.equals(hook) && !(kind.equals("shutdown") && hook.equals("disconnect")))) return;
        observed = true;
        try {
            String actual = AcceptanceSupport.phase(player);
            Map<String, Object> result = AcceptanceSupport.check(kind + " at " + expected,
                    "Real client exit requested in the stated phase; vanilla may READY/interrupt before player removal");
            result.put("expectedPhase", expected); result.put("actualPhase", actual);
            result.put("requestPhase", expected);
            result.put("requestedExit", kind); result.put("actualLifecycleHook", hook);
            result.put("protected", InventoryService.isProtected(player));
            boolean allowed = actual.equals(expected) || actual.equals("INVALID")
                    || (expected.equals("PREPARING") && actual.equals("COUNTDOWN"));
            if (!allowed || !InventoryService.isProtected(player)) result.put("status", "failed");
            AcceptanceSupport.json(output.resolve("exit-observed.json"), result);
            if (kind.equals("disconnect")) MinecraftClient.getInstance().execute(MinecraftClient.getInstance()::scheduleStop);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
