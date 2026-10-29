package dev.hotbarswaptrainer.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hotbarswaptrainer.task.FullInventoryTaskGenerator;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.TaskValidator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ProfileLayoutPersistenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void savedOrderRetainsWholeRulesAndDrivesFullTasksAfterReload() throws Exception {
        Path directory = temporary.getRoot().toPath();
        ProfileRepository repository = new ProfileRepository(directory, id -> 64);
        repository.installDefaultIfMissing();
        Path file = directory.resolve("default.json");
        // File entry order is unrelated to named hotbar/offhand positions.
        JsonObject json = new JsonParser().parse(read(file)).getAsJsonObject();
        JsonArray slots = json.getAsJsonArray("slots");
        JsonArray reversed = new JsonArray();
        for (int index = 9; index >= 0; index--) reversed.add(slots.get(index));
        json.add("slots", reversed);
        json.addProperty("name", "我的布局");
        Files.write(file, json.toString().getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(file);
        LoadoutProfile before = repository.load(file);
        int[] order = {2, 9, 0, 3, 4, 7, 6, 5, 8, 1};
        LoadoutProfile saved = repository.saveSlotOrder(before, order);
        List<SlotRule> expectedRules = new ArrayList<>();
        for (int source : order) expectedRules.add(before.getSlots().get(source));
        LoadoutProfile expected = new LoadoutProfile(before.getId(), before.getName(), expectedRules);
        assertEquals(expected.getHash(), saved.getHash());
        assertEquals(before.getId(), saved.getId());
        assertNotEquals(before.getHash(), saved.getHash());
        assertEquals("minecraft:iron_sword", before.getSlots().get(0).getAlternatives().get(0).getTemplate().getItemId());
        assertEquals(Arrays.asList(8, 9, 10), saved.getFeasibleStackCounts());
        assertEquals(SlotRule.Kind.OPTIONAL, saved.getSlots().get(5).getKind());
        assertEquals(SlotRule.Kind.FLEXIBLE, saved.getSlots().get(7).getKind());
        assertEquals(3, saved.getSlots().get(4).getAlternatives().size());
        assertEquals(StackSpec.NbtMatch.FIELDS, saved.getSlots().get(4).getAlternatives().get(1).getNbtMatch());
        assertEquals(before.getSlots().get(4).getAlternatives().get(1).getMatchNbt(),
                saved.getSlots().get(4).getAlternatives().get(1).getMatchNbt());
        List<Path> backups = backups(directory);
        assertEquals(1, backups.size());
        assertArrayEquals(original, Files.readAllBytes(backups.get(0)));
        ProfileLoadResult reloaded = new ProfileRepository(directory, id -> 64).loadAll();
        assertTrue(reloaded.getErrors().toString(), reloaded.getErrors().isEmpty());
        assertEquals(1, reloaded.getProfiles().size());
        LoadoutProfile actual = reloaded.getProfiles().get(0);
        assertEquals(expected.getHash(), actual.getHash());
        FullInventoryTaskGenerator generator = new FullInventoryTaskGenerator();
        TaskValidator validator = new TaskValidator();
        for (int seed = 0; seed < 60; seed++) {
            InventoryTask task = generator.generate(actual, seed, 8 + seed % 3);
            assertEquals("minecraft:iron_sword", task.getTarget().get(2).getItemId());
            assertEquals("minecraft:iron_pickaxe", task.getTarget().get(9).getItemId());
            assertFalse(validator.validate(task, task.getInitial()).isComplete());
            InventoryLayout solved = task.getInitial();
            for (int hotbar : task.getSolution()) solved = solved.withSwap(hotbar);
            assertTrue(validator.validate(task, solved).isComplete());
        }
    }

    @Test
    public void staleOrCorruptSourceCannotBeOverwritten() throws Exception {
        Path directory = temporary.getRoot().toPath();
        ProfileRepository repository = new ProfileRepository(directory, id -> 64);
        repository.installDefaultIfMissing();
        Path file = directory.resolve("default.json");
        LoadoutProfile before = repository.load(file);
        JsonObject changed = new JsonParser().parse(read(file)).getAsJsonObject();
        changed.addProperty("name", "Edited outside the game");
        Files.write(file, changed.toString().getBytes(StandardCharsets.UTF_8));
        byte[] changedBytes = Files.readAllBytes(file);
        expectSaveError(repository, before, swappedOrder(), "Profile changed");
        assertArrayEquals(changedBytes, Files.readAllBytes(file));
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        expectSaveError(repository, before, swappedOrder(), "Profile unavailable");
        assertEquals("{broken", read(file));
        assertTrue(backups(directory).isEmpty());
    }

    @Test
    public void duplicateIdsAndInvalidPermutationsCannotChangeFiles() throws Exception {
        Path directory = temporary.getRoot().toPath();
        ProfileRepository repository = new ProfileRepository(directory, id -> 64);
        repository.installDefaultIfMissing();
        Path file = directory.resolve("default.json");
        byte[] original = Files.readAllBytes(file);
        LoadoutProfile before = repository.load(file);
        expectSaveError(repository, before, null, "all ten");
        expectSaveError(repository, before, new int[] {0, 1}, "all ten");
        expectSaveError(repository, before, new int[] {0, 0, 2, 3, 4, 5, 6, 7, 8, 9}, "exactly once");
        expectSaveError(repository, before, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 10}, "exactly once");
        expectSaveError(repository, before, new int[] {-1, 1, 2, 3, 4, 5, 6, 7, 8, 9}, "exactly once");
        Files.write(directory.resolve("duplicate.json"), original);
        expectSaveError(repository, before, swappedOrder(), "Duplicate profile id");
        assertArrayEquals(original, Files.readAllBytes(file));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("duplicate.json")));
        assertTrue(backups(directory).isEmpty());
    }

    @Test
    public void laterEditUsesNewSnapshotAndKeepsBothOriginalVersions() throws Exception {
        Path directory = temporary.getRoot().toPath();
        ProfileRepository repository = new ProfileRepository(directory, id -> 64);
        repository.installDefaultIfMissing();
        Path file = directory.resolve("default.json");
        LoadoutProfile before = repository.load(file);
        LoadoutProfile changed = repository.saveSlotOrder(before, swappedOrder());
        expectSaveError(repository, before, swappedOrder(), "Profile changed");
        LoadoutProfile restored = repository.saveSlotOrder(changed, swappedOrder());
        assertEquals(before.getHash(), restored.getHash());
        assertEquals(before.getHash(), repository.load(file).getHash());
        assertEquals(2, backups(directory).size());
    }

    private static int[] swappedOrder() { return new int[] {1, 0, 2, 3, 4, 5, 6, 7, 8, 9}; }

    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }

    private static List<Path> backups(Path directory) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".bak")).collect(Collectors.toList());
        }
    }

    private static void expectSaveError(ProfileRepository repository, LoadoutProfile before, int[] order, String message) throws Exception {
        try {
            repository.saveSlotOrder(before, order);
            fail("Expected save failure containing " + message);
        } catch (ProfileException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
