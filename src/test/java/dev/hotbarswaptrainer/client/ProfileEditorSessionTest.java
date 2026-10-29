package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** File-backed UI coordination tests, deliberately independent of widget construction. */
public class ProfileEditorSessionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ProfileRepository repository;
    private Path source;
    private final AtomicBoolean allowed = new AtomicBoolean(true);

    private ProfileEditorSession open() throws Exception {
        repository = new ProfileRepository(temporary.getRoot().toPath(), id -> 64);
        repository.installDefaultIfMissing();
        // Filename deliberately differs from ID: saving must never infer a filename from the ID.
        source = temporary.getRoot().toPath().resolve("chosen-source.json");
        Files.move(temporary.getRoot().toPath().resolve("default.json"), source);
        return new ProfileEditorSession(repository, source, repository.load(source), allowed::get);
    }
    private ProfileDraft.Candidate first(ProfileEditorSession editor) {
        return editor.draft().getSlots().get(0).getAlternatives().get(0);
    }

    @Test public void openingAndNavigationReadsDoNotMaterializeImplicitDefaultsOrWrite() throws Exception {
        ProfileEditorSession editor = open();
        byte[] original = Files.readAllBytes(source);
        ProfileDraft.Candidate candidate = first(editor);
        assertNull(candidate.getNbt()); assertNull(candidate.getMatch()); assertNull(candidate.getMatchNbt());
        for (int visit = 0; visit < 5; visit++) {
            assertEquals("1", editor.count(candidate)); assertEquals("", editor.validate()); editor.applicability();
        }
        assertFalse(editor.dirty());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertNull(candidate.getMatch()); assertNull(candidate.getMatchNbt());
    }
    @Test public void unfinishedQuantityAndSnbtSurviveOtherEditsAndBlockSaving() throws Exception {
        ProfileEditorSession editor = open();
        byte[] original = Files.readAllBytes(source);
        ProfileDraft.Candidate candidate = first(editor);
        for (String text : new String[] {"", " ", "0", "-1", "1.0", "2147483648", "x"}) {
            editor.setCount(candidate, text);
            editor.draft().setName("edited elsewhere");
            candidate.setNbt("{unfinished:");
            assertTrue(editor.dirty()); assertEquals(text, editor.count(candidate));
            assertFalse(editor.validate().isEmpty()); assertFalse(editor.save());
            assertFalse(editor.reloadRequired());
            assertEquals("{unfinished:", first(editor).getNbt());
            assertArrayEquals(original, Files.readAllBytes(source));
        }
        editor.setCount(candidate, "2");
        assertTrue(editor.validate().contains("nbt"));
        candidate.setNbt(null);
        assertEquals("", editor.validate());
        assertTrue(editor.save()); assertEquals(2, first(editor).getCount());
    }
    @Test public void resetRestoresWholeOpeningDraftIncludingNullableFieldsAndRawCounts() throws Exception {
        ProfileEditorSession editor = open();
        ProfileDraft.Candidate candidate = first(editor);
        editor.setCount(candidate, ""); candidate.setMatchNbt("{}"); candidate.setMatch(StackSpec.NbtMatch.FIELDS);
        editor.draft().getSlots().get(9).getAlternatives().clear();
        editor.draft().getSlots().get(9).setKind(SlotRule.Kind.EMPTY);
        editor.draft().setName("changed"); editor.reset();
        assertFalse(editor.dirty()); assertEquals("1", editor.count(first(editor)));
        assertNull(first(editor).getMatch()); assertNull(first(editor).getMatchNbt());
        assertEquals(SlotRule.Kind.REQUIRED, editor.draft().getSlots().get(9).getKind());
        assertFalse(editor.draft().getSlots().get(9).getAlternatives().isEmpty());
        assertEquals(editor.expected().getName(), editor.draft().getName());
    }
    @Test public void explicitEmptyCompoundAndUseDefaultRemainDifferentThroughSaveAndReopen() throws Exception {
        ProfileEditorSession editor = open();
        first(editor).setNbt("{}"); first(editor).setMatchNbt("{}"); first(editor).setMatch(StackSpec.NbtMatch.EXACT);
        assertTrue(editor.dirty()); assertTrue(editor.save()); assertFalse(editor.dirty());
        assertEquals("{}", first(editor).getNbt()); assertEquals("{}", first(editor).getMatchNbt());
        first(editor).setNbt(null); first(editor).setMatch(null); first(editor).setMatchNbt(null);
        assertTrue(editor.dirty()); assertTrue(editor.save());
        assertNull(first(editor).getNbt()); assertNull(first(editor).getMatch()); assertNull(first(editor).getMatchNbt());
    }
    @Test public void successfulSaveUsesExactSourceReloadsHandleAndKeepsOldSnapshotFrozen() throws Exception {
        ProfileEditorSession editor = open();
        LoadoutProfile before = editor.expected();
        ProfileDraft oldDraft = editor.draft();
        oldDraft.setName("saved once"); assertTrue(editor.save());
        assertNotSame(oldDraft, editor.draft()); assertFalse(editor.dirty());
        assertEquals("Stationary basics", before.getName());
        assertEquals("saved once", repository.load(source).getName());
        editor.draft().setName("saved twice"); assertTrue(editor.save());
        assertEquals("saved twice", repository.load(source).getName());
        assertFalse(Files.exists(source.resolveSibling("default.json")));
    }
    @Test public void staleOrCorruptSourceRetainsDraftAndRequiresExplicitReload() throws Exception {
        ProfileEditorSession editor = open();
        editor.draft().setName("keep my edits"); editor.setCount(first(editor), "003");
        byte[] external = (new String(Files.readAllBytes(source), StandardCharsets.UTF_8) + " \n").getBytes(StandardCharsets.UTF_8);
        Files.write(source, external);
        assertFalse(editor.save()); assertTrue(editor.reloadRequired());
        assertEquals("keep my edits", editor.draft().getName()); assertEquals("003", editor.count(first(editor)));
        assertFalse(editor.save()); assertArrayEquals(external, Files.readAllBytes(source));
        Files.write(source, "{broken".getBytes(StandardCharsets.UTF_8));
        assertFalse(editor.reload()); assertEquals("keep my edits", editor.draft().getName());
        Files.write(source, external);
        assertTrue(editor.reload()); assertFalse(editor.reloadRequired()); assertFalse(editor.dirty());
        assertEquals("1", editor.count(first(editor)));
    }
    @Test public void duplicateIdCannotRedirectSaveEvenIfSelectedSourceIsLaterRemoved() throws Exception {
        ProfileEditorSession editor = open();
        Path duplicate = source.resolveSibling("other.json"); Files.copy(source, duplicate);
        byte[] original = Files.readAllBytes(duplicate);
        editor.draft().setName("draft"); assertFalse(editor.save()); assertTrue(editor.reloadRequired());
        assertTrue(editor.message().contains("Duplicate")); Files.delete(source);
        assertFalse(editor.reload()); assertEquals(source, editor.source);
        assertEquals("draft", editor.draft().getName()); assertArrayEquals(original, Files.readAllBytes(duplicate));
    }
    @Test public void restorationIsRecheckedImmediatelyBeforeSaveAndCanBeRetriedAfterAcknowledgment() throws Exception {
        ProfileEditorSession editor = open(); byte[] original = Files.readAllBytes(source);
        editor.draft().setName("pending"); allowed.set(false);
        assertFalse(editor.canSave()); assertFalse(editor.save()); assertFalse(editor.reloadRequired());
        assertArrayEquals(original, Files.readAllBytes(source)); assertTrue(editor.dirty());
        allowed.set(true); assertTrue(editor.save()); assertFalse(editor.dirty());
    }
    @Test public void singleOnlyProfileCanSaveWithoutSeedSamplingOrFullCounts() throws Exception {
        ProfileEditorSession editor = open();
        for (int index = 1; index < 10; index++) {
            ProfileDraft.Slot slot = editor.draft().getSlots().get(index);
            slot.setKind(SlotRule.Kind.EMPTY); slot.getAlternatives().clear();
        }
        assertEquals("", editor.validate());
        assertTrue(editor.applicability().contains("Single: available"));
        assertTrue(editor.applicability().contains("Full stack counts: []"));
        assertTrue(editor.applicability().contains("does not guarantee"));
        assertTrue(editor.save());
    }
    @Test public void removedCandidateInvalidTextCannotPoisonRemainingDraft() throws Exception {
        ProfileEditorSession editor = open();
        ProfileDraft.Slot slot = editor.draft().getSlots().get(0);
        ProfileDraft.Candidate added = new ProfileDraft.Candidate("minecraft:stone", 1);
        slot.getAlternatives().add(added); editor.setCount(added, "");
        assertFalse(editor.validate().isEmpty()); slot.getAlternatives().remove(added);
        assertEquals("", editor.validate()); assertFalse(editor.dirty());
    }
    @Test public void gateFailsClosedForJoinRecoveryLanAndDisconnectedClient() {
        assertFalse(ProfileEditorSession.allowed(true, true, true, null));
        for (String phase : new String[] {"BACKING_UP", "PREPARING", "COUNTDOWN", "GO", "RUNNING", "SUCCESS", "INVALID", "RESTORING", "ERROR_LOCKED", "unknown"})
            assertFalse(phase, ProfileEditorSession.allowed(true, true, true, new TrainerProtocol.View.Builder().phase(phase).build()));
        for (String phase : new String[] {"IDLE", "ERROR"}) {
            TrainerProtocol.View view = new TrainerProtocol.View.Builder().phase(phase).build();
            assertTrue(ProfileEditorSession.allowed(true, true, true, view));
            assertFalse(ProfileEditorSession.allowed(false, true, true, view));
            assertFalse(ProfileEditorSession.allowed(true, false, true, view));
            assertFalse(ProfileEditorSession.allowed(true, true, false, view));
        }
    }
}
