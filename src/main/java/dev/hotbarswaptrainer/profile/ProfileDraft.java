package dev.hotbarswaptrainer.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mutable, unvalidated editor input. Null optional fields mean schema defaults. No live profile is mutated. */
public final class ProfileDraft {
    private final String id;
    private String name;
    private final List<Slot> slots;

    ProfileDraft(String id, String name, List<Slot> slots) {
        this.id = id;
        this.name = name;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    /** Exactly ten mutable slots in hotbar/offhand order; positions cannot be added or removed. */
    public List<Slot> getSlots() { return slots; }

    public static final class Slot {
        private SlotRule.Kind kind;
        private final List<Candidate> alternatives = new ArrayList<>();

        public Slot(SlotRule.Kind kind) { this.kind = kind; }
        public SlotRule.Kind getKind() { return kind; }
        public void setKind(SlotRule.Kind kind) { this.kind = kind; }
        public List<Candidate> getAlternatives() { return alternatives; }
    }

    public static final class Candidate {
        private String item;
        private int count;
        private String nbt;
        private StackSpec.NbtMatch match;
        private String matchNbt;

        public Candidate(String item, int count) { this.item = item; this.count = count; }
        public String getItem() { return item; }
        public void setItem(String item) { this.item = item; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getNbt() { return nbt; }
        public void setNbt(String nbt) { this.nbt = nbt; }
        public StackSpec.NbtMatch getMatch() { return match; }
        public void setMatch(StackSpec.NbtMatch match) { this.match = match; }
        public String getMatchNbt() { return matchNbt; }
        public void setMatchNbt(String matchNbt) { this.matchNbt = matchNbt; }
    }
}
