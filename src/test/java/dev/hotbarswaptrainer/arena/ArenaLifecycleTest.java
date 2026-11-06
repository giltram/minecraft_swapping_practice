package dev.hotbarswaptrainer.arena;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.Assert.*;
import static dev.hotbarswaptrainer.arena.ArenaJournalTest.expectFailure;

public class ArenaLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ArenaJournal journal(String name) throws Exception {
        Path root = temporary.getRoot().toPath().toRealPath();
        ArenaJournal journal = new ArenaJournal(root.resolve(name)); journal.openOrReserve(); return journal;
    }

    @Test public void initializeAndRepeatedResetsKeepIdentityAndExactBounds() throws Exception {
        ArenaJournal journal = journal("owned"); String id = journal.read().id;
        Terrain terrain = new Terrain(); complete(new ArenaLifecycle(journal, terrain, false));
        String operation = journal.read().operation;
        for (int repeat = 0; repeat < 3; repeat++) {
            terrain.cells[31] = 9;
            complete(new ArenaLifecycle(journal, terrain, true));
            assertEquals(id, journal.read().id); assertNotEquals(operation, journal.read().operation);
            operation = journal.read().operation;
            assertEquals(ArenaJournal.Phase.READY, journal.read().phase);
            assertEquals(0, terrain.cells[31]);
        }
    }

    @Test public void reuseInspectsWithoutWritingAndChangedGroundRequiresExplicitReset() throws Exception {
        ArenaJournal journal = journal("owned"); Terrain terrain = new Terrain();
        complete(new ArenaLifecycle(journal, terrain, false)); int writes = terrain.writes;
        complete(new ArenaLifecycle(journal, terrain, false)); assertEquals(writes, terrain.writes);
        terrain.cells[32] = 7;
        expectFailure(() -> complete(new ArenaLifecycle(journal, terrain, false)));
        assertEquals(7, terrain.cells[32]); assertEquals(ArenaJournal.Phase.READY, journal.read().phase);
        complete(new ArenaLifecycle(journal, terrain, true)); assertEquals(0, terrain.cells[32]);
    }

    @Test public void initializeAndResetResumeAtEveryCriticalBoundary() throws Exception {
        for (boolean reset : new boolean[] {false, true}) for (int cut : new int[] {0, 1, 63, 64, 65, 127, 128, 129, 140, 145}) {
            ArenaJournal journal = journal("cut-" + reset + "-" + cut); Terrain terrain = new Terrain();
            if (reset) { complete(new ArenaLifecycle(journal, terrain, false)); Arrays.fill(terrain.cells, 8); }
            ArenaLifecycle interrupted = new ArenaLifecycle(journal, terrain, reset);
            for (int tick = 0; tick < cut; tick++) interrupted.tick();
            String identity = journal.read().id;
            complete(new ArenaLifecycle(new ArenaJournal(journal.world()), terrain, false));
            assertEquals(identity, journal.read().id); assertEquals(ArenaJournal.Phase.READY, journal.read().phase);
            for (int cell : terrain.cells) assertEquals(0, cell);
        }
    }

    @Test public void failedSaveOrReadbackNeverCommitsReadyAndRetryReplaysWrites() throws Exception {
        for (boolean readback : new boolean[] {false, true}) {
            ArenaJournal journal = journal("save-" + readback); Terrain terrain = new Terrain();
            terrain.failSave = !readback; terrain.failReadback = readback;
            expectFailure(() -> complete(new ArenaLifecycle(journal, terrain, false)));
            assertEquals(ArenaJournal.Phase.VERIFYING, journal.read().phase);
            int writes = terrain.writes;
            terrain.failSave = false; terrain.failReadback = false;
            complete(new ArenaLifecycle(journal, terrain, false));
            assertEquals(writes + 256, terrain.writes); assertEquals(ArenaJournal.Phase.READY, journal.read().phase);
        }
    }

    @Test public void failedOwnershipValidationPrecedesAllMutation() throws Exception {
        ArenaJournal journal = journal("owned"); Terrain terrain = new Terrain(); terrain.invalidOwnership = true;
        expectFailure(() -> new ArenaLifecycle(journal, terrain, false));
        assertEquals(0, terrain.writes); assertEquals(0, terrain.configured);
        assertEquals(ArenaJournal.Phase.CREATING, journal.read().phase);
    }

    @Test public void cannotResetAnUnfinishedOperationOrSkipJournalPhases() throws Exception {
        ArenaJournal journal = journal("owned"); Terrain terrain = new Terrain();
        expectFailure(() -> new ArenaLifecycle(journal, terrain, true));
        expectFailure(() -> journal.transition(journal.read(), ArenaJournal.Phase.READY));
        assertEquals(0, terrain.writes);
    }

    private static void complete(ArenaLifecycle operation) throws Exception {
        for (int tick = 0; tick < 200; tick++) if (operation.tick().state == ArenaStatus.State.READY) return;
        fail("Operation did not finish within its bounded work");
    }

    private static final class Terrain implements ArenaLifecycle.Terrain {
        final int[] cells = new int[256];
        int[] saved; int writes, configured;
        boolean failSave, failReadback, invalidOwnership;
        Terrain() { Arrays.fill(cells, 8); }
        @Override public void validate(ArenaJournal.Record record) throws IOException { if (invalidOwnership) throw new IOException("unowned"); }
        @Override public void configure() { configured++; }
        @Override public void applySection(int unit) { assertTrue(unit >= 0 && unit < cells.length); cells[unit] = 0; writes++; }
        @Override public boolean checkSection(int unit) { assertTrue(unit >= 0 && unit < cells.length); return cells[unit] == 0; }
        @Override public void save(ArenaJournal.Record record) throws IOException { if (failSave) throw new IOException("write denied"); saved = cells.clone(); }
        @Override public void checkSavedChunk(int chunk) throws IOException {
            if (failReadback) throw new IOException("disk mismatch");
            assertTrue(chunk >= 0 && chunk < 16);
            for (int unit = chunk * 16; unit < (chunk + 1) * 16; unit++) assertEquals(0, saved[unit]);
        }
    }
}
