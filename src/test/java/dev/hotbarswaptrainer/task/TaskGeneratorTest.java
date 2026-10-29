package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import dev.hotbarswaptrainer.profile.StackValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class TaskGeneratorTest {
    private final TaskValidator validator = new TaskValidator();

    @Test
    public void tenThousandFullTasksHaveNontrivialInitialStatesAndReversibleWitnesses() throws Exception {
        List<LoadoutProfile> profiles = Arrays.asList(defaultProfile(), duplicatesAndNbtProfile());
        FullInventoryTaskGenerator generator = new FullInventoryTaskGenerator();
        Set<Integer> observedCounts = new HashSet<>();
        Set<StackValue> observedOffhands = new HashSet<>();
        for (long seed = 0; seed < 10000; seed++) {
            LoadoutProfile profile = profiles.get((int) (seed % profiles.size()));
            int count = 8 + (int) (seed % 3);
            InventoryTask task = generator.generate(profile, seed, count);
            assertEquals("seed=" + seed, ValidationResult.Status.INCOMPLETE, validator.validate(task, task.getInitial()).getStatus());
            assertEquals(count, task.getInitial().countStacks());
            assertEquals(count, task.getTarget().countStacks());
            InventoryLayout solved = task.getInitial();
            for (int hotbar : task.getSolution()) solved = solved.withSwap(hotbar);
            assertEquals("seed=" + seed, task.getTarget(), solved);
            assertTrue("seed=" + seed, validator.validate(task, solved).isComplete());
            assertEquals(0, task.getInitialSelectedSlot());
            observedCounts.add(count);
            observedOffhands.add(task.getInitial().get(InventoryLayout.OFFHAND));
            if (seed % 1000 == 0) {
                InventoryTask repeat = generator.generate(profile, seed, count);
                assertEquals(task.getId(), repeat.getId());
                assertEquals(task.getInitial(), repeat.getInitial());
                assertEquals(task.getSolution(), repeat.getSolution());
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(8, 9, 10)), observedCounts);
        assertTrue(observedOffhands.size() > 5);
        assertTrue(observedOffhands.contains(StackValue.EMPTY));
    }

    @Test
    public void tenThousandSingleTasksUseExplicitDestinationsAndOneOrTwoSwaps() throws Exception {
        SingleItemTaskGenerator generator = new SingleItemTaskGenerator();
        LoadoutProfile profile = duplicatesAndNbtProfile();
        Set<Integer> destinations = new HashSet<>();
        for (long seed = 0; seed < 10000; seed++) {
            InventoryTask task = generator.generate(profile, seed);
            assertFalse(validator.validate(task, task.getInitial()).isComplete());
            assertEquals(1, task.getInitial().countStacks());
            assertTrue(task.getInitial().get(InventoryLayout.OFFHAND).isEmpty());
            assertTrue(task.getInitial().get(task.getDestination()).isEmpty());
            assertEquals(task.getDestination() == InventoryLayout.OFFHAND ? 1 : 2, task.getSolution().size());
            InventoryLayout solved = task.getInitial();
            for (int hotbar : task.getSolution()) solved = solved.withSwap(hotbar);
            assertTrue("seed=" + seed, validator.validate(task, solved).isComplete());
            destinations.add(task.getDestination());
        }
        assertEquals(9, destinations.size());
        assertTrue(destinations.contains(InventoryLayout.OFFHAND));
        assertFalse(destinations.contains(5)); // Flexible position is never a Single Item destination.
    }

    @Test
    public void impossibleCountsAndTrivialMultisetsFailWithBoundedClearErrors() throws Exception {
        FullInventoryTaskGenerator generator = new FullInventoryTaskGenerator();
        try { generator.generate(defaultProfile(), 4, 7); fail("count 7 must fail"); }
        catch (TaskGenerationException expected) { assertTrue(expected.getMessage().contains("not feasible")); }
        List<SlotRule> identical = Collections.nCopies(10, required(stack("minecraft:stone", 64, "{}")));
        try { generator.generate(new LoadoutProfile("identical", "Identical", identical), 3, 10); fail("trivial multiset must fail"); }
        catch (TaskGenerationException expected) { assertTrue(expected.getMessage().contains("nontrivial")); }
    }

    @Test
    public void deterministicFallbackFindsAMismatchWhenMostPermutationsAlreadyMatch() throws Exception {
        StackSpec stone = stack("minecraft:stone", 64, "{}");
        SlotRule flexible = new SlotRule(SlotRule.Kind.FLEXIBLE, Collections.singletonList(stone));
        List<SlotRule> rules = new ArrayList<>(Collections.nCopies(10, flexible));
        rules.set(0, required(stone));
        LoadoutProfile broad = new LoadoutProfile("broad", "Broad rules", rules);
        FullInventoryTaskGenerator generator = new FullInventoryTaskGenerator();
        boolean observedFallback = false;
        for (int seed = 0; seed < 100; seed++) {
            InventoryTask task = generator.generate(broad, seed, 9);
            observedFallback |= task.getSolution().size() <= 3;
            assertFalse(validator.validate(task, task.getInitial()).isComplete());
            InventoryLayout solved = task.getInitial();
            for (int hotbar : task.getSolution()) solved = solved.withSwap(hotbar);
            assertTrue(validator.validate(task, solved).isComplete());
        }
        assertTrue("Deterministic seed sample should exercise the bounded fallback", observedFallback);
    }

    @Test
    public void validationConservesFullNbtAndCountsBeforeCheckingPositions() throws Exception {
        InventoryTask task = new SingleItemTaskGenerator().generate(duplicatesAndNbtProfile(), 10);
        InventoryLayout target = task.getTarget();
        StackValue actual = target.get(task.getDestination());
        CompoundTag alteredNbt = actual.getNbt();
        alteredNbt.putString("Unexpected", "changed");
        InventoryLayout nbtLoss = target.with(task.getDestination(), StackValue.of(actual.getItemId(), actual.getCount(), alteredNbt));
        assertTrue(validator.validate(task, nbtLoss).isInvalid());
        assertTrue(validator.validate(task, target.with(task.getDestination(), StackValue.EMPTY)).isInvalid());
        assertTrue(validator.validate(task, target).isComplete());
    }

    @Test
    public void sameItemWithDifferentCountsCannotReplaceTheGeneratedWholeStack() throws Exception {
        StackSpec item = stack("minecraft:cobblestone", 64, "{}");
        List<SlotRule> slots = new ArrayList<>(Collections.nCopies(10,
                new SlotRule(SlotRule.Kind.EMPTY, Collections.emptyList())));
        slots.set(0, required(item));
        InventoryTask task = new SingleItemTaskGenerator().generate(new LoadoutProfile("count", "Count", slots), 2);
        InventoryLayout consumed = task.getTarget().with(0, StackValue.of("minecraft:cobblestone", 63, new CompoundTag()));
        assertTrue(validator.validate(task, consumed).isInvalid());
        InventoryLayout split = task.getTarget().with(0, StackValue.of("minecraft:cobblestone", 32, new CompoundTag()))
                .with(1, StackValue.of("minecraft:cobblestone", 32, new CompoundTag()));
        assertTrue(validator.validate(task, split).isInvalid());
    }

    @Test
    public void overlappingAlternativesAcceptOtherValidLayoutsWithoutHiddenStackIdentity() throws Exception {
        StackSpec stone = stack("minecraft:stone", 64, "{}");
        StackSpec cobble = stack("minecraft:cobblestone", 64, "{}");
        SlotRule either = new SlotRule(SlotRule.Kind.REQUIRED, Arrays.asList(stone, cobble));
        List<SlotRule> rules = new ArrayList<>(Collections.nCopies(10, either));
        rules.set(9, required(stack("minecraft:potion", 1, "{Potion:\"minecraft:fire_resistance\"}")));
        InventoryTask task = new FullInventoryTaskGenerator().generate(
                new LoadoutProfile("overlap", "Overlap", rules), 36, 10);
        InventoryLayout other = task.getTarget();
        int source = -1;
        int destination = -1;
        for (int a = 0; a < 9; a++) for (int b = a + 1; b < 9; b++) {
            if (!other.get(a).equals(other.get(b))) { source = a; destination = b; }
        }
        assertTrue(source >= 0);
        StackValue first = other.get(source);
        other = other.with(source, other.get(destination)).with(destination, first);
        assertNotEquals(task.getTarget(), other);
        assertTrue(validator.validate(task, other).isComplete());
    }

    @Test
    public void absentOptionalRequiresEmptyAndFlexibleAcceptsConservedLeftovers() throws Exception {
        InventoryTask task = new FullInventoryTaskGenerator().generate(defaultProfile(), 2, 8);
        assertFalse(task.getSlotRules().get(7).isPresent());
        InventoryLayout misplaced = task.getTarget().withSwap(7);
        assertEquals(ValidationResult.Status.INCOMPLETE, validator.validate(task, misplaced).getStatus());
        assertTrue(task.getSlotRules().get(5).matches(task.getTarget().get(0)));
        assertFalse(task.getSlotRules().get(7).matches(task.getTarget().get(0)));
    }

    @Test
    public void sharedBlockPoolsCanBeIndependentlySampledAndInterchanged() throws Exception {
        List<StackSpec> blocks = new ArrayList<>();
        for (String block : Arrays.asList("oak_leaves", "oak_planks", "dirt", "soul_sand", "nether_bricks", "netherrack", "gravel")) {
            blocks.add(stack("minecraft:" + block, 64, "{}"));
        }
        List<SlotRule> rules = new ArrayList<>(defaultProfile().getSlots());
        rules.set(3, new SlotRule(SlotRule.Kind.REQUIRED, blocks));
        rules.set(7, new SlotRule(SlotRule.Kind.REQUIRED, blocks));
        rules.set(5, required(stack("minecraft:oak_door", 64, "{}")));
        LoadoutProfile profile = new LoadoutProfile("shared-blocks", "Shared block pools", rules);
        Set<StackValue> observed = new HashSet<>();
        boolean repeated = false;
        FullInventoryTaskGenerator generator = new FullInventoryTaskGenerator();
        for (int seed = 0; seed < 100; seed++) {
            InventoryTask task = generator.generate(profile, seed, 10);
            StackValue first = task.getTarget().get(3);
            StackValue spare = task.getTarget().get(7);
            observed.add(first);
            observed.add(spare);
            repeated |= first.equals(spare);
            InventoryLayout exchanged = task.getTarget().with(3, spare).with(7, first);
            assertTrue(validator.validate(task, exchanged).isComplete());
            assertFalse(validator.validate(task, task.getTarget().withSwap(3)).isComplete());
        }
        assertEquals(7, observed.size());
        assertTrue("Independent pools allow both slots to draw the same block", repeated);
    }

    private static LoadoutProfile defaultProfile() throws Exception {
        byte[] bytes;
        try (InputStream stream = TaskGeneratorTest.class.getResourceAsStream("/defaults/default.json")) {
            assertNotNull(stream);
            java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) != -1) data.write(buffer, 0, read);
            bytes = data.toByteArray();
        }
        return new ProfileRepository(Paths.get("unused"), id -> 64).parse(new String(bytes, StandardCharsets.UTF_8), "default");
    }

    private static LoadoutProfile duplicatesAndNbtProfile() throws Exception {
        StackSpec plain = stack("minecraft:cobblestone", 64, "{}");
        StackSpec named = stack("minecraft:cobblestone", 64, "{display:{Name:'\"building\"'}}");
        StackSpec potion = stack("minecraft:potion", 1, "{Potion:\"minecraft:fire_resistance\"}");
        StackSpec longPotion = stack("minecraft:potion", 1, "{Potion:\"minecraft:long_fire_resistance\"}");
        SlotRule overlap = new SlotRule(SlotRule.Kind.REQUIRED, Arrays.asList(plain, named));
        List<SlotRule> slots = new ArrayList<>(Collections.nCopies(10, overlap));
        slots.set(1, required(potion));
        slots.set(2, required(longPotion));
        slots.set(5, new SlotRule(SlotRule.Kind.FLEXIBLE, Collections.singletonList(named)));
        slots.set(7, new SlotRule(SlotRule.Kind.OPTIONAL, Arrays.asList(plain, potion)));
        return new LoadoutProfile("duplicates-nbt", "Duplicates and NBT", slots);
    }

    private static SlotRule required(StackSpec spec) { return new SlotRule(SlotRule.Kind.REQUIRED, Collections.singletonList(spec)); }
    private static StackSpec stack(String item, int count, String nbt) throws Exception { return StackSpec.exact(item, count, StringNbtReader.parse(nbt)); }
}
