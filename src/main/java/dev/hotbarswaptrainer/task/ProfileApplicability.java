package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.SlotRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact mode preconditions on an already structurally valid profile; no seed sampling. */
public final class ProfileApplicability {
    private final List<Integer> singleDestinations;
    private final List<Integer> fullStackCounts;

    private ProfileApplicability(List<Integer> singleDestinations, List<Integer> fullStackCounts) {
        this.singleDestinations = Collections.unmodifiableList(singleDestinations);
        this.fullStackCounts = Collections.unmodifiableList(fullStackCounts);
    }

    public static ProfileApplicability assess(LoadoutProfile profile) {
        List<Integer> destinations = new ArrayList<>();
        for (int index = 0; index < profile.getSlots().size(); index++) {
            SlotRule rule = profile.getSlots().get(index);
            if (rule.isTrainableDestination() && rule.canGenerateStack()) destinations.add(index);
        }
        List<Integer> counts = new ArrayList<>();
        for (int count : profile.getFeasibleStackCounts()) if (count >= 8 && count <= 10) counts.add(count);
        return new ProfileApplicability(destinations, counts);
    }

    /** Every listed Single destination admits a non-complete initial state and a solution. */
    public List<Integer> getSingleDestinations() { return singleDestinations; }
    public boolean isSingleTrainable() { return !singleDestinations.isEmpty(); }
    /** Count feasibility only; does not promise that bounded Full generation will find a nontrivial task. */
    public List<Integer> getFullStackCounts() { return fullStackCounts; }
    public boolean hasFullStackCount() { return !fullStackCounts.isEmpty(); }
}
