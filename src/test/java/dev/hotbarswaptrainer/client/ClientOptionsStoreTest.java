package dev.hotbarswaptrainer.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ClientOptionsStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingFileUsesExistingDefaultsWithoutWriting() throws Exception {
        Path file = file();
        ClientOptions options = new ClientOptionsStore(file).load();
        assertEquals(TrainingMode.SINGLE_ITEM, options.mode);
        assertEquals("default", options.profile);
        assertEquals(10, options.goal);
        assertFalse(options.showHints);
        assertFalse(Files.exists(file));
    }

    @Test
    public void validOptionsRoundTripAndReplaceAtomicallyWithoutTemporaryFiles() throws Exception {
        Path file = file();
        ClientOptionsStore store = new ClientOptionsStore(file);
        store.load();
        for (int goal : new int[] {0, 1, 73, 10001, Integer.MAX_VALUE}) {
            ClientOptions expected = new ClientOptions(TrainingMode.FULL_INVENTORY, "custom-1", goal, true);
            store.save(expected);
            assertEquals(expected, new ClientOptionsStore(file).load());
            assertEquals(1, new JsonParser().parse(read(file)).getAsJsonObject().get("schemaVersion").getAsInt());
        }
        store.save(ClientOptions.defaults());
        assertEquals(ClientOptions.defaults(), new ClientOptionsStore(file).load());
        try (Stream<Path> files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    public void invalidSchemasTypesRangesAndIdsAreRejectedAndPreserved() throws Exception {
        Path file = file();
        ClientOptionsStore store = new ClientOptionsStore(file);
        store.load();
        store.save(ClientOptions.defaults());
        String valid = read(file);
        String[][] invalidFields = {
                {"schemaVersion", "2"}, {"schemaVersion", "\"1\""}, {"schemaVersion", "1.5"},
                {"mode", "\"UNKNOWN\""}, {"mode", "false"}, {"profile", "\"\""},
                {"profile", "\"../custom\""}, {"profile", "42"}, {"goal", "-1"},
                {"goal", "2147483648"}, {"goal", "1.5"}, {"goal", "\"10\""},
                {"goal", "null"}, {"showHints", "\"false\""}, {"showHints", "0"}, {"extra", "true"}
        };
        for (String[] invalid : invalidFields) {
            JsonObject json = new JsonParser().parse(valid).getAsJsonObject();
            json.add(invalid[0], new JsonParser().parse(invalid[1]));
            rejectAndPreserve(file, json.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String key : new String[] {"schemaVersion", "mode", "profile", "goal", "showHints"}) {
            JsonObject json = new JsonParser().parse(valid).getAsJsonObject();
            json.remove(key);
            rejectAndPreserve(file, json.toString().getBytes(StandardCharsets.UTF_8));
        }
        JsonObject json = new JsonParser().parse(valid).getAsJsonObject();
        json.addProperty("profile", new String(new char[129]).replace('\0', 'a'));
        rejectAndPreserve(file, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void malformedTruncatedDuplicateOversizedAndNonUtf8FilesCannotBeOverwritten() throws Exception {
        Path file = file();
        for (String json : new String[] {"{broken", "", "null", "[]", "{} {}", "{\"goal\":1,\"goal\":2}",
                "{unquoted:1}", "{/*comment*/\"goal\":1}", "{\"goal\":01}", "{\"goal\":1,}"}) {
            rejectAndPreserve(file, json.getBytes(StandardCharsets.UTF_8));
        }
        rejectAndPreserve(file, new byte[ClientOptionsStore.MAX_BYTES + 1]);
        rejectAndPreserve(file, new byte[] {(byte) 0xC3, (byte) 0x28});
    }

    @Test
    public void failedAtomicReplacementReportsErrorAndCleansUpTemporaryFile() throws Exception {
        Path file = file();
        ClientOptionsStore store = new ClientOptionsStore(file);
        store.load();
        // A nonempty directory cannot be atomically replaced by the options file on this filesystem.
        Files.createDirectories(file);
        Path sentinel = file.resolve("keep.txt");
        Files.write(sentinel, new byte[] {1, 2, 3});
        try { store.save(ClientOptions.defaults()); fail("Expected replacement failure"); }
        catch (IOException expected) { assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(sentinel)); }
        try (Stream<Path> files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    private void rejectAndPreserve(Path file, byte[] bytes) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        ClientOptionsStore store = new ClientOptionsStore(file);
        try { store.load(); fail("Expected load rejection"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("File preserved; saving disabled")); }
        try { store.save(ClientOptions.defaults()); fail("Expected write lock"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("saving disabled")); }
        assertArrayEquals(bytes, Files.readAllBytes(file));
        try (Stream<Path> files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    private Path file() { return temporary.getRoot().toPath().resolve("config/client-options.json"); }
    private String read(Path file) throws Exception { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8); }
}
