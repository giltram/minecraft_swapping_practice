package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final UUID player = UUID.randomUUID();
    static final String HASH = String.join("", Collections.nCopies(64, "a"));
    static TaskResult task(TaskResult.Outcome outcome, long nanos) {
        return new TaskResult(UUID.randomUUID(), outcome, nanos, 3, 2, 4, 1, 2, "reason");
    }
    static SessionResult record(UUID player, UUID session, long end, int goal, boolean hints,
                                String hash, SessionMetadata.EndState state, TaskResult... tasks) {
        return new SessionResult(session, TrainingMode.SINGLE_ITEM, hash, goal, Arrays.asList(tasks),
                new SessionMetadata(player, 100, end, "old-id", "Old profile name", hints,
                        SessionMetadata.TIMING, SessionMetadata.SCORING, SessionMetadata.ENVIRONMENT, state));
    }
    private Path root() throws IOException { return temporary.newFolder().toPath(); }
    private Path file(Path root, SessionResult r) { return root.resolve(player.toString()).resolve(r.getSessionId() + ".json"); }
    private SessionResult stopped() { return record(player, UUID.randomUUID(), 200, 0, true, HASH,
            SessionMetadata.EndState.STOPPED, task(TaskResult.Outcome.SUCCESS, 42), task(TaskResult.Outcome.INVALID, 0), task(TaskResult.Outcome.STOPPED, 5)); }

    @Test public void freshInstanceRoundTripsEveryFieldAndMemoryCleanupRetainsHistory() throws Exception {
        Path root = root(); HistoryStore store = new HistoryStore(root); SessionResult result = stopped();
        store.save(player, result); byte[] original = Files.readAllBytes(file(root, result));
        store.save(player, result); assertArrayEquals(original, Files.readAllBytes(file(root, result)));
        store.forget(player); assertFalse(store.findLatest(player).isPresent());
        store.save(player, result); store.clear(); assertFalse(store.findLatest(player).isPresent());
        HistorySnapshot snapshot = new HistoryStore(root).read(player);
        assertTrue(snapshot.errors.toString(), snapshot.errors.isEmpty()); assertEquals(1, snapshot.records.size());
        SessionResult loaded = snapshot.records.get(0);
        assertArrayEquals(HistoryJson.encode(player, result), HistoryJson.encode(player, loaded));
        assertEquals("Old profile name", loaded.getMetadata().profileName); assertEquals(42, loaded.getTotalNanos());
        assertEquals(3, loaded.getTasks().size()); assertEquals(2, loaded.getTasks().get(0).getForbiddenActions());
        assertTrue(new HistoryStore(root).read(UUID.randomUUID()).records.isEmpty());
    }
    @Test public void conflictsAndBadOriginalNeverReplaceBytes() throws Exception {
        Path root = root(); HistoryStore store = new HistoryStore(root); SessionResult first = stopped(); store.save(player, first);
        byte[] original = Files.readAllBytes(file(root, first));
        SessionResult changed = record(player, first.getSessionId(), 201, 0, true, HASH, SessionMetadata.EndState.STOPPED);
        try { store.save(player, changed); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("conflict")); }
        assertArrayEquals(original, Files.readAllBytes(file(root, first)));
        Files.write(file(root, first), "broken".getBytes(StandardCharsets.UTF_8));
        try { store.save(player, first); fail(); } catch (IOException expected) { }
        assertEquals("broken", new String(Files.readAllBytes(file(root, first)), StandardCharsets.UTF_8));
    }
    @Test public void stableOrderingPaginationFilteringAndClamp() throws Exception {
        Path root = root(); HistoryStore store = new HistoryStore(root);
        for (int i = 0; i < 25; i++) store.save(player, record(player, new UUID(0, i), 200 + i / 2, i % 2, i % 2 == 0,
                HASH, SessionMetadata.EndState.STOPPED));
        HistorySnapshot snapshot = store.read(player); HistorySnapshot.Page page = snapshot.query(HistorySnapshot.Filter.all(), 1);
        assertEquals(25, page.total); assertEquals(3, page.pages); assertEquals(10, page.records.size());
        assertEquals(2, snapshot.query(HistorySnapshot.Filter.all(), Integer.MAX_VALUE).index);
        assertEquals(new UUID(0, 24), snapshot.records.get(0).getSessionId());
        assertEquals(new UUID(0, 22), snapshot.records.get(1).getSessionId());
        assertEquals(13, snapshot.query(new HistorySnapshot.Filter(TrainingMode.SINGLE_ITEM, true, 0, HASH, SessionMetadata.EndState.STOPPED), 0).total);
        assertEquals(0, snapshot.query(new HistorySnapshot.Filter(TrainingMode.FULL_INVENTORY, null, null, null, null), 0).total);
    }
    @Test public void badTruncatedUnknownDuplicateAndOversizedFilesArePreservedAndIsolated() throws Exception {
        Path root = root(); HistoryStore store = new HistoryStore(root); SessionResult valid = stopped(); store.save(player, valid);
        String json = new String(HistoryJson.encode(player, valid), StandardCharsets.UTF_8);
        List<String> damaged = Arrays.asList("{", json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                json.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                json.replace("\"swapAttempts\":3", "\"swapAttempts\":2147483648"),
                json.replace("\"targetHints\":true", "\"targetHints\":\"true\""), json.substring(0, json.length()-4));
        int count = 0;
        for (String text : damaged) {
            UUID id = UUID.randomUUID(); Path path = root.resolve(player.toString()).resolve(id + ".json");
            text = text.replace(valid.getSessionId().toString(), id.toString());
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8); Files.write(path, bytes);
            assertArrayEquals(bytes, Files.readAllBytes(path)); count++;
        }
        Path huge = root.resolve(player.toString()).resolve(UUID.randomUUID() + ".json");
        byte[] bytes = new byte[HistoryJson.MAX_BYTES + 1]; Files.write(huge, bytes);
        Files.write(root.resolve(player.toString()).resolve(".history-uncommitted.tmp"), json.getBytes(StandardCharsets.UTF_8));
        HistorySnapshot snapshot = store.read(player);
        assertEquals(1, snapshot.records.size()); assertEquals(count + 1, snapshot.errors.size());
        assertEquals(bytes.length, Files.size(huge));
    }
    @Test public void atomicCommitFailurePreservesExistingAndCleansOnlyOwnTemporaryFile() throws Exception {
        Path root = root(); HistoryStore normal = new HistoryStore(root); SessionResult first = stopped(); normal.save(player, first);
        byte[] original = Files.readAllBytes(file(root, first));
        Path evidence = root.resolve(player.toString()).resolve(".old-evidence.tmp"); Files.write(evidence, new byte[]{1});
        HistoryStore denied = new HistoryStore(root, (a, b) -> { throw new AtomicMoveNotSupportedException(a.toString(), b.toString(), "injected"); });
        try { denied.save(player, stopped()); fail(); } catch (AtomicMoveNotSupportedException expected) { }
        assertArrayEquals(original, Files.readAllBytes(file(root, first))); assertTrue(Files.exists(evidence));
        try (java.util.stream.Stream<Path> paths = Files.list(evidence.getParent())) {
            assertEquals(1, paths.filter(p -> p.toString().endsWith(".tmp")).count());
        }
    }
    @Test public void writeFailureAndForeignPlayerFailWithoutMutatingAnyHistory() throws Exception {
        Path root = root(); SessionResult result = stopped(); Files.write(root.resolve(player.toString()), new byte[]{8});
        try { new HistoryStore(root).save(player, result); fail(); } catch (IOException expected) { }
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(root.resolve(player.toString())));
        try { new HistoryStore(root()).save(UUID.randomUUID(), result); fail(); } catch (IOException expected) { }
    }
    @Test public void invalidNumericCollectionsAndMissingMetadataNeverCreateFalseResults() throws Exception {
        SessionResult valid = stopped(); String json = new String(HistoryJson.encode(player, valid), StandardCharsets.UTF_8);
        for (String text : Arrays.asList(json.replace("\"elapsedNanos\":42", "\"elapsedNanos\":0"),
                json.replace("\"elapsedNanos\":42", "\"elapsedNanos\":-1"),
                json.replace("\"elapsedNanos\":42", "\"elapsedNanos\":1.5"),
                json.replace("\"outcome\":\"SUCCESS\"", "\"outcome\":\"UNKNOWN\""),
                json.replace("\"requestedSuccesses\":0,", ""), json + "{}", "[[[[[[[]]]]]]]")) {
            try { HistoryJson.decode(text.getBytes(StandardCharsets.UTF_8), player, valid.getSessionId()); fail(text); } catch (IOException expected) { }
        }
        List<TaskResult> tooMany = new ArrayList<>(); for (int i=0;i<=HistoryJson.MAX_TASKS;i++) tooMany.add(task(TaskResult.Outcome.INVALID,0));
        try { new HistoryStore(root()).save(player, new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM,HASH,0,tooMany)); fail(); }
        catch (IOException expected) { }
    }
    @Test public void catalogueAndByteBudgetsReturnExplicitIncompleteWarnings() throws Exception {
        Path root=root(), directory=root.resolve(player.toString()); Files.createDirectory(directory);
        for(int i=0;i<=HistoryStore.MAX_RECORDS;i++) Files.write(directory.resolve(new UUID(0,i)+".json"),new byte[0]);
        HistorySnapshot limited=new HistoryStore(root).read(player);
        assertTrue(limited.errors.stream().anyMatch(e->e.contains("catalogue limit")));
        Path bytesRoot=root(), bytesDirectory=bytesRoot.resolve(player.toString()); Files.createDirectory(bytesDirectory);
        for(int i=0;i<17;i++) {
            SessionResult result=new SessionResult(new UUID(1,i),TrainingMode.SINGLE_ITEM,"legacy",0,Collections.emptyList());
            byte[] content=HistoryJson.encode(player,result), padded=new byte[HistoryJson.MAX_BYTES];
            Arrays.fill(padded,(byte)' '); System.arraycopy(content,0,padded,0,content.length);
            Files.write(bytesDirectory.resolve(result.getSessionId()+".json"),padded);
        }
        HistorySnapshot budget=new HistoryStore(bytesRoot).read(player);
        assertEquals(16,budget.records.size()); assertEquals(1,budget.errors.size());
        assertTrue(budget.errors.get(0).contains("budget"));
    }
    @Test public void invalidUtf8SymlinksAndGenericCommitFailurePreserveEvidence() throws Exception {
        Path root=root(); HistoryStore store=new HistoryStore(root); SessionResult valid=stopped(); store.save(player,valid);
        Path directory=root.resolve(player.toString()), bad=directory.resolve(UUID.randomUUID()+".json");
        byte[] raw={(byte)0xc3,(byte)0x28}; Files.write(bad,raw);
        Path link=directory.resolve(UUID.randomUUID()+".json"); Files.createSymbolicLink(link,file(root,valid));
        HistorySnapshot read=store.read(player); assertEquals(1,read.records.size()); assertEquals(2,read.errors.size());
        assertArrayEquals(raw,Files.readAllBytes(bad)); assertTrue(Files.isSymbolicLink(link));
        HistoryStore broken=new HistoryStore(root,(from,to)->{throw new IOException("commit rejected");});
        byte[] before=Files.readAllBytes(file(root,valid));
        try { broken.save(player,stopped()); fail(); } catch(IOException expected) { assertEquals("commit rejected",expected.getMessage()); }
        assertArrayEquals(before,Files.readAllBytes(file(root,valid)));
    }
    @Test public void finiteCompleteAndZeroSuccessRoundTripAndOverflowRejected() throws Exception {
        Path root = root(); HistoryStore store = new HistoryStore(root);
        store.save(player, record(player, UUID.randomUUID(), 200, 1, false,HASH,SessionMetadata.EndState.COMPLETED,task(TaskResult.Outcome.SUCCESS,10)));
        store.save(player, record(player, UUID.randomUUID(), 201, 0, false,HASH,SessionMetadata.EndState.STOPPED));
        assertEquals(2, store.read(player).records.size());
        try { store.save(player, record(player,UUID.randomUUID(),201,1,false,HASH,SessionMetadata.EndState.COMPLETED)); fail(); } catch (IOException expected) { }
        try { store.save(player, record(player,UUID.randomUUID(),201,0,false,HASH,SessionMetadata.EndState.STOPPED,
                task(TaskResult.Outcome.SUCCESS,Long.MAX_VALUE),task(TaskResult.Outcome.SUCCESS,1))); fail(); } catch (IOException expected) { }
        try { new TaskResult(UUID.randomUUID(),TaskResult.Outcome.SUCCESS,1,Integer.MAX_VALUE,Integer.MAX_VALUE,0,1,0,""); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
