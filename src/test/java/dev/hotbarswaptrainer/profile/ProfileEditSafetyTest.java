package dev.hotbarswaptrainer.profile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ProfileEditSafetyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void exactBackupAndOnlyExplicitlySelectedFileChange() throws Exception {
        ProfileRepository repository = installed();
        Path selected = file();
        String raw = new String(Files.readAllBytes(selected), StandardCharsets.UTF_8)
                .replace("Stationary basics", "原始中文名称").replace("\n", "\r\n");
        byte[] original = raw.getBytes(StandardCharsets.UTF_8);
        Files.write(selected, original);
        Path unrelated = selected.resolveSibling("other.json");
        byte[] other = raw.replace("\"id\": \"default\"", "\"id\": \"other\"").getBytes(StandardCharsets.UTF_8);
        Files.write(unrelated, other);
        Path broken = selected.resolveSibling("broken.json");
        Files.write(broken, "{broken".getBytes(StandardCharsets.UTF_8));
        ProfileRepository.Edit edit = repository.beginEdit(selected, repository.load(selected));
        edit.getDraft().setName("new name");
        LoadoutProfile saved = repository.save(edit);
        assertEquals(saved.getHash(), repository.load(selected).getHash());
        assertEquals(1, matching(".bak").size());
        assertEquals(java.util.Arrays.asList(broken, selected, unrelated), repository.listProfileFiles());
        assertArrayEquals(original, Files.readAllBytes(matching(".bak").get(0)));
        assertArrayEquals(other, Files.readAllBytes(unrelated));
        assertEquals("{broken", new String(Files.readAllBytes(broken), StandardCharsets.UTF_8));
        assertTrue(matching(".tmp").isEmpty());
        expectFailure(() -> repository.save(edit), "already saved");
        assertEquals(1, matching(".bak").size());
    }

    @Test
    public void byteStaleSemanticStaleAndCorruptEditsAreRejected() throws Exception {
        ProfileRepository repository = installed();
        LoadoutProfile original = repository.load(file());
        byte[] bytes = Files.readAllBytes(file());
        ProfileRepository.Edit edit = repository.beginEdit(file(), original);
        edit.getDraft().setName("draft");
        Files.write(file(), (new String(bytes, StandardCharsets.UTF_8) + " \n").getBytes(StandardCharsets.UTF_8));
        assertEquals(original.getHash(), repository.load(file()).getHash());
        expectFailure(() -> repository.save(edit), "Profile changed");
        Files.write(file(), "{broken".getBytes(StandardCharsets.UTF_8));
        expectFailure(() -> repository.save(edit), "Profile changed");
        expectFailure(() -> repository.beginEdit(file(), original), "default.json");
        // A matching copy cannot redirect an explicitly selected corrupt source.
        Files.write(file().resolveSibling("copy.json"), bytes);
        expectFailure(() -> repository.beginEdit(file(), original), "default.json");
        assertEquals("{broken", new String(Files.readAllBytes(file()), StandardCharsets.UTF_8));
        Files.delete(file().resolveSibling("copy.json"));
        Files.write(file(), new String(bytes, StandardCharsets.UTF_8).replace("Stationary basics", "external").getBytes(StandardCharsets.UTF_8));
        expectFailure(() -> repository.beginEdit(file(), original), "Profile changed");
        assertTrue(matching(".bak").isEmpty());
        assertTrue(matching(".tmp").isEmpty());
    }

    @Test
    public void duplicateIdIsRejectedEvenIfOtherFileHasInvalidSchema() throws Exception {
        ProfileRepository repository = installed();
        LoadoutProfile original = repository.load(file());
        byte[] bytes = Files.readAllBytes(file());
        ProfileRepository.Edit edit = repository.beginEdit(file(), original);
        Path duplicate = file().resolveSibling("duplicate.json");
        Files.write(duplicate, bytes);
        expectFailure(() -> repository.beginEdit(file(), original), "Duplicate profile id");
        expectFailure(() -> repository.save(edit), "Duplicate profile id");
        byte[] corruptDuplicate = "{\"id\":\"default\",\"slots\":[]}".getBytes(StandardCharsets.UTF_8);
        Files.write(duplicate, corruptDuplicate);
        expectFailure(() -> repository.save(edit), "Duplicate profile id");
        expectFailure(() -> repository.saveSlotOrder(original, new int[] {1,0,2,3,4,5,6,7,8,9}), "Duplicate profile id");
        assertArrayEquals(bytes, Files.readAllBytes(file()));
        assertArrayEquals(corruptDuplicate, Files.readAllBytes(duplicate));
        assertTrue(matching(".bak").isEmpty());
    }

    @Test
    public void sourceSelectionCannotEscapeRepositoryOrCrossRepositoryHandles() throws Exception {
        ProfileRepository repository = installed();
        LoadoutProfile original = repository.load(file());
        Path outside = temporary.newFolder("elsewhere").toPath().resolve("default.json");
        Files.copy(file(), outside);
        expectFailure(() -> repository.beginEdit(outside, original), "directly inside");
        ProfileRepository other = new ProfileRepository(file().getParent(), id -> 64);
        ProfileRepository.Edit edit = repository.beginEdit(file(), original);
        expectFailure(() -> other.save(edit), "another repository");
        expectFailure(() -> repository.save(null), "another repository");
        Path link = file().resolveSibling("link.json");
        Files.createSymbolicLink(link, outside);
        expectFailure(() -> repository.beginEdit(link, original), "regular profile file");
        assertTrue(matching(".bak").isEmpty());
    }

    @Test
    public void unsupportedAtomicReplacementRetainsOriginalBackupAndCleansOnlyOurTemp() throws Exception {
        assertFailedReplacement(true, false);
    }

    @Test
    public void otherReplacementFailureRetainsOriginalBackupAndCleansOnlyOurTemp() throws Exception {
        assertFailedReplacement(false, false);
    }

    @Test
    public void slotOrderUsesSameAtomicFailureSafety() throws Exception {
        assertFailedReplacement(true, true);
    }

    @Test
    public void serializationSizeFailureWritesNothing() throws Exception {
        ProfileRepository repository = installed();
        byte[] original = Files.readAllBytes(file());
        ProfileRepository.Edit edit = repository.beginEdit(file(), repository.load(file()));
        edit.getDraft().setName(new String(new char[ProfileRepository.MAX_PROFILE_BYTES]).replace('\0', 'x'));
        expectFailure(() -> repository.save(edit), "1 MiB");
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertTrue(matching(".bak").isEmpty());
        assertTrue(matching(".tmp").isEmpty());
    }

    @Test
    public void malformedUtf8IsDamagedSourceAndRemainsUntouched() throws Exception {
        ProfileRepository repository = installed();
        LoadoutProfile before = repository.load(file());
        byte[] invalid = new String(Files.readAllBytes(file()), StandardCharsets.UTF_8)
                .replace("Stationary basics", "X").getBytes(StandardCharsets.UTF_8);
        // Invalid UTF-8 inside an otherwise syntactically valid JSON string.
        for (int index = 0; index < invalid.length; index++) if (invalid[index] == 'X') invalid[index] = (byte) 0xff;
        Files.write(file(), invalid);
        expectFailure(() -> repository.beginEdit(file(), before), "invalid UTF-8");
        assertArrayEquals(invalid, Files.readAllBytes(file()));
        assertTrue(matching(".bak").isEmpty());
    }

    @Test
    public void sourceChangeDuringPendingReadbackRejectsSaveAndCleansTemp() throws Exception {
        installed();
        boolean[] armed = {false};
        // Simulate an external writer once this save's temporary file exists.
        ItemCatalog catalog = new ItemCatalog() {
            @Override public int maximumStackSize(String id) { return 64; }
            @Override public StackValue normalizeTemplate(StackValue value) {
                if (armed[0]) {
                    try (Stream<Path> paths = Files.list(file().getParent())) {
                        if (paths.anyMatch(path -> path.getFileName().toString().startsWith(".profile-"))) {
                            armed[0] = false;
                            Files.write(file(), "{externally damaged".getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (IOException failure) { throw new AssertionError(failure); }
                }
                return value;
            }
        };
        ProfileRepository repository = new ProfileRepository(file().getParent(), catalog);
        ProfileRepository.Edit edit = repository.beginEdit(file(), repository.load(file()));
        edit.getDraft().setName("new name");
        armed[0] = true;
        expectFailure(() -> repository.save(edit), "Profile changed");
        assertEquals("{externally damaged", new String(Files.readAllBytes(file()), StandardCharsets.UTF_8));
        assertTrue(matching(".tmp").isEmpty());
        assertTrue(matching(".bak").isEmpty());
    }

    private void assertFailedReplacement(boolean unsupported, boolean slotOrder) throws Exception {
        installed();
        byte[] original = Files.readAllBytes(file());
        Path unrelatedTemp = file().resolveSibling("unrelated.tmp");
        Files.write(unrelatedTemp, new byte[] {1,2,3});
        int[] calls = {0};
        ProfileRepository repository = new ProfileRepository(file().getParent(), id -> 64, (pending, selected) -> {
            calls[0]++;
            assertEquals(file().getParent(), pending.getParent());
            assertEquals(file(), selected);
            assertTrue(Files.exists(pending));
            assertArrayEquals(original, Files.readAllBytes(selected));
            try { assertArrayEquals(original, Files.readAllBytes(matching(".bak").get(0))); }
            catch (Exception failure) { throw new AssertionError(failure); }
            if (unsupported) throw new AtomicMoveNotSupportedException(pending.toString(), selected.toString(), "fixture");
            throw new IOException("replacement denied");
        });
        LoadoutProfile before = repository.load(file());
        ProfileRepository.Edit edit = repository.beginEdit(file(), before);
        edit.getDraft().setName("new name");
        try {
            if (slotOrder) repository.saveSlotOrder(before, new int[] {1,0,2,3,4,5,6,7,8,9});
            else repository.save(edit);
            fail("replacement must fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(unsupported ? "Atomic profile replacement is unsupported" : "replacement denied"));
        }
        assertEquals(1, calls[0]);
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertArrayEquals(original, Files.readAllBytes(matching(".bak").get(0)));
        assertEquals(java.util.Collections.singletonList(unrelatedTemp), matching(".tmp"));
    }

    private ProfileRepository installed() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), id -> 64);
        repository.installDefaultIfMissing();
        return repository;
    }

    private Path file() { return temporary.getRoot().toPath().resolve("default.json"); }

    private List<Path> matching(String suffix) throws Exception {
        try (Stream<Path> paths = Files.list(temporary.getRoot().toPath())) {
            return paths.filter(path -> path.toString().endsWith(suffix)).collect(Collectors.toList());
        }
    }

    private interface CheckedAction { void run() throws Exception; }

    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try { action.run(); fail("Expected failure containing " + message); }
        catch (ProfileException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
}
