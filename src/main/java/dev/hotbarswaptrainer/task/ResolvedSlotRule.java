package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackValue;

/** OPTIONAL presence is frozen per task; FLEXIBLE remains permissive under conservation. */
public final class ResolvedSlotRule {
    private final SlotRule rule;
    private final boolean present;

    public ResolvedSlotRule(SlotRule rule, boolean present) {
        this.rule = rule;
        this.present = present;
    }

    public SlotRule getRule() { return rule; }
    public boolean isPresent() { return present; }

    public boolean matches(StackValue stack) {
        switch (rule.getKind()) {
            case FLEXIBLE: return true;
            case EMPTY: return stack.isEmpty();
            case OPTIONAL: return present ? rule.matchesPresent(stack) : stack.isEmpty();
            case REQUIRED: return rule.matchesPresent(stack);
            default: throw new AssertionError(rule.getKind());
        }
    }
}
