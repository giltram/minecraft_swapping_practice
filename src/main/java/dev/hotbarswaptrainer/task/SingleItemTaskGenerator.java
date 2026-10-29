package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import dev.hotbarswaptrainer.profile.StackValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class SingleItemTaskGenerator {
    public InventoryTask generate(LoadoutProfile profile, long seed) {
        Random random = new Random(seed);
        List<Integer> destinations = ProfileApplicability.assess(profile).getSingleDestinations();
        if (destinations.isEmpty()) throw new TaskGenerationException(profile.getId() + ": no trainable destination");
        int destination = destinations.get(random.nextInt(destinations.size()));
        List<StackSpec> candidates = profile.getSlots().get(destination).getAlternatives();
        StackSpec selected = candidates.get(random.nextInt(candidates.size()));
        StackValue value = selected.getTemplate();
        int source = random.nextInt(destination == InventoryLayout.OFFHAND ? 9 : 8);
        if (destination != InventoryLayout.OFFHAND && source >= destination) source++;
        InventoryLayout initial = InventoryLayout.empty().with(source, value);
        InventoryLayout target = InventoryLayout.empty().with(destination, value);
        List<ResolvedSlotRule> rules = new ArrayList<>();
        SlotRule empty = new SlotRule(SlotRule.Kind.EMPTY, Collections.emptyList());
        for (int index = 0; index < InventoryLayout.SIZE; index++) {
            rules.add(new ResolvedSlotRule(index == destination
                    ? new SlotRule(SlotRule.Kind.REQUIRED, Collections.singletonList(selected)) : empty, index == destination));
        }
        List<Integer> solution = destination == InventoryLayout.OFFHAND
                ? Collections.singletonList(source) : Arrays.asList(source, destination);
        UUID id = UUID.nameUUIDFromBytes((profile.getHash() + ":single:" + seed).getBytes(StandardCharsets.UTF_8));
        return new InventoryTask(id, TrainingMode.SINGLE_ITEM, seed, profile.getHash(), initial, target, rules, solution, destination);
    }
}
