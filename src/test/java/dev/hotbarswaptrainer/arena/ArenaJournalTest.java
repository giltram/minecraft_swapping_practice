package dev.hotbarswaptrainer.arena;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class ArenaJournalTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path root() throws IOException { return temporary.getRoot().toPath().toRealPath(); }

    @Test public void reservesOnceAndReusesStablePathAndIdentity() throws Exception {
        ArenaJournal journal = new ArenaJournal(root().resolve(ArenaJournal.DIRECTORY));
        ArenaJournal.Record first = journal.openOrReserve();
        assertEquals(ArenaJournal.Phase.CREATING, first.phase);
        assertEquals(first, new ArenaJournal(journal.world()).openOrReserve());
        assertFalse(Files.exists(journal.world().resolve(".hotbar-swap-trainer-test")));
    }

    @Test public void existingUnknownOrdinaryAndOldTestDirectoriesStayUntouched() throws Exception {
        for (String kind : new String[] {"unknown", "ordinary", "old-test"}) {
            Path world = Files.createDirectory(root().resolve(kind));
            Path precious = world.resolve("level.dat"); Files.write(precious, new byte[] {7, 9, 11});
            if (kind.equals("old-test")) Files.write(world.resolve(".hotbar-swap-trainer-test"), new byte[] {1});
            expectFailure(() -> new ArenaJournal(world).openOrReserve());
            assertArrayEquals(new byte[] {7, 9, 11}, Files.readAllBytes(precious));
            assertFalse(Files.exists(world.resolve(ArenaJournal.FILE)));
        }
    }

    @Test public void damagedMissingUnsupportedAndReboundOwnershipRemainRaw() throws Exception {
        ArenaJournal journal = new ArenaJournal(root().resolve("owned")); journal.openOrReserve();
        Path file = journal.world().resolve(ArenaJournal.FILE);
        String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (String raw : new String[] {"{", original.replace("\"version\":\"1\"", "\"version\":\"2\""),
                original.replace("flat-64-v1", "flat-64-v2"), original.replace(journal.world().toString(), root().resolve("elsewhere").toString()),
                original.replace("\"version\":\"1\"", "\"version\":\"1\",\"version\":\"1\""), original + "{}"}) {
            Files.write(file, raw.getBytes(StandardCharsets.UTF_8));
            expectFailure(journal::read);
            assertEquals(raw, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
        Files.delete(file); expectFailure(journal::openOrReserve); assertFalse(Files.exists(file));
    }

    @Test public void renamedOrCopiedWorldDoesNotRebind() throws Exception {
        ArenaJournal journal = new ArenaJournal(root().resolve("owned")); journal.openOrReserve();
        Path copy = Files.createDirectory(root().resolve("copy"));
        byte[] raw = Files.readAllBytes(journal.world().resolve(ArenaJournal.FILE));
        Files.write(copy.resolve(ArenaJournal.FILE), raw);
        expectFailure(() -> new ArenaJournal(copy).read());
        assertArrayEquals(raw, Files.readAllBytes(copy.resolve(ArenaJournal.FILE)));
    }

    @Test public void rejectsSymlinksAndStaleOperationTransitions() throws Exception {
        ArenaJournal journal = new ArenaJournal(root().resolve("owned"));
        ArenaJournal.Record first = journal.openOrReserve();
        journal.transition(first, ArenaJournal.Phase.INITIALIZING);
        byte[] raw = Files.readAllBytes(journal.world().resolve(ArenaJournal.FILE));
        expectFailure(() -> journal.transition(first, ArenaJournal.Phase.INITIALIZING));
        assertArrayEquals(raw, Files.readAllBytes(journal.world().resolve(ArenaJournal.FILE)));
        Path link = root().resolve("link"); Files.createSymbolicLink(link, journal.world());
        expectFailure(() -> new ArenaJournal(link).openOrReserve());
        assertArrayEquals(raw, Files.readAllBytes(journal.world().resolve(ArenaJournal.FILE)));
    }

    static void expectFailure(Failing action) throws Exception {
        try { action.run(); fail("Expected fail-closed rejection"); } catch (IOException expected) { }
    }
    interface Failing { void run() throws Exception; }
}
