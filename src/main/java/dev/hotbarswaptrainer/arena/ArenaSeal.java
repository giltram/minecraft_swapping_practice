package dev.hotbarswaptrainer.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Independently bound completion seal; atomic replacement avoids vanilla PersistentState's in-place writes. */
final class ArenaSeal {
    static final String NAME = "hotbar-swap-trainer-arena";
    private final CompoundTag value;

    ArenaSeal(ArenaJournal.Record record, Path world) {
        value = new CompoundTag();
        value.putInt("version", 1); value.putString("id", record.id);
        value.putString("operation", record.operation); value.putString("path", world.toString());
        value.putString("spec", ArenaSpec.ID);
    }

    CompoundTag toTag(CompoundTag tag) { return value.copy(); }

    static void commit(Path world, ArenaJournal.Record record) throws IOException {
        Path directory = world.resolve("data"); ArenaJournal.safePath(directory); Files.createDirectories(directory);
        Path target = directory.resolve(NAME + ".dat"); ArenaJournal.safePath(target);
        CompoundTag root = new CompoundTag(); root.put("data", new ArenaSeal(record, world).value);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(root, bytes);
        Path temporary = Files.createTempFile(directory, ".arena-seal-", ".tmp");
        try {
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        check(world, record, true);
    }

    static void check(Path world, ArenaJournal.Record record, boolean committed) throws IOException {
        Path file = world.resolve("data/" + NAME + ".dat");
        ArenaJournal.safePath(file);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !committed && record.firstOperation()
                && record.phase != ArenaJournal.Phase.READY) return;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing arena world seal");
        CompoundTag tag;
        try (InputStream input = Files.newInputStream(file)) { tag = NbtIo.readCompressed(input).getCompound("data"); }
        String operation = tag.getString("operation");
        if (!tag.contains("version", 3) || tag.getInt("version") != 1
                || !record.id.equals(tag.getString("id")) || !world.toString().equals(tag.getString("path"))
                || !ArenaSpec.ID.equals(tag.getString("spec"))
                || !(record.operation.equals(operation) || (!committed && record.phase != ArenaJournal.Phase.READY && record.previous.equals(operation))))
            throw new IOException("Arena world seal does not match its ownership journal");
    }
}
