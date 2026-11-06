package dev.hotbarswaptrainer.profile;

import dev.hotbarswaptrainer.stats.HistorySnapshot;
import dev.hotbarswaptrainer.stats.HistoryStore;
import dev.hotbarswaptrainer.stats.SessionMetadata;
import dev.hotbarswaptrainer.stats.SessionResult;
import dev.hotbarswaptrainer.stats.TaskResult;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.SingleItemTaskGenerator;
import dev.hotbarswaptrainer.task.TaskValidator;
import dev.hotbarswaptrainer.task.TrainingMode;
import dev.hotbarswaptrainer.task.ValidationResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public class ProfileNameContractTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void maximumNameSurvivesProfileTaskAndDurableHistory() throws Exception {
        Path profiles = temporary.newFolder("profiles").toPath();
        ProfileRepository repository = new ProfileRepository(profiles, id -> 64);
        repository.installDefaultIfMissing();
        Path file = profiles.resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        // Multibyte names use the same Java character limit as the editor/history.
        String name = String.join("", Collections.nCopies(256, "名"));
        edit.getDraft().setName(name);
        repository.save(edit);
        LoadoutProfile profile = new ProfileRepository(profiles, id -> 64).load(file);
        InventoryTask task = new SingleItemTaskGenerator().generate(profile, 17);
        InventoryLayout solved = task.getInitial();
        for (int selected : task.getSolution()) solved = solved.withSwap(selected);
        assertEquals(ValidationResult.Status.COMPLETE, new TaskValidator().validate(task, solved).getStatus());
        UUID player = UUID.randomUUID();
        SessionMetadata metadata = new SessionMetadata(player, 100, 200, profile.getId(), profile.getName(),
                false, SessionMetadata.TIMING, SessionMetadata.SCORING, SessionMetadata.ENVIRONMENT,
                SessionMetadata.EndState.COMPLETED);
        TaskResult success = new TaskResult(task.getId(), TaskResult.Outcome.SUCCESS, 42, 1, 1, 0, 0, 0, "");
        Path history = temporary.newFolder("history").toPath();
        new HistoryStore(history).save(player, new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM,
                profile.getHash(), 1, Collections.singletonList(success), metadata));
        HistorySnapshot loaded = new HistoryStore(history).read(player);
        assertTrue(loaded.errors.toString(), loaded.errors.isEmpty());
        assertEquals(1, loaded.records.size());
        assertEquals(name, loaded.records.get(0).getMetadata().profileName);
        assertEquals(42, loaded.records.get(0).getTotalNanos());
    }

    @Test public void oversizedDraftAndExistingFileFailEarlyWithoutReplacingBytes() throws Exception {
        Path profiles = temporary.newFolder("profiles").toPath();
        ProfileRepository repository = new ProfileRepository(profiles, id -> 64);
        repository.installDefaultIfMissing();
        Path file = profiles.resolve("default.json");
        byte[] original = Files.readAllBytes(file);
        LoadoutProfile profile = repository.load(file);
        String name = String.join("", Collections.nCopies(257, "a"));
        ProfileRepository.Edit edit = repository.beginEdit(file, profile);
        edit.getDraft().setName(name);
        ProfileDraftValidation validation = repository.validateDraft(edit.getDraft());
        assertFalse(validation.isValid());
        assertTrue(validation.getError(), validation.getError().contains("256"));
        try { repository.save(edit); fail("oversized draft saved"); }
        catch (ProfileException expected) { assertTrue(expected.getMessage().contains("256")); }
        assertArrayEquals(original, Files.readAllBytes(file));
        byte[] legacy = new String(original, StandardCharsets.UTF_8).replace(profile.getName(), name).getBytes(StandardCharsets.UTF_8);
        Files.write(file, legacy);
        ProfileLoadResult loaded = repository.loadAll();
        assertTrue(loaded.getProfiles().isEmpty());
        assertTrue(loaded.getErrors().toString(), loaded.getErrors().stream().anyMatch(error -> error.contains("256")));
        assertArrayEquals(legacy, Files.readAllBytes(file));
        try { new SessionMetadata(UUID.randomUUID(), 100, 200, profile.getId(), name, false,
                SessionMetadata.TIMING, SessionMetadata.SCORING, SessionMetadata.ENVIRONMENT, SessionMetadata.EndState.STOPPED);
            fail("metadata accepted oversized name"); }
        catch (IllegalArgumentException expected) { }
    }
}
