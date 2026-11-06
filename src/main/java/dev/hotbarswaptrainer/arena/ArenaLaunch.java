package dev.hotbarswaptrainer.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.datafixer.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.util.dynamic.RegistryReadingOps;
import net.minecraft.util.registry.RegistryTracker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Disk-only preflight, completed BEFORE vanilla is allowed to open/write a save. */
public final class ArenaLaunch {
    private ArenaLaunch() { }

    public static boolean prepare(Path saves) throws IOException {
        ArenaJournal.safePath(saves);
        Files.createDirectories(saves);
        ArenaJournal journal = new ArenaJournal(saves.resolve(ArenaJournal.DIRECTORY));
        ArenaJournal.Record record = journal.openOrReserve();
        Files.walkFileTree(journal.world(), new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || !attributes.isRegularFile()) throw new IOException("Unsupported arena file: " + file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        Path level = journal.world().resolve("level.dat");
        ArenaJournal.safePath(level);
        if (!Files.exists(level, LinkOption.NOFOLLOW_LINKS)) {
            // Vanilla writes initial level.dat before starting the server/generating chunks.
            // A pre-server creation cut may leave session.lock/datapacks/temp files, all retained.
            if (record.phase != ArenaJournal.Phase.CREATING
                    || Files.exists(journal.world().resolve("level.dat_old"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(journal.world().resolve("region"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(journal.world().resolve("playerdata"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(journal.world().resolve("data"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Arena level data is missing; existing data retained");
            try (java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(journal.world())) {
                for (Path child : children) {
                    String name = child.getFileName().toString();
                    if (name.equals(ArenaJournal.FILE) || name.equals("session.lock") || name.matches("level[0-9]+\\.dat")
                            || name.matches("\\.arena-journal-.*\\.tmp")) continue;
                    if (name.equals("datapacks") && Files.isDirectory(child)) {
                        try (java.nio.file.DirectoryStream<Path> packs = Files.newDirectoryStream(child)) {
                            if (!packs.iterator().hasNext()) continue;
                        }
                    }
                    throw new IOException("Unexpected data in an unfinished arena creation: " + child);
                }
            }
            return true;
        }
        checkGenerator(journal.world());
        ArenaSeal.check(journal.world(), record, false);
        return false;
    }

    static void checkGenerator(Path world) throws IOException {
        Path level = world.resolve("level.dat");
        ArenaJournal.safePath(level);
        CompoundTag data;
        try (InputStream input = Files.newInputStream(level)) { data = NbtIo.readCompressed(input).getCompound("Data"); }
        Tag expected = GeneratorOptions.CODEC.encodeStart(RegistryReadingOps.of(NbtOps.INSTANCE, RegistryTracker.create()), ArenaSpec.generator()).result()
                .orElseThrow(() -> new IOException("Cannot encode pinned arena generator"));
        if (!expected.equals(data.get("WorldGenSettings"))) throw new IOException("Saved arena generator does not match its ownership specification");
    }
}
