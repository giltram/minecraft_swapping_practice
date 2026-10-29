package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.inventory.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Armed only by an owned-world acceptance case; wraps the actual compressed file stream. */
public final class SaveWriteFault {
    private static String mode;
    private static ServerPlayerEntity player;
    private static Path output;
    private static boolean stopEntered;
    private static long stopEntries;
    private static final ThreadLocal<Boolean> selected = new ThreadLocal<>();

    private SaveWriteFault() { }

    static void arm(ServerPlayerEntity target, String requested, Path destination) {
        player = target; mode = requested; output = destination; stopEntered = false;
    }

    public static void enteringStop() { stopEntries++; if (mode != null) stopEntered = true; }
    static long stopEntries() { return stopEntries; }

    public static void begin(CompoundTag tag) {
        boolean host = mode != null && mode.endsWith("host");
        CompoundTag saved = host ? tag.getCompound("Data").getCompound("Player") : tag;
        selected.set(mode != null && mode.contains("write-") && saved.containsUuid("UUID")
                && saved.getUuid("UUID").equals(player.getUuid())
                && (host == tag.contains("Data")));
    }

    public static OutputStream wrap(OutputStream stream) {
        boolean inject = Boolean.TRUE.equals(selected.get());
        selected.remove();
        if (!inject) return stream;
        return new FilterOutputStream(stream) {
            private int written;
            @Override public void write(int value) throws IOException {
                out.write(value); written++;
                if (written >= 32) cut(written);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                int count = Math.min(length, 32 - written);
                out.write(bytes, offset, count); written += count;
                if (written >= 32) cut(written);
            }
            private void cut(int count) throws IOException {
                out.flush();
                halt("inside compressed save stream after " + count + " bytes", count);
            }
        };
    }

    public static void restoreBoundary(boolean afterSave) {
        if (mode == null || !stopEntered) return;
        if (mode.equals(afterSave ? "accept-stop-after-clear" : "accept-stop-before-clear")) {
            halt(afterSave ? "actual stop after confirmed clear save" : "actual stop after replacement before clear save", 0);
        }
    }

    private static void halt(String detail, int bytes) {
        String requested = mode;
        mode = null; // Evidence writes must never recursively inject.
        try {
            AcceptanceSupport.require(!requested.startsWith("accept-stop-") || stopEntered, "Cut bypassed SessionManager.stop");
            Map<String, Object> result = AcceptanceSupport.check(requested, detail);
            result.put("compressedBytesWritten", bytes); result.put("actualStopEntered", stopEntered);
            result.put("protectedInMemory", InventoryService.isProtected(player));
            AcceptanceSupport.write(output.resolve("accept-memory-before-halt.nbt"), player.toTag(new CompoundTag()));
            AcceptanceSupport.json(output.resolve("accept-cut.json"), result);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        Runtime.getRuntime().halt(91);
    }
}
