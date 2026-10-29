package dev.hotbarswaptrainer.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One named position, including candidates for an optional flexible filler. */
public final class SlotRule {
    public enum Kind { REQUIRED, OPTIONAL, EMPTY, FLEXIBLE }

    private final Kind kind;
    private final List<StackSpec> alternatives;

    public SlotRule(Kind kind, List<StackSpec> alternatives) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
        if ((kind == Kind.REQUIRED || kind == Kind.OPTIONAL) && alternatives.isEmpty()) {
            throw new IllegalArgumentException(kind + " needs at least one alternative");
        }
        if (kind == Kind.EMPTY && !alternatives.isEmpty()) {
            throw new IllegalArgumentException("EMPTY cannot have alternatives");
        }
        for (StackSpec alternative : alternatives) Objects.requireNonNull(alternative, "alternative");
    }

    public Kind getKind() { return kind; }
    public List<StackSpec> getAlternatives() { return alternatives; }
    public boolean isTrainableDestination() { return kind == Kind.REQUIRED || kind == Kind.OPTIONAL; }
    public boolean canGenerateStack() { return !alternatives.isEmpty(); }

    public boolean matchesPresent(StackValue value) {
        for (StackSpec alternative : alternatives) if (alternative.matches(value)) return true;
        return false;
    }
}
