package dev.hotbarswaptrainer.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ClientOptionsPersistenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptedChangesRestoreAllOptionsInANewClient() throws Exception {
        ProfileRepository profiles = profiles();
        addProfile("custom");
        TrainerClient trainer = client(profiles);
        assertFalse(Files.exists(file()));
        assertFalse(trainer.showSwapCounts());
        trainer.toggleSwapCounts();
        trainer.cycleMode();
        assertTrue(trainer.selectProfile("custom"));
        assertEquals("custom", trainer.profile());
        assertTrue(trainer.setGoal("2147483647"));
        trainer.toggleHints();

        TrainerClient restarted = client(profiles);
        assertEquals(TrainingMode.FULL_INVENTORY, restarted.mode());
        assertEquals("custom", restarted.profile());
        assertEquals(Integer.MAX_VALUE, restarted.goal());
        assertTrue(restarted.showHints());
        assertTrue(restarted.showSwapCounts());
        assertTrue(restarted.optionMessages().isEmpty());
        restarted.toggleHints();
        restarted.setGoal("0");
        assertFalse(client(profiles).showHints());
        assertTrue(client(profiles).showSwapCounts());
        restarted.toggleSwapCounts();
        assertFalse(client(profiles).showSwapCounts());
        assertEquals(0, client(profiles).goal());
    }

    @Test
    public void draftsNeverWriteAndOtherEditsOnlySaveLastValidGoal() throws Exception {
        TrainerClient trainer = client(profiles());
        assertTrue(trainer.setGoal("73"));
        byte[] accepted = Files.readAllBytes(file());
        for (String draft : new String[] {"", " ", "2147483648", "99999999999999999999999999999999", "-1", "1.5"}) {
            assertFalse(trainer.setGoal(draft));
            assertArrayEquals(accepted, Files.readAllBytes(file()));
        }
        trainer.toggleHints();
        ClientOptions stored = new ClientOptionsStore(file()).load();
        assertEquals(73, stored.goal);
        assertTrue(stored.showHints);
        // Reinitializing a screen with the same valid text is not an accepted change.
        Files.delete(file());
        assertTrue(trainer.setGoal("73"));
        assertFalse(Files.exists(file()));
    }

    @Test
    public void corruptOptionsUseSafeDefaultsAndRemainProtectedAcrossAllEdits() throws Exception {
        ProfileRepository profiles = profiles();
        addProfile("custom");
        byte[] broken = "{\"schemaVersion\":1,\"mode\":\"FULL_INVENTORY\",".getBytes(StandardCharsets.UTF_8);
        Files.write(file(), broken);
        TrainerClient trainer = client(profiles);
        assertEquals(TrainingMode.SINGLE_ITEM, trainer.mode());
        assertEquals("default", trainer.profile());
        assertEquals(10, trainer.goal());
        assertFalse(trainer.showHints());
        assertTrue(trainer.optionMessages().toString().contains("saving disabled"));
        trainer.cycleMode();
        assertTrue(trainer.selectProfile("custom"));
        trainer.setGoal("73");
        trainer.toggleHints();
        assertArrayEquals(broken, Files.readAllBytes(file()));
        // Moving a corrupt file is explicit; this process still does not silently regenerate it.
        Files.move(file(), file().resolveSibling("damaged-options.json"));
        trainer.toggleHints();
        assertFalse(Files.exists(file()));
        TrainerClient restarted = client(profiles);
        restarted.toggleHints();
        assertTrue(Files.exists(file()));
    }

    @Test
    public void missingProfileFallsBackExplicitlyToDefaultOrFirstAvailableProfile() throws Exception {
        ProfileRepository profiles = profiles();
        addProfile("custom");
        save(new ClientOptions(TrainingMode.FULL_INVENTORY, "removed", 73, true));
        byte[] beforeLoad = Files.readAllBytes(file());
        TrainerClient trainer = client(profiles);
        assertEquals("default", trainer.profile());
        assertTrue(trainer.profileAvailable());
        assertTrue(trainer.optionMessages().toString().contains("'removed' unavailable; using 'default'"));
        assertArrayEquals(beforeLoad, Files.readAllBytes(file()));
        assertTrue(trainer.selectProfile("custom"));
        assertEquals("custom", client(profiles).profile());

        Files.delete(profileDirectory().resolve("default.json"));
        save(new ClientOptions(TrainingMode.SINGLE_ITEM, "removed-again", 10, false));
        trainer = client(profiles);
        assertEquals("custom", trainer.profile());
        assertTrue(trainer.profileAvailable());
        trainer.toggleHints();
        assertEquals("custom", new ClientOptionsStore(file()).load().profile);
    }

    @Test
    public void deletedProfilesNeverPersistAndSelectorCanRecoverWhenFilesAreRepaired() throws Exception {
        ProfileRepository profiles = profiles();
        TrainerClient trainer = client(profiles);
        trainer.setGoal("73");
        byte[] beforeDeletion = Files.readAllBytes(file());
        Files.delete(profileDirectory().resolve("default.json"));
        assertFalse(trainer.selectProfile("default"));
        assertFalse(trainer.profileAvailable());
        assertTrue(trainer.optionMessages().toString().contains("No valid profiles"));
        trainer.toggleHints();
        assertArrayEquals(beforeDeletion, Files.readAllBytes(file()));

        profiles.installDefaultIfMissing();
        addProfile("replacement");
        Files.delete(profileDirectory().resolve("default.json"));
        assertTrue(trainer.selectProfile("replacement"));
        assertTrue(trainer.profileAvailable());
        assertEquals("replacement", trainer.profile());
        assertEquals("replacement", new ClientOptionsStore(file()).load().profile);
        assertTrue(new ClientOptionsStore(file()).load().showHints);
    }

    @Test
    public void retryingTheSameRepairedProfileSavesPendingValidPreferences() throws Exception {
        ProfileRepository profiles = profiles();
        TrainerClient trainer = client(profiles);
        Files.delete(profileDirectory().resolve("default.json"));
        trainer.setGoal("73");
        trainer.toggleHints();
        assertFalse(trainer.profileAvailable());
        assertFalse(Files.exists(file()));
        profiles.installDefaultIfMissing();
        assertTrue(trainer.selectProfile("default"));
        assertTrue(trainer.profileAvailable());
        assertTrue(trainer.optionMessages().isEmpty());
        ClientOptions stored = new ClientOptionsStore(file()).load();
        assertEquals("default", stored.profile);
        assertEquals(73, stored.goal);
        assertTrue(stored.showHints);
    }

    @Test
    public void explicitSelectionDoesNotEditLayoutsOrAcceptUnavailableIds() throws Exception {
        ProfileRepository profiles = profiles();
        addProfile("custom");
        TrainerClient trainer = client(profiles);
        byte[] original = Files.readAllBytes(profileDirectory().resolve("custom.json"));
        assertTrue(trainer.selectProfile("custom"));
        byte[] preferences = Files.readAllBytes(file());
        assertFalse(trainer.selectProfile("missing"));
        assertEquals("custom", trainer.profile());
        assertArrayEquals(preferences, Files.readAllBytes(file()));
        assertArrayEquals(original, Files.readAllBytes(profileDirectory().resolve("custom.json")));
        assertTrue(trainer.selectProfile("default"));
        assertEquals("default", client(profiles).profile());
    }

    @Test
    public void failedSaveKeepsUsableMemoryAndReportsErrorUntilAnEditRetries() throws Exception {
        TrainerClient trainer = client(profiles());
        Files.createDirectory(file());
        Path sentinel = file().resolve("keep");
        Files.write(sentinel, new byte[] {1});
        assertTrue(trainer.setGoal("73"));
        assertEquals(73, trainer.goal());
        assertTrue(trainer.optionMessages().toString().contains("Cannot save"));
        Files.delete(sentinel);
        Files.delete(file());
        trainer.toggleHints();
        assertTrue(trainer.optionMessages().isEmpty());
        assertEquals(73, new ClientOptionsStore(file()).load().goal);
    }

    private TrainerClient client(ProfileRepository profiles) {
        TrainerClient trainer = new TrainerClient();
        trainer.initializeOptions(profiles, new ClientOptionsStore(file()));
        return trainer;
    }

    private void save(ClientOptions options) throws Exception {
        ClientOptionsStore store = new ClientOptionsStore(file());
        store.load();
        store.save(options);
    }

    private ProfileRepository profiles() throws Exception {
        ProfileRepository profiles = new ProfileRepository(profileDirectory(), id -> 64);
        profiles.installDefaultIfMissing();
        return profiles;
    }

    private void addProfile(String id) throws Exception {
        JsonObject json = new JsonParser().parse(new String(Files.readAllBytes(
                profileDirectory().resolve("default.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        json.addProperty("id", id);
        Files.write(profileDirectory().resolve(id + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Path profileDirectory() { return temporary.getRoot().toPath().resolve("profiles"); }
    private Path file() { return temporary.getRoot().toPath().resolve("client-options.json"); }
}
