package dev.hotbarswaptrainer.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Frozen profile snapshot; no repository or registry access is needed after construction. */
public final class LoadoutProfile {
    public static final int SCHEMA_VERSION = 1;
    public static final int SLOT_COUNT = 10;
    public static final int MAX_ID_LENGTH = 128;

    private final String id;
    private final String name;
    private final List<SlotRule> slots;
    private final String hash;

    public LoadoutProfile(String id, String name, List<SlotRule> slots) {
        if (id == null || id.length() > MAX_ID_LENGTH || !id.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Profile id must be 1..128 lowercase letters, digits, dots, dashes, or underscores");
        }
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Profile name is empty");
        if (slots.size() != SLOT_COUNT) throw new IllegalArgumentException("Profile must have exactly ten positions");
        boolean trainable = false;
        for (SlotRule slot : slots) {
            if (slot == null) throw new IllegalArgumentException("Profile contains a missing position");
            trainable |= slot.isTrainableDestination();
        }
        if (!trainable) throw new IllegalArgumentException("Profile needs a REQUIRED or OPTIONAL destination");
        this.id = id;
        this.name = name;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.hash = calculateHash();
    }

    public int getSchemaVersion() { return SCHEMA_VERSION; }
    public String getId() { return id; }
    public String getName() { return name; }
    public List<SlotRule> getSlots() { return slots; }
    public String getHash() { return hash; }

    public List<Integer> getFeasibleStackCounts() {
        int required = 0;
        int optional = 0;
        for (SlotRule slot : slots) {
            if (slot.getKind() == SlotRule.Kind.REQUIRED) required++;
            if ((slot.getKind() == SlotRule.Kind.OPTIONAL || slot.getKind() == SlotRule.Kind.FLEXIBLE)
                    && slot.canGenerateStack()) optional++;
        }
        List<Integer> result = new ArrayList<>();
        for (int count = required; count <= required + optional; count++) result.add(count);
        return Collections.unmodifiableList(result);
    }

    private String calculateHash() {
        StringBuilder content = new StringBuilder("schema=1\nid=").append(id).append("\nname=").append(name);
        for (SlotRule slot : slots) {
            content.append("\nslot=").append(slot.getKind());
            for (StackSpec spec : slot.getAlternatives()) {
                StackValue value = spec.getTemplate();
                content.append('\n').append(value.getItemId()).append('|').append(value.getCount()).append('|')
                        .append(canonicalNbt(value.getNbt())).append('|').append(spec.getNbtMatch()).append('|')
                        .append(canonicalNbt(spec.getMatchNbt()));
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte element : digest) hex.append(String.format("%02x", element & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String canonicalNbt(Tag tag) {
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            List<String> keys = new ArrayList<>(compound.getKeys());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) result.append(key.length()).append(':').append(key).append('=')
                    .append(canonicalNbt(compound.get(key))).append(';');
            return result.append('}').toString();
        }
        if (tag instanceof ListTag) {
            StringBuilder result = new StringBuilder("[");
            for (Tag element : (ListTag) tag) result.append(canonicalNbt(element)).append(';');
            return result.append(']').toString();
        }
        return tag.getType() + ":" + tag.toString();
    }
}
