package dev.hotbarswaptrainer.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ProfileRepositoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void profileValidationReportsExactPathsAndRejectsUnknownItemsAndCounts() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), id -> "minecraft:stone".equals(id) ? 64 : 0);
        JsonObject json = validProfile();
        assertEquals("test", repository.parse(json.toString(), "fixture.json").getId());
        JsonObject stack = json.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("alternatives").get(0).getAsJsonObject();
        stack.addProperty("item", "minecraft:missing");
        expectError(repository, json, "slots[0].alternatives[0].item");
        stack.addProperty("item", "minecraft:stone");
        stack.addProperty("count", 65);
        expectError(repository, json, "slots[0].alternatives[0].count");
        stack.addProperty("count", 1.5);
        expectError(repository, json, "expected an integer");
        stack.addProperty("count", 1);
        stack.addProperty("nbt", "{broken:");
        expectError(repository, json, "slots[0].alternatives[0].nbt");
        stack.remove("nbt");
        stack.addProperty("match", "ANY");
        expectError(repository, json, "expected EXACT or FIELDS");
    }

    @Test
    public void positionsAreExplicitAndDuplicatesMissingPositionsAndVersionsFail() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), id -> 64);
        JsonObject json = validProfile();
        json.getAsJsonArray("slots").get(1).getAsJsonObject().addProperty("slot", "hotbar1");
        expectError(repository, json, "duplicate position hotbar1");
        json = validProfile();
        json.getAsJsonArray("slots").remove(9);
        expectError(repository, json, "missing offhand");
        json = validProfile();
        json.addProperty("schemaVersion", 2);
        expectError(repository, json, "supported version is 1");
        json = validProfile();
        json.addProperty("typo", true);
        expectError(repository, json, "typo: unknown field");
        json = validProfile();
        json.addProperty("id", new String(new char[129]).replace('\0', 'a'));
        expectError(repository, json, "1..128");
        json = validProfile();
        for (int index = 0; index < 10; index++) {
            json.getAsJsonArray("slots").get(index).getAsJsonObject().addProperty("kind", "FLEXIBLE");
        }
        expectError(repository, json, "REQUIRED or OPTIONAL destination");
    }

    @Test
    public void invalidFilesRemainUntouchedAndDoNotHideValidProfiles() throws Exception {
        Path directory = temporary.getRoot().toPath();
        Path broken = directory.resolve("broken.json");
        byte[] original = "{not-json".getBytes(StandardCharsets.UTF_8);
        Files.write(broken, original);
        Files.write(directory.resolve("good.json"), validProfile().toString().getBytes(StandardCharsets.UTF_8));
        ProfileLoadResult result = new ProfileRepository(directory, id -> 64).loadAll();
        assertEquals(1, result.getProfiles().size());
        assertEquals(1, result.getErrors().size());
        assertArrayEquals(original, Files.readAllBytes(broken));
    }

    @Test
    public void bundledDefaultIsInstalledOnceAndAllowsEightNineAndTenStacks() throws Exception {
        Path directory = temporary.getRoot().toPath();
        ProfileRepository repository = new ProfileRepository(directory, id -> 64);
        repository.installDefaultIfMissing();
        LoadoutProfile profile = repository.load(directory.resolve("default.json"));
        assertEquals(java.util.Arrays.asList(8, 9, 10), profile.getFeasibleStackCounts());
        Files.write(directory.resolve("default.json"), "broken".getBytes(StandardCharsets.UTF_8));
        repository.installDefaultIfMissing();
        assertEquals("broken", new String(Files.readAllBytes(directory.resolve("default.json")), StandardCharsets.UTF_8));
    }

    @Test
    public void stackValuesAndProfileHashUseStructuralTypedNbtAndDeepCopies() throws Exception {
        CompoundTag firstNbt = StringNbtReader.parse("{b:2,a:{nested:1b},list:[{y:2,x:1}]}");
        CompoundTag reordered = StringNbtReader.parse("{list:[{x:1,y:2}],a:{nested:1b},b:2}");
        StackValue first = StackValue.of("minecraft:stone", 64, firstNbt);
        StackValue second = StackValue.of("minecraft:stone", 64, reordered);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, StackValue.of("minecraft:stone", 64, StringNbtReader.parse("{b:2,a:{nested:1},list:[{y:2,x:1}]}")));
        firstNbt.putString("changed", "source");
        first.getNbt().putString("changed", "getter");
        assertEquals(first, second);
        List<SlotRule> firstRules = new ArrayList<>(Collections.nCopies(10, new SlotRule(SlotRule.Kind.EMPTY, Collections.emptyList())));
        firstRules.set(0, new SlotRule(SlotRule.Kind.REQUIRED, Collections.singletonList(new StackSpec(first, StackSpec.NbtMatch.EXACT, null))));
        List<SlotRule> secondRules = new ArrayList<>(firstRules);
        secondRules.set(0, new SlotRule(SlotRule.Kind.REQUIRED, Collections.singletonList(new StackSpec(second, StackSpec.NbtMatch.EXACT, null))));
        LoadoutProfile firstProfile = new LoadoutProfile("same", "Same", firstRules);
        LoadoutProfile secondProfile = new LoadoutProfile("same", "Same", secondRules);
        assertEquals(firstProfile.getHash(), secondProfile.getHash());
        firstRules.set(0, new SlotRule(SlotRule.Kind.EMPTY, Collections.emptyList()));
        assertEquals(SlotRule.Kind.REQUIRED, firstProfile.getSlots().get(0).getKind());
        try { firstProfile.getSlots().clear(); fail("profile rules must be immutable"); }
        catch (UnsupportedOperationException expected) { /* immutable contract */ }
    }

    @Test
    public void fieldMatchersRetainPotionDistinctionsAndConservationCanRemainExact() throws Exception {
        StackValue template = StackValue.of("minecraft:potion", 1,
                StringNbtReader.parse("{Potion:\"minecraft:fire_resistance\",display:{Name:'\"fire\"'},Extra:1}"));
        StackSpec fields = new StackSpec(template, StackSpec.NbtMatch.FIELDS,
                StringNbtReader.parse("{Potion:\"minecraft:fire_resistance\",display:{Name:'\"fire\"'}}"));
        assertTrue(fields.matches(template));
        assertFalse(fields.matches(StackValue.of("minecraft:potion", 1,
                StringNbtReader.parse("{Potion:\"minecraft:long_fire_resistance\",display:{Name:'\"fire\"'}}"))));
        assertFalse(fields.matches(StackValue.of("minecraft:splash_potion", 1, template.getNbt())));
        assertFalse(fields.matches(StackValue.of("minecraft:potion", 2, template.getNbt())));
        assertFalse(fields.matches(StackValue.of("minecraft:potion", 1, StringNbtReader.parse("{Potion:\"minecraft:fire_resistance\"}"))));
    }

    @Test
    public void oversizedUnserializableAndDeepNbtFailsBeforeAProfileCanStart() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), id -> 64);
        JsonObject json = validProfile();
        JsonObject stack = json.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("alternatives").get(0).getAsJsonObject();
        stack.addProperty("nbt", "{Name:\"" + repeated('a', 70000) + "\"}");
        expectError(repository, json, "slots[0].alternatives[0].nbt");
        stack.addProperty("nbt", "{Name:\"" + repeated('\u0800', 22000) + "\"}");
        expectError(repository, json, "cannot be serialized to binary format");
        stack.addProperty("nbt", "{Name:\"" + repeated('a', 40000) + "\"}");
        expectError(repository, json, "Binary NBT exceeds 32 KiB");
        String nested = "{}";
        for (int level = 0; level < 70; level++) nested = "{nested:" + nested + "}";
        stack.addProperty("nbt", nested);
        expectError(repository, json, "nesting exceeds 64");
        CompoundTag tooLong = new CompoundTag();
        tooLong.putString("Name", repeated('\u0800', 22000));
        try { StackSpec.exact("minecraft:stone", 1, tooLong); fail("programmatic templates must also serialize"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("binary format")); }
        Path oversized = temporary.getRoot().toPath().resolve("oversized.json");
        Files.write(oversized, new byte[ProfileRepository.MAX_PROFILE_BYTES + 1]);
        try { repository.load(oversized); fail("oversized files must fail"); }
        catch (ProfileException expected) { assertTrue(expected.getMessage().contains("exceeds 1 MiB")); }
    }

    @Test
    public void normalizedGenerationNbtDefinesOnlyImplicitExactMatching() throws Exception {
        ItemCatalog catalog = new ItemCatalog() {
            @Override public int maximumStackSize(String itemId) { return 64; }
            @Override public StackValue normalizeTemplate(StackValue value) {
                CompoundTag normalized = value.getNbt();
                normalized.putInt("Damage", 0);
                return StackValue.of(value.getItemId(), value.getCount(), normalized);
            }
        };
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), catalog);
        JsonObject json = validProfile();
        StackSpec implicit = repository.parse(json.toString(), "fixture.json").getSlots().get(0).getAlternatives().get(0);
        assertEquals(0, implicit.getTemplate().getNbt().getInt("Damage"));
        assertTrue(implicit.getTemplate().getNbt().contains("Damage", 3));
        assertTrue(implicit.matches(implicit.getTemplate()));
        assertEquals(implicit.getTemplate().getNbt(), implicit.getMatchNbt());

        JsonObject stack = json.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("alternatives").get(0).getAsJsonObject();
        stack.addProperty("matchNbt", "{}");
        expectError(repository, json, "slots[0].alternatives[0]");
        stack.addProperty("match", "FIELDS");
        StackSpec fields = repository.parse(json.toString(), "fixture.json").getSlots().get(0).getAlternatives().get(0);
        assertTrue(fields.getMatchNbt().isEmpty());
        assertTrue(fields.matches(fields.getTemplate()));
    }

    private static String repeated(char character, int count) { return new String(new char[count]).replace('\0', character); }

    private static JsonObject validProfile() {
        JsonObject profile = new JsonObject();
        profile.addProperty("schemaVersion", 1);
        profile.addProperty("id", "test");
        profile.addProperty("name", "Test");
        JsonArray slots = new JsonArray();
        for (int index = 0; index < 10; index++) {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", ProfileRepository.slotName(index));
            slot.addProperty("kind", index == 0 ? "REQUIRED" : "EMPTY");
            if (index == 0) {
                JsonObject stack = new JsonObject();
                stack.addProperty("item", "minecraft:stone");
                stack.addProperty("count", 64);
                JsonArray alternatives = new JsonArray();
                alternatives.add(stack);
                slot.add("alternatives", alternatives);
            }
            slots.add(slot);
        }
        profile.add("slots", slots);
        return profile;
    }

    private static void expectError(ProfileRepository repository, JsonObject value, String expected) throws Exception {
        try { repository.parse(value.toString(), "fixture.json"); fail("Expected profile error containing " + expected); }
        catch (ProfileException failure) { assertTrue(failure.getMessage(), failure.getMessage().contains(expected)); }
    }
}
