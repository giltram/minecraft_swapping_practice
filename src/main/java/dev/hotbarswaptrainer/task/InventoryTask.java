package dev.hotbarswaptrainer.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable task and inverse-swap proof. The target is one witness, not hidden token identity. */
public final class InventoryTask {
    private final UUID id;
    private final TrainingMode mode;
    private final long seed;
    private final String profileHash;
    private final InventoryLayout initial;
    private final InventoryLayout target;
    private final List<ResolvedSlotRule> slotRules;
    private final List<Integer> solution;
    private final int destination;

    InventoryTask(UUID id, TrainingMode mode, long seed, String profileHash, InventoryLayout initial,
                  InventoryLayout target, List<ResolvedSlotRule> slotRules, List<Integer> solution, int destination) {
        this.id = id;
        this.mode = mode;
        this.seed = seed;
        this.profileHash = profileHash;
        this.initial = initial;
        this.target = target;
        this.slotRules = Collections.unmodifiableList(new ArrayList<>(slotRules));
        this.solution = Collections.unmodifiableList(new ArrayList<>(solution));
        this.destination = destination;
    }

    public UUID getId() { return id; }
    public TrainingMode getMode() { return mode; }
    public long getSeed() { return seed; }
    public String getProfileHash() { return profileHash; }
    public InventoryLayout getInitial() { return initial; }
    public InventoryLayout getTarget() { return target; }
    public List<ResolvedSlotRule> getSlotRules() { return slotRules; }
    public List<Integer> getSolution() { return solution; }
    public int getInitialSelectedSlot() { return 0; }
    public int getDestination() { return destination; }
}
