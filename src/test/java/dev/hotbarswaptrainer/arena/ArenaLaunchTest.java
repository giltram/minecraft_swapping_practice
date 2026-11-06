package dev.hotbarswaptrainer.arena;

import net.minecraft.Bootstrap;
import net.minecraft.datafixer.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.gen.GeneratorOptions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;
import static dev.hotbarswaptrainer.arena.ArenaJournalTest.expectFailure;

public class ArenaLaunchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { Bootstrap.initialize(); }
    private Path root() throws Exception { return temporary.getRoot().toPath().toRealPath(); }
    private ArenaJournal journal(Path saves) { return new ArenaJournal(saves.resolve(ArenaJournal.DIRECTORY)); }

    @Test public void preServerCreationCutResumesWithoutAdoptingAnUnknownSave() throws Exception {
        Path saves = root();
        assertTrue(ArenaLaunch.prepare(saves)); ArenaJournal journal = journal(saves);
        String id = journal.read().id;
        Files.write(journal.world().resolve("session.lock"), new byte[] {0});
        Files.createDirectory(journal.world().resolve("datapacks"));
        assertTrue(ArenaLaunch.prepare(saves)); assertEquals(id, journal.read().id);
        Files.createDirectory(journal.world().resolve("region"));
        expectFailure(() -> ArenaLaunch.prepare(saves));
        assertEquals(ArenaJournal.Phase.CREATING, journal.read().phase);
    }

    @Test public void unknownSameNameAndOldTestMarkerDoNotAuthorizeVanillaLaunch() throws Exception {
        Path world = Files.createDirectory(root().resolve(ArenaJournal.DIRECTORY));
        Files.write(world.resolve("level.dat"), new byte[] {3, 5});
        Files.write(world.resolve(".hotbar-swap-trainer-test"), new byte[] {1});
        expectFailure(() -> ArenaLaunch.prepare(root()));
        assertArrayEquals(new byte[] {3, 5}, Files.readAllBytes(world.resolve("level.dat")));
        assertFalse(Files.exists(world.resolve(ArenaJournal.FILE)));
    }

    @Test public void linkedWorldDataAndUnexpectedPreCreationContentFailClosed() throws Exception {
        Path saves = root(); ArenaLaunch.prepare(saves); ArenaJournal journal = journal(saves);
        Path external = Files.createDirectory(saves.resolve("external"));
        Files.createSymbolicLink(journal.world().resolve("region"), external);
        expectFailure(() -> ArenaLaunch.prepare(saves));
        Files.delete(journal.world().resolve("region"));
        Path unknown = journal.world().resolve("unknown.bin"); Files.write(unknown, new byte[] {2});
        expectFailure(() -> ArenaLaunch.prepare(saves)); assertArrayEquals(new byte[] {2}, Files.readAllBytes(unknown));
    }

    @Test public void createdButNotEnteredWorldLoadsInsteadOfBeingCreatedAgain() throws Exception {
        Path saves = root(); assertTrue(ArenaLaunch.prepare(saves));
        ArenaJournal journal = journal(saves); writeLevel(journal.world());
        assertFalse(ArenaLaunch.prepare(saves));
        assertTrue(ArenaSpec.matches(ArenaSpec.generator()));
        CompoundTag data = readLevel(journal.world()); data.getCompound("WorldGenSettings").putLong("seed", 99);
        saveLevel(journal.world(), data);
        byte[] raw = Files.readAllBytes(journal.world().resolve("level.dat"));
        expectFailure(() -> ArenaLaunch.prepare(saves));
        assertArrayEquals(raw, Files.readAllBytes(journal.world().resolve("level.dat")));
    }

    @Test public void readyWorldRequiresMatchingSealAndMissingDamagedUnsupportedOrReboundSealIsPreserved() throws Exception {
        Path saves = root(); assertTrue(ArenaLaunch.prepare(saves)); ArenaJournal journal = journal(saves);
        writeLevel(journal.world());
        ArenaJournal.Record record = journal.transition(journal.read(), ArenaJournal.Phase.INITIALIZING);
        record = journal.transition(record, ArenaJournal.Phase.VERIFYING);
        record = journal.transition(record, ArenaJournal.Phase.READY);
        expectFailure(() -> ArenaLaunch.prepare(saves));
        Path data = Files.createDirectory(journal.world().resolve("data"));
        Path seal = data.resolve(ArenaSeal.NAME + ".dat");
        CompoundTag valid = new ArenaSeal(record, journal.world()).toTag(new CompoundTag());
        saveSeal(seal, valid); assertFalse(ArenaLaunch.prepare(saves));
        for (String key : new String[] {"version", "id", "path", "spec", "operation"}) {
            CompoundTag bad = valid.copy(); bad.putString(key, "damaged"); saveSeal(seal, bad);
            byte[] raw = Files.readAllBytes(seal); expectFailure(() -> ArenaLaunch.prepare(saves));
            assertArrayEquals(raw, Files.readAllBytes(seal));
        }
        Files.write(seal, new byte[] {1, 2, 3}); expectFailure(() -> ArenaLaunch.prepare(saves));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(seal));
    }

    @Test public void pendingResetAcceptsOnlyItsPreviousOrCurrentSeal() throws Exception {
        Path saves = root(); ArenaLaunch.prepare(saves); ArenaJournal journal = journal(saves); writeLevel(journal.world());
        ArenaJournal.Record record = journal.transition(journal.read(), ArenaJournal.Phase.INITIALIZING);
        record = journal.transition(record, ArenaJournal.Phase.VERIFYING); record = journal.transition(record, ArenaJournal.Phase.READY);
        Path data = Files.createDirectory(journal.world().resolve("data")); Path file = data.resolve(ArenaSeal.NAME + ".dat");
        CompoundTag previous = new ArenaSeal(record, journal.world()).toTag(new CompoundTag()); saveSeal(file, previous);
        record = journal.transition(record, ArenaJournal.Phase.RESETTING);
        assertFalse(ArenaLaunch.prepare(saves));
        saveSeal(file, new ArenaSeal(record, journal.world()).toTag(new CompoundTag())); assertFalse(ArenaLaunch.prepare(saves));
        previous.putString("operation", java.util.UUID.randomUUID().toString()); saveSeal(file, previous);
        expectFailure(() -> ArenaLaunch.prepare(saves));
    }

    private static void writeLevel(Path world) throws Exception {
        CompoundTag data = new CompoundTag();
        data.put("WorldGenSettings", GeneratorOptions.CODEC.encodeStart(net.minecraft.util.dynamic.RegistryReadingOps.of(
                NbtOps.INSTANCE, net.minecraft.util.registry.RegistryTracker.create()), ArenaSpec.generator()).result().get());
        saveLevel(world, data);
    }
    private static CompoundTag readLevel(Path world) throws Exception {
        try (java.io.InputStream input = Files.newInputStream(world.resolve("level.dat"))) { return NbtIo.readCompressed(input).getCompound("Data"); }
    }
    private static void saveLevel(Path world, CompoundTag data) throws Exception {
        CompoundTag root = new CompoundTag(); root.put("Data", data);
        try (java.io.OutputStream output = Files.newOutputStream(world.resolve("level.dat"))) { NbtIo.writeCompressed(root, output); }
    }
    private static void saveSeal(Path file, CompoundTag data) throws Exception {
        CompoundTag root = new CompoundTag(); root.put("data", data);
        try (java.io.OutputStream output = Files.newOutputStream(file)) { NbtIo.writeCompressed(root, output); }
    }
}
