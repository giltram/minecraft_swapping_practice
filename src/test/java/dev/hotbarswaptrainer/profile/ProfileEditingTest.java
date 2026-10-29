package dev.hotbarswaptrainer.profile;

import dev.hotbarswaptrainer.task.FullInventoryTaskGenerator;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.ProfileApplicability;
import dev.hotbarswaptrainer.task.SingleItemTaskGenerator;
import dev.hotbarswaptrainer.task.TaskGenerationException;
import dev.hotbarswaptrainer.task.TaskValidator;
import dev.hotbarswaptrainer.task.ValidationResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ProfileEditingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String TYPED_NBT = "{b:1b,s:2s,i:3,l:4L,f:5.5f,d:6.25d,bytes:[B;1b,-2b],ints:[I;1,2],"
            + "longs:[L;3L,4L],list:[{nested:1b}],display:{Name:'\"完整 NBT\"'},extra:{keep:9}}";

    @Test
    public void completeEditRetainsAllRulesCandidatesTypedNbtAndFrozenSnapshots() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository repository = installed(id -> 64);
        byte[] original = Files.readAllBytes(file);
        LoadoutProfile before = repository.load(file);
        String oldHash = before.getHash();
        InventoryTask oldTask = new FullInventoryTaskGenerator().generate(before, 17);
        InventoryLayout oldInitial = oldTask.getInitial();
        ProfileRepository.Edit edit = repository.beginEdit(file, before);
        ProfileDraft draft = edit.getDraft();
        draft.setName("完整编辑");
        for (int index = 0; index < 10; index++) {
            ProfileDraft.Slot slot = draft.getSlots().get(index);
            slot.setKind(index < 7 ? SlotRule.Kind.REQUIRED : index == 7 ? SlotRule.Kind.OPTIONAL
                    : index == 8 ? SlotRule.Kind.EMPTY : SlotRule.Kind.FLEXIBLE);
            slot.getAlternatives().clear();
            if (index == 8) continue;
            ProfileDraft.Candidate candidate = new ProfileDraft.Candidate("minecraft:stone", index + 1);
            candidate.setNbt(TYPED_NBT);
            candidate.setMatch(index % 2 == 0 ? StackSpec.NbtMatch.EXACT : StackSpec.NbtMatch.FIELDS);
            candidate.setMatchNbt(index % 2 == 0 ? TYPED_NBT : "{b:1b,list:[{nested:1b}]}");
            slot.getAlternatives().add(candidate);
            slot.getAlternatives().add(new ProfileDraft.Candidate("minecraft:dirt", 32));
        }
        ProfileDraftValidation validation = repository.validateDraft(draft);
        assertTrue(validation.getError(), validation.isValid());
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(oldHash, before.getHash());
        assertSame(oldInitial, oldTask.getInitial());
        assertWitness(oldTask);
        LoadoutProfile saved = repository.save(edit);
        LoadoutProfile reloaded = new ProfileRepository(file.getParent(), id -> 64).load(file);
        assertEquals(before.getId(), reloaded.getId());
        assertEquals("完整编辑", reloaded.getName());
        assertEquals(validation.getProfile().getHash(), saved.getHash());
        assertEquals(saved.getHash(), reloaded.getHash());
        assertEquals(Arrays.asList(7, 8, 9), reloaded.getFeasibleStackCounts());
        assertEquals(Arrays.asList(8, 9), ProfileApplicability.assess(reloaded).getFullStackCounts());
        for (int index = 0; index < 10; index++) {
            SlotRule slot = reloaded.getSlots().get(index);
            assertEquals(draft.getSlots().get(index).getKind(), slot.getKind());
            if (index == 8) { assertTrue(slot.getAlternatives().isEmpty()); continue; }
            assertEquals(2, slot.getAlternatives().size());
            StackSpec candidate = slot.getAlternatives().get(0);
            assertEquals(index + 1, candidate.getTemplate().getCount());
            assertEquals("minecraft:stone", candidate.getTemplate().getItemId());
            assertEquals(StringNbtReader.parse(TYPED_NBT), candidate.getTemplate().getNbt());
            assertEquals(index % 2 == 0 ? StackSpec.NbtMatch.EXACT : StackSpec.NbtMatch.FIELDS, candidate.getNbtMatch());
            assertEquals(StringNbtReader.parse(index % 2 == 0 ? TYPED_NBT : "{b:1b,list:[{nested:1b}]}"), candidate.getMatchNbt());
            assertEquals("minecraft:dirt", slot.getAlternatives().get(1).getTemplate().getItemId());
            assertEquals(32, slot.getAlternatives().get(1).getTemplate().getCount());
        }
        draft.setName("unsaved again");
        draft.getSlots().get(0).getAlternatives().get(0).setNbt("{broken:");
        assertFalse(repository.validateDraft(draft).isValid());
        assertEquals(saved.getHash(), repository.load(file).getHash());
        assertEquals(oldHash, before.getHash());
        for (int seed = 0; seed < 30; seed++) {
            assertWitness(new SingleItemTaskGenerator().generate(reloaded, seed));
            for (int count : ProfileApplicability.assess(reloaded).getFullStackCounts()) {
                assertWitness(new FullInventoryTaskGenerator().generate(reloaded, seed, count));
            }
        }
    }

    @Test
    public void implicitExactTracksEditedGenerationAfterRuntimeNormalization() throws Exception {
        ProfileRepository repository = installed(normalizingCatalog());
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        ProfileDraft.Candidate candidate = edit.getDraft().getSlots().get(0).getAlternatives().get(0);
        assertNull(candidate.getMatch());
        assertNull(candidate.getMatchNbt());
        candidate.setNbt("{Custom:2b}");
        LoadoutProfile saved = repository.save(edit);
        StackSpec spec = saved.getSlots().get(0).getAlternatives().get(0);
        assertEquals(StringNbtReader.parse("{Custom:2b,Damage:0}"), spec.getTemplate().getNbt());
        assertEquals(spec.getTemplate().getNbt(), spec.getMatchNbt());
        assertEquals(saved.getHash(), repository.load(file).getHash());
        ProfileRepository.Edit reopenedEdit = repository.beginEdit(file, saved);
        ProfileDraft.Candidate reopened = reopenedEdit.getDraft().getSlots().get(0).getAlternatives().get(0);
        assertNull(reopened.getMatchNbt());
        assertEquals("{Custom:2b}", reopened.getNbt());
        reopened.setMatchNbt("{}");
        assertFalse(repository.validateDraft(reopenedEdit.getDraft()).isValid());
    }

    @Test
    public void explicitExactAndFieldsDefaultsKeepTheirDistinctMeaning() throws Exception {
        ProfileRepository repository = installed(normalizingCatalog());
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        ProfileDraft.Candidate candidate = edit.getDraft().getSlots().get(0).getAlternatives().get(0);
        candidate.setNbt("{Custom:1b}");
        candidate.setMatch(StackSpec.NbtMatch.EXACT);
        candidate.setMatchNbt("{Custom:1b,Damage:0}");
        LoadoutProfile exact = repository.save(edit);
        edit = repository.beginEdit(file, exact);
        candidate = edit.getDraft().getSlots().get(0).getAlternatives().get(0);
        assertEquals("{Custom:1b,Damage:0}", candidate.getMatchNbt());
        candidate.setNbt("{Custom:2b}");
        assertFalse(repository.validateDraft(edit.getDraft()).isValid());
        candidate.setMatch(StackSpec.NbtMatch.FIELDS);
        candidate.setMatchNbt(null);
        LoadoutProfile fields = repository.save(edit);
        StackSpec spec = repository.load(file).getSlots().get(0).getAlternatives().get(0);
        assertEquals(fields.getHash(), repository.load(file).getHash());
        assertEquals(StringNbtReader.parse("{Custom:2b}"), spec.getMatchNbt());
        assertTrue(spec.matches(spec.getTemplate()));
        // Typed subset matching must still reject an int in place of a byte.
        assertFalse(spec.matches(StackValue.of(spec.getTemplate().getItemId(), spec.getTemplate().getCount(),
                StringNbtReader.parse("{Custom:2,Damage:0}"))));
    }

    @Test
    public void invalidDraftsNeverWriteOrLeakIntoLoadedProfiles() throws Exception {
        ProfileRepository repository = installed(id -> id.startsWith("minecraft:") && !id.equals("minecraft:missing") ? 64 : 0);
        Path file = temporary.getRoot().toPath().resolve("default.json");
        byte[] original = Files.readAllBytes(file);
        LoadoutProfile before = repository.load(file);
        ProfileRepository.Edit edit = repository.beginEdit(file, before);
        ProfileDraft draft = edit.getDraft();
        assertFalse(repository.validateDraft(null).isValid());
        draft.setName(" ");
        invalidSave(repository, edit, "name");
        draft.setName("valid");
        ProfileDraft.Candidate candidate = draft.getSlots().get(0).getAlternatives().get(0);
        candidate.setItem("minecraft:missing");
        invalidSave(repository, edit, ".item");
        candidate.setItem("minecraft:stone");
        for (int count : new int[] {0, -1, 65}) {
            candidate.setCount(count);
            invalidSave(repository, edit, ".count");
        }
        candidate.setCount(1);
        candidate.setNbt("{broken:");
        invalidSave(repository, edit, ".nbt");
        candidate.setNbt("{v:1b}");
        candidate.setMatchNbt("{v:1}");
        invalidSave(repository, edit, "Generation NBT");
        candidate.setMatchNbt("[]");
        invalidSave(repository, edit, "matchNbt");
        candidate.setMatchNbt(null);
        candidate.setNbt("{v:'" + new String(new char[40000]).replace('\0', 'a') + "'}");
        invalidSave(repository, edit, "32 KiB");
        candidate.setNbt(null);
        draft.getSlots().get(0).setKind(SlotRule.Kind.EMPTY);
        invalidSave(repository, edit, "EMPTY");
        draft.getSlots().get(0).setKind(SlotRule.Kind.REQUIRED);
        draft.getSlots().get(0).getAlternatives().clear();
        invalidSave(repository, edit, "at least one");
        draft.getSlots().get(0).setKind(null);
        invalidSave(repository, edit, "kind");
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(before.getHash(), repository.load(file).getHash());
        try (java.util.stream.Stream<Path> files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    public void singleOnlyIsLegalAndSaveableWithoutFullCounts() throws Exception {
        ProfileRepository repository = installed(id -> 64);
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        for (int index = 1; index < 10; index++) {
            edit.getDraft().getSlots().get(index).setKind(SlotRule.Kind.EMPTY);
            edit.getDraft().getSlots().get(index).getAlternatives().clear();
        }
        assertTrue(repository.validateDraft(edit.getDraft()).isValid());
        LoadoutProfile saved = repository.save(edit);
        LoadoutProfile reloaded = repository.load(file);
        assertEquals(saved.getHash(), reloaded.getHash());
        ProfileApplicability applicability = ProfileApplicability.assess(reloaded);
        assertTrue(applicability.isSingleTrainable());
        assertEquals(Arrays.asList(0), applicability.getSingleDestinations());
        assertFalse(applicability.hasFullStackCount());
        for (int seed = 0; seed < 20; seed++) assertWitness(new SingleItemTaskGenerator().generate(reloaded, seed));
        try { new FullInventoryTaskGenerator().generate(reloaded, 1); fail("no Full count"); }
        catch (TaskGenerationException expected) { assertEquals(TaskGenerationException.Reason.MODE_PRECONDITION, expected.getReason()); }
    }

    @Test
    public void fullCountConditionsDoNotPromiseNontrivialBoundedGeneration() throws Exception {
        ProfileRepository repository = installed(id -> 64);
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        for (ProfileDraft.Slot slot : edit.getDraft().getSlots()) {
            slot.setKind(SlotRule.Kind.REQUIRED);
            slot.getAlternatives().clear();
            slot.getAlternatives().add(new ProfileDraft.Candidate("minecraft:stone", 1));
        }
        LoadoutProfile saved = repository.save(edit);
        LoadoutProfile reloaded = repository.load(file);
        assertEquals(saved.getHash(), reloaded.getHash());
        ProfileApplicability applicability = ProfileApplicability.assess(reloaded);
        assertTrue(applicability.isSingleTrainable());
        assertEquals(Arrays.asList(10), applicability.getFullStackCounts());
        assertWitness(new SingleItemTaskGenerator().generate(reloaded, 1));
        try { new FullInventoryTaskGenerator().generate(reloaded, 1); fail("all sampled layouts already solved"); }
        catch (TaskGenerationException expected) {
            assertEquals(TaskGenerationException.Reason.BOUNDED_SEARCH_EXHAUSTED, expected.getReason());
            assertTrue(expected.getMessage().contains("target samples"));
        }
    }

    @Test
    public void optionalOnlyAndFlexibleWithoutFillersAreLegalForSingle() throws Exception {
        ProfileRepository repository = installed(id -> 64);
        Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository.Edit edit = repository.beginEdit(file, repository.load(file));
        for (ProfileDraft.Slot slot : edit.getDraft().getSlots()) {
            slot.setKind(SlotRule.Kind.FLEXIBLE);
            slot.getAlternatives().clear();
        }
        ProfileDraft.Slot offhand = edit.getDraft().getSlots().get(9);
        offhand.setKind(SlotRule.Kind.OPTIONAL);
        offhand.getAlternatives().add(new ProfileDraft.Candidate("minecraft:stone", 1));
        LoadoutProfile saved = repository.save(edit);
        LoadoutProfile reloaded = repository.load(file);
        assertEquals(saved.getHash(), reloaded.getHash());
        assertEquals(Arrays.asList(0, 1), reloaded.getFeasibleStackCounts());
        assertEquals(Arrays.asList(9), ProfileApplicability.assess(reloaded).getSingleDestinations());
        assertFalse(ProfileApplicability.assess(reloaded).hasFullStackCount());
        for (int seed = 0; seed < 20; seed++) {
            InventoryTask task = new SingleItemTaskGenerator().generate(reloaded, seed);
            assertEquals(9, task.getDestination());
            assertWitness(task);
        }
        edit = repository.beginEdit(file, reloaded);
        edit.getDraft().getSlots().get(9).setKind(SlotRule.Kind.FLEXIBLE);
        invalidSave(repository, edit, "REQUIRED or OPTIONAL");
        assertEquals(saved.getHash(), repository.load(file).getHash());
    }

    @Test
    public void slotOrderStillKeepsImplicitExactWithRuntimeDefaults() throws Exception {
        ProfileRepository repository = installed(normalizingCatalog());
        Path file = temporary.getRoot().toPath().resolve("default.json");
        LoadoutProfile before = repository.load(file);
        LoadoutProfile reordered = repository.saveSlotOrder(before, new int[] {1,0,2,3,4,5,6,7,8,9});
        LoadoutProfile reloaded = repository.load(file);
        assertEquals(reordered.getHash(), reloaded.getHash());
        assertEquals(before.getSlots().get(0).getAlternatives().get(0).getTemplate(),
                reloaded.getSlots().get(1).getAlternatives().get(0).getTemplate());
        ProfileDraft.Candidate moved = repository.beginEdit(file, reloaded).getDraft().getSlots().get(1).getAlternatives().get(0);
        assertNull(moved.getNbt());
        assertNull(moved.getMatch());
        assertNull(moved.getMatchNbt());
        assertWitness(new SingleItemTaskGenerator().generate(reloaded, 1));
        assertWitness(new FullInventoryTaskGenerator().generate(reloaded, 1));
    }

    private ProfileRepository installed(ItemCatalog catalog) throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), catalog);
        repository.installDefaultIfMissing();
        return repository;
    }

    private static ItemCatalog normalizingCatalog() {
        return new ItemCatalog() {
            @Override public int maximumStackSize(String item) { return 64; }
            @Override public StackValue normalizeTemplate(StackValue value) {
                CompoundTag nbt = value.getNbt();
                if (!nbt.contains("Damage")) nbt.putInt("Damage", 0);
                return StackValue.of(value.getItemId(), value.getCount(), nbt);
            }
        };
    }

    private static void invalidSave(ProfileRepository repository, ProfileRepository.Edit edit, String error) throws Exception {
        ProfileDraftValidation result = repository.validateDraft(edit.getDraft());
        assertFalse(result.isValid());
        assertTrue(result.getError(), result.getError().contains(error));
        try { repository.save(edit); fail("invalid draft"); }
        catch (ProfileException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(error)); }
    }

    static void assertWitness(InventoryTask task) {
        TaskValidator validator = new TaskValidator();
        assertEquals(ValidationResult.Status.INCOMPLETE, validator.validate(task, task.getInitial()).getStatus());
        InventoryLayout solved = task.getInitial();
        for (int swap : task.getSolution()) solved = solved.withSwap(swap);
        assertEquals(ValidationResult.Status.COMPLETE, validator.validate(task, solved).getStatus());
    }
}
