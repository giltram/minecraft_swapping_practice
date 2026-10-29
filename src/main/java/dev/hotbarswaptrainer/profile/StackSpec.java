package dev.hotbarswaptrainer.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

/** A generation template and a separate, explicit NBT matching predicate. */
public final class StackSpec {
    public enum NbtMatch { EXACT, FIELDS }

    private final StackValue template;
    private final NbtMatch nbtMatch;
    private final CompoundTag matchNbt;

    public StackSpec(StackValue template, NbtMatch nbtMatch, CompoundTag matchNbt) {
        this.template = Objects.requireNonNull(template, "template");
        if (template.isEmpty()) throw new IllegalArgumentException("StackSpec cannot generate air");
        this.nbtMatch = Objects.requireNonNull(nbtMatch, "nbtMatch");
        this.matchNbt = (matchNbt == null ? template.getNbt() : matchNbt).copy();
        NbtTemplateValidator.validate(template.getNbt());
        NbtTemplateValidator.validate(this.matchNbt);
        if (!matches(template)) throw new IllegalArgumentException("Generation NBT must satisfy matching NBT");
    }

    public static StackSpec exact(String itemId, int count, CompoundTag nbt) {
        return new StackSpec(StackValue.of(itemId, count, nbt), NbtMatch.EXACT, nbt);
    }

    public StackValue getTemplate() { return template; }
    public NbtMatch getNbtMatch() { return nbtMatch; }
    public CompoundTag getMatchNbt() { return matchNbt.copy(); }

    public boolean matches(StackValue value) {
        if (value.isEmpty() || !template.getItemId().equals(value.getItemId())
                || template.getCount() != value.getCount()) return false;
        CompoundTag actual = value.getNbt();
        return nbtMatch == NbtMatch.EXACT ? matchNbt.equals(actual) : containsFields(actual, matchNbt);
    }

    private static boolean containsFields(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.getKeys()) {
            Tag actualValue = actual.get(key);
            Tag expectedValue = expected.get(key);
            if (actualValue == null) return false;
            if (expectedValue instanceof CompoundTag && actualValue instanceof CompoundTag) {
                if (!containsFields((CompoundTag) actualValue, (CompoundTag) expectedValue)) return false;
            } else if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }
}
