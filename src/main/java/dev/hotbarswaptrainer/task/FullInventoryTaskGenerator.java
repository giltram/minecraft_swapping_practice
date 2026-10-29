package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import dev.hotbarswaptrainer.profile.StackValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class FullInventoryTaskGenerator {
    private static final int TARGET_ATTEMPTS = 32;
    private static final int SHUFFLE_ATTEMPTS = 16;
    private final TaskValidator validator = new TaskValidator();

    public InventoryTask generate(LoadoutProfile profile, long seed) {
        List<Integer> feasible = ProfileApplicability.assess(profile).getFullStackCounts();
        if (feasible.isEmpty()) throw new TaskGenerationException(profile.getId() + ": no feasible Full Inventory stack count in 8..10");
        return generate(profile, seed, feasible.get(new Random(seed ^ 0x41A7E2L).nextInt(feasible.size())));
    }

    public InventoryTask generate(LoadoutProfile profile, long seed, int stackCount) {
        if (stackCount < 8 || stackCount > 10 || !profile.getFeasibleStackCounts().contains(stackCount)) {
            throw new TaskGenerationException(profile.getId() + ": stack count " + stackCount + " is not feasible in 8..10");
        }
        Random random = new Random(seed);
        UUID id = UUID.nameUUIDFromBytes((profile.getHash() + ":full:" + seed + ":" + stackCount).getBytes(StandardCharsets.UTF_8));
        for (int targetAttempt = 0; targetAttempt < TARGET_ATTEMPTS; targetAttempt++) {
            List<Integer> optional = new ArrayList<>();
            int requiredCount = 0;
            for (int index = 0; index < InventoryLayout.SIZE; index++) {
                SlotRule rule = profile.getSlots().get(index);
                if (rule.getKind() == SlotRule.Kind.REQUIRED) requiredCount++;
                else if (rule.canGenerateStack()) optional.add(index);
            }
            Collections.shuffle(optional, random);
            List<Integer> selectedOptional = optional.subList(0, stackCount - requiredCount);
            InventoryLayout target = InventoryLayout.empty();
            List<ResolvedSlotRule> rules = new ArrayList<>();
            for (int index = 0; index < InventoryLayout.SIZE; index++) {
                SlotRule rule = profile.getSlots().get(index);
                boolean present = rule.getKind() == SlotRule.Kind.REQUIRED || selectedOptional.contains(index);
                rules.add(new ResolvedSlotRule(rule, present));
                if (present) {
                    List<StackSpec> choices = rule.getAlternatives();
                    target = target.with(index, choices.get(random.nextInt(choices.size())).getTemplate());
                }
            }
            for (int attempt = 0; attempt < SHUFFLE_ATTEMPTS; attempt++) {
                InventoryLayout initial = target;
                List<Integer> scramble = new ArrayList<>();
                int swapCount = 12 + random.nextInt(25);
                for (int swap = 0; swap < swapCount; swap++) {
                    int hotbar = random.nextInt(InventoryLayout.HOTBAR_SIZE);
                    initial = initial.withSwap(hotbar);
                    scramble.add(hotbar);
                }
                InventoryTask task = task(id, seed, profile, initial, target, rules, scramble);
                if (!validator.validate(task, initial).isComplete()) return task;
            }
            // Exhaustively inspect this multiset before deciding all its permutations are trivial.
            for (int destination = 0; destination < InventoryLayout.SIZE; destination++) {
                for (int source = 0; source < InventoryLayout.SIZE; source++) {
                    if (rules.get(destination).matches(target.get(source))) continue;
                    List<Integer> scramble = exchangePositions(source, destination);
                    InventoryLayout initial = target;
                    for (int swap : scramble) initial = initial.withSwap(swap);
                    return task(id, seed, profile, initial, target, rules, scramble);
                }
            }
        }
        throw new TaskGenerationException(TaskGenerationException.Reason.BOUNDED_SEARCH_EXHAUSTED, profile.getId() + ": no nontrivial task after " + TARGET_ATTEMPTS
                + " target samples; sampled multisets satisfy every permutation. Change alternatives or slot rules");
    }

    private static List<Integer> exchangePositions(int source, int destination) {
        List<Integer> swaps = new ArrayList<>();
        if (source == InventoryLayout.OFFHAND) swaps.add(destination);
        else if (destination == InventoryLayout.OFFHAND) swaps.add(source);
        else { swaps.add(source); swaps.add(destination); swaps.add(source); }
        return swaps;
    }

    private static InventoryTask task(UUID id, long seed, LoadoutProfile profile, InventoryLayout initial,
                                      InventoryLayout target, List<ResolvedSlotRule> rules, List<Integer> scramble) {
        List<Integer> solution = new ArrayList<>(scramble);
        Collections.reverse(solution);
        return new InventoryTask(id, TrainingMode.FULL_INVENTORY, seed, profile.getHash(), initial, target, rules, solution, -1);
    }
}
