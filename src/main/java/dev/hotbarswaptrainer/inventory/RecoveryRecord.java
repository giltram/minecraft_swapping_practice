package dev.hotbarswaptrainer.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Versioned, world/player bound original inventory; never merge or give stacks on replay. */
public final class RecoveryRecord {
    public static final String KEY = "HotbarSwapTrainerRecovery";
    private final UUID playerId;
    private final UUID transactionId;
    private final String world;
    private final InventorySnapshot original;

    public RecoveryRecord(UUID playerId, UUID transactionId, String world, InventorySnapshot original) {
        this.playerId = playerId;
        this.transactionId = transactionId;
        this.world = world;
        this.original = original;
    }

    public InventorySnapshot getOriginal() { return original; }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 1);
        tag.putBoolean("active", true);
        tag.putString("player", playerId.toString());
        tag.putString("transaction", transactionId.toString());
        tag.putString("world", world);
        tag.put("original", original.toTag());
        tag.putString("checksum", checksum(tag));
        return tag;
    }

    public static RecoveryRecord fromTag(CompoundTag input, UUID expectedPlayer, String expectedWorld) {
        CompoundTag body = input.copy();
        String checksum = body.getString("checksum");
        body.remove("checksum");
        if (body.getInt("schemaVersion") != 1 || !body.getBoolean("active") || !checksum(body).equals(checksum)) {
            throw new IllegalArgumentException("Recovery version, active flag or checksum is invalid; keep this save for inspection");
        }
        UUID player = UUID.fromString(body.getString("player"));
        UUID transaction = UUID.fromString(body.getString("transaction"));
        String world = body.getString("world");
        if (!expectedPlayer.equals(player) || !expectedWorld.equals(world)) {
            throw new IllegalArgumentException("Recovery belongs to another player or world path");
        }
        return new RecoveryRecord(player, transaction, world, InventorySnapshot.fromTag(body.getCompound("original")));
    }

    /** Canonical compound ordering with NBT type IDs; list order and numeric types remain significant. */
    static String checksum(Tag tag) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(tag).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte part : digest) value.append(String.format("%02x", part & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String canonical(Tag tag) {
        StringBuilder result = new StringBuilder().append(tag.getType()).append(':');
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            List<String> keys = new ArrayList<>(compound.getKeys());
            Collections.sort(keys);
            for (String key : keys) {
                String value = canonical(compound.get(key));
                result.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value);
            }
        } else if (tag instanceof ListTag) {
            for (Tag child : (ListTag) tag) {
                String value = canonical(child);
                result.append(value.length()).append(':').append(value);
            }
        } else {
            result.append(tag.toString());
        }
        return result.toString();
    }
}
