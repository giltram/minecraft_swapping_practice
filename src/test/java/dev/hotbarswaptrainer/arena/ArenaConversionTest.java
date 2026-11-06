package dev.hotbarswaptrainer.arena;

import net.minecraft.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;
import static dev.hotbarswaptrainer.arena.ArenaJournalTest.expectFailure;

public class ArenaConversionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { Bootstrap.initialize(); }
    private ArenaJournal ready() throws Exception {
        ArenaJournal journal = new ArenaJournal(temporary.getRoot().toPath().toRealPath().resolve("owned"));
        ArenaJournal.Record r = journal.openOrReserve(); r = journal.transition(r, ArenaJournal.Phase.INITIALIZING);
        r = journal.transition(r, ArenaJournal.Phase.VERIFYING); ArenaSeal.commit(journal.world(), r);
        journal.transition(r, ArenaJournal.Phase.READY); return journal;
    }
    @Test public void legacyReadDoesNotRewriteAndExplicitConversionArchivesExactOriginals() throws Exception {
        ArenaJournal j = ready(); ArenaJournal.Record old = j.read();
        byte[] raw = Files.readAllBytes(j.world().resolve(ArenaJournal.FILE));
        byte[] seal = Files.readAllBytes(j.world().resolve("data/" + ArenaSeal.NAME + ".dat"));
        assertEquals(1, j.read().version); assertSame(ArenaLayout.FLAT, j.read().layout);
        assertArrayEquals(raw, Files.readAllBytes(j.world().resolve(ArenaJournal.FILE)));
        ArenaJournal.Record r = j.convert(old, ArenaLayout.PARKOUR);
        assertEquals(2, r.version); assertEquals(old.id, r.id); assertEquals(old.operation, r.previous);
        assertSame(ArenaLayout.PARKOUR, r.layout); assertSame(ArenaLayout.FLAT, r.source);
        assertEquals(ArenaSpec.BOUNDS, "overworld:-32..31,0..255,-32..31");
        assertArrayEquals(raw, Files.readAllBytes(j.world().resolve(".arena-ownership-" + old.operation + ".json")));
        assertArrayEquals(seal, Files.readAllBytes(j.world().resolve("data/.arena-seal-" + old.operation + ".dat")));
        ArenaSeal.check(j.world(), r, false); expectFailure(() -> j.convert(old, ArenaLayout.FLAT));
        r = j.transition(r, ArenaJournal.Phase.VERIFYING); ArenaSeal.check(j.world(), r, false);
        ArenaSeal.commit(j.world(), r); r = j.transition(r, ArenaJournal.Phase.READY); ArenaSeal.check(j.world(), r, true);
        ArenaJournal.Record back = j.convert(r, ArenaLayout.FLAT); assertSame(ArenaLayout.PARKOUR, back.source);
        ArenaSeal.check(j.world(), back, false); assertEquals(old.id, back.id);
    }
    @Test public void unsupportedLayoutRetainsRawDataAndCannotAuthorizeConversion() throws Exception {
        ArenaJournal j = ready(); j.convert(j.read(), ArenaLayout.PARKOUR);
        Path file = j.world().resolve(ArenaJournal.FILE);
        byte[] bad = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)
                .replace("basic-parkour-v1", "unknown-course-v99").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, bad); expectFailure(j::read); assertArrayEquals(bad, Files.readAllBytes(file));
    }
    @Test public void layoutAddsPlatformsButLeavesBaseAndBoundsUnchanged() {
        dev.hotbarswaptrainer.course.BasicCourse c = new dev.hotbarswaptrainer.course.BasicCourse(dev.hotbarswaptrainer.course.BasicCourse.SEED);
        for (int y = 0; y < 256; y++) for (int x = -32; x <= 31; x++) for (int z = -32; z <= 31; z++) {
            assertEquals(ArenaSpec.blockAt(y), ArenaLayout.FLAT.blockAt(x, y, z));
            if (c.blockPlatform(x, y, z) == null) assertEquals(ArenaSpec.blockAt(y), ArenaLayout.PARKOUR.blockAt(x, y, z));
            else assertFalse(ArenaLayout.PARKOUR.blockAt(x, y, z).isAir());
        }
    }
    @Test public void courseStartPreflightIsBoundedAndRejectsMissingSupportAndBlockedJumpClearance() {
        final int[] calls = {0};
        assertTrue(ArenaLayout.PARKOUR.checkCourse(pos -> {
            calls[0]++; assertTrue(pos.getX() >= -32 && pos.getX() <= 31 && pos.getZ() >= -32 && pos.getZ() <= 31);
            return ArenaLayout.PARKOUR.blockAt(pos.getX(), pos.getY(), pos.getZ());
        }));
        assertEquals(1305, calls[0]);
        assertFalse(ArenaLayout.PARKOUR.checkCourse(pos -> pos.getX() == 0 && pos.getY() == 8 && pos.getZ() == 0
                ? net.minecraft.block.Blocks.AIR.getDefaultState() : ArenaLayout.PARKOUR.blockAt(pos.getX(), pos.getY(), pos.getZ())));
        assertFalse(ArenaLayout.PARKOUR.checkCourse(pos -> pos.getX() == 0 && pos.getY() == 12 && pos.getZ() == 0
                ? net.minecraft.block.Blocks.STONE.getDefaultState() : ArenaLayout.PARKOUR.blockAt(pos.getX(), pos.getY(), pos.getZ())));
    }
    @Test public void conflictingArchiveFailsBeforeJournalIntentAndRetainsBothSources() throws Exception {
        ArenaJournal j = ready(); ArenaJournal.Record old = j.read();
        byte[] original = Files.readAllBytes(j.world().resolve(ArenaJournal.FILE));
        Path archive = j.world().resolve(".arena-ownership-" + old.operation + ".json");
        Files.write(archive, new byte[] {7, 9});
        expectFailure(() -> j.convert(old, ArenaLayout.PARKOUR));
        assertArrayEquals(original, Files.readAllBytes(j.world().resolve(ArenaJournal.FILE)));
        assertArrayEquals(new byte[] {7, 9}, Files.readAllBytes(archive));
        ArenaSeal.check(j.world(), old, true);
    }
    @Test public void wrongLayoutSeedVersionAndUnknownSealFieldsArePreservedAndRejected() throws Exception {
        ArenaJournal j = ready(); ArenaJournal.Record r = j.convert(j.read(), ArenaLayout.PARKOUR);
        r = j.transition(r, ArenaJournal.Phase.VERIFYING); ArenaSeal.commit(j.world(), r); r = j.transition(r, ArenaJournal.Phase.READY);
        final ArenaJournal.Record ready = r;
        Path file = j.world().resolve("data/" + ArenaSeal.NAME + ".dat");
        net.minecraft.nbt.CompoundTag original = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(Files.readAllBytes(file)));
        for (String key : new String[] {"layout", "seed", "version", "unknown"}) {
            net.minecraft.nbt.CompoundTag bad = original.copy();
            if (key.equals("layout")) bad.getCompound("data").putString(key, "unknown-course-v2");
            else if (key.equals("seed")) bad.getCompound("data").putLong(key, 99);
            else if (key.equals("version")) bad.getCompound("data").putInt(key, 3);
            else bad.getCompound("data").putString(key, "preserve raw");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); net.minecraft.nbt.NbtIo.writeCompressed(bad, bytes);
            Files.write(file, bytes.toByteArray()); expectFailure(() -> ArenaSeal.check(j.world(), ready, true));
            assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(file));
        }
    }
    @Test public void v2CannotInventAFirstOperationOrCreationPhase() throws Exception {
        ArenaJournal j = ready(); ArenaJournal.Record converted = j.convert(j.read(), ArenaLayout.PARKOUR);
        Path file = j.world().resolve(ArenaJournal.FILE);
        String valid = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        for (String bad : new String[] {valid.replace(converted.previous, "none"),
                valid.replace("RESETTING", "INITIALIZING"), valid.replace(converted.previous, converted.operation)}) {
            byte[] raw = bad.getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(file, raw);
            expectFailure(j::read); assertArrayEquals(raw, Files.readAllBytes(file));
        }
    }
}
