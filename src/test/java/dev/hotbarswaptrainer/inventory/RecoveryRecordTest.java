package dev.hotbarswaptrainer.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.StringNbtReader;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class RecoveryRecordTest {
    private static final UUID PLAYER = UUID.fromString("85ac8304-6e4c-44e7-af49-89b10c8f2e61");
    private static final UUID TRANSACTION = UUID.fromString("a8bd0241-2c0e-4a99-ad6f-a54201103e82");
    private static final String WORLD = "/test/saves/recovery";

    @Test
    public void allFortyOnePositionsAndSelectedSlotRoundTripWithoutAliasing() {
        ListTag original = completeInventory();
        InventorySnapshot snapshot = new InventorySnapshot(original, 8);
        RecoveryRecord record = new RecoveryRecord(PLAYER, TRANSACTION, WORLD, snapshot);
        CompoundTag encoded = record.toTag();
        RecoveryRecord restored = RecoveryRecord.fromTag(encoded, PLAYER, WORLD);
        assertEquals(snapshot, restored.getOriginal());
        assertEquals(41, restored.getOriginal().toTag().getList("Inventory", 10).size());
        assertEquals(8, restored.getOriginal().toTag().getInt("SelectedItemSlot"));
        original.getCompound(0).putByte("Count", (byte) 1);
        encoded.getCompound("original").getList("Inventory", 10).getCompound(1).putString("id", "minecraft:dirt");
        CompoundTag returnedCopy = restored.getOriginal().toTag();
        returnedCopy.getList("Inventory", 10).clear();
        assertEquals(snapshot, restored.getOriginal());
        assertEquals(41, restored.getOriginal().toTag().getList("Inventory", 10).size());
        assertEquals(64, restored.getOriginal().toTag().getList("Inventory", 10).getCompound(0).getByte("Count"));
    }

    @Test
    public void checksumPreservesNumericTypesAndListOrderButIgnoresCompoundKeyOrder() throws Exception {
        CompoundTag first = StringNbtReader.parse("{a:1b,b:{c:2,d:[{x:1,y:2}]}}");
        CompoundTag reordered = StringNbtReader.parse("{b:{d:[{y:2,x:1}],c:2},a:1b}");
        assertEquals(RecoveryRecord.checksum(first), RecoveryRecord.checksum(reordered));
        assertNotEquals(RecoveryRecord.checksum(first), RecoveryRecord.checksum(StringNbtReader.parse("{a:1,b:{c:2,d:[{x:1,y:2}]}}")));
        assertNotEquals(RecoveryRecord.checksum(StringNbtReader.parse("{a:[1,2]}")), RecoveryRecord.checksum(StringNbtReader.parse("{a:[2,1]}")));
    }

    @Test
    public void corruptBodyWrongBindingUnknownVersionAndInactiveRecordsFailClosed() {
        CompoundTag valid = record().toTag();
        CompoundTag corrupt = valid.copy();
        corrupt.getCompound("original").getList("Inventory", 10).getCompound(0).putByte("Count", (byte) 1);
        rejectRecord(corrupt, PLAYER, WORLD);
        rejectRecord(valid, UUID.randomUUID(), WORLD);
        rejectRecord(valid, PLAYER, "/different/world");
        CompoundTag unsupported = valid.copy();
        unsupported.putInt("schemaVersion", 2);
        rejectRecord(resign(unsupported), PLAYER, WORLD);
        CompoundTag inactive = valid.copy();
        inactive.putBoolean("active", false);
        rejectRecord(resign(inactive), PLAYER, WORLD);
        CompoundTag noTransaction = valid.copy();
        noTransaction.remove("transaction");
        rejectRecord(resign(noTransaction), PLAYER, WORLD);
        CompoundTag noOriginal = valid.copy();
        noOriginal.remove("original");
        rejectRecord(resign(noOriginal), PLAYER, WORLD);
        CompoundTag malformedChecksum = valid.copy();
        malformedChecksum.putString("checksum", "short");
        rejectRecord(malformedChecksum, PLAYER, WORLD);
    }

    @Test
    public void duplicateOutOfRangeAndIncompleteInventoryEntriesAreRejected() {
        ListTag duplicate = completeInventory();
        duplicate.add(duplicate.getCompound(0).copy());
        rejectSnapshot(duplicate, 0);
        ListTag outOfRange = completeInventory();
        outOfRange.getCompound(0).putByte("Slot", (byte) 42);
        rejectSnapshot(outOfRange, 0);
        ListTag noId = completeInventory();
        noId.getCompound(0).remove("id");
        rejectSnapshot(noId, 0);
        ListTag zero = completeInventory();
        zero.getCompound(0).putByte("Count", (byte) 0);
        rejectSnapshot(zero, 0);
        rejectSnapshot(completeInventory(), 9);
        rejectSnapshot(completeInventory(), -1);
    }

    @Test
    public void malformedSnapshotTypesDoNotSilentlyBecomeAnEmptyInventory() {
        CompoundTag snapshotTag = new CompoundTag();
        ListTag wrongChildren = new ListTag();
        wrongChildren.add(StringTag.of("not an item compound"));
        snapshotTag.put("Inventory", wrongChildren);
        snapshotTag.putInt("SelectedItemSlot", 0);
        try { InventorySnapshot.fromTag(snapshotTag); fail("wrong child types must fail"); }
        catch (IllegalArgumentException expected) { /* fail closed */ }
        ListTag missingSlot = completeInventory();
        missingSlot.getCompound(0).remove("Slot");
        rejectSnapshot(missingSlot, 0);
        ListTag narrowedCount = completeInventory();
        narrowedCount.getCompound(0).putInt("Count", 257);
        rejectSnapshot(narrowedCount, 0);
        ListTag malformedStackTag = completeInventory();
        malformedStackTag.getCompound(0).putString("tag", "not a compound");
        rejectSnapshot(malformedStackTag, 0);
    }

    private static RecoveryRecord record() { return new RecoveryRecord(PLAYER, TRANSACTION, WORLD, new InventorySnapshot(completeInventory(), 4)); }

    private static ListTag completeInventory() {
        ListTag list = new ListTag();
        for (int slot = 0; slot < 36; slot++) list.add(stack(slot));
        for (int slot = 100; slot < 104; slot++) list.add(stack(slot));
        list.add(stack(150));
        return list;
    }

    private static CompoundTag stack(int slot) {
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) slot);
        stack.putString("id", "minecraft:stone");
        stack.putByte("Count", (byte) 64);
        CompoundTag tag = new CompoundTag();
        tag.putString("OriginalPosition", "slot " + slot);
        stack.put("tag", tag);
        return stack;
    }

    private static CompoundTag resign(CompoundTag tag) {
        tag.remove("checksum");
        tag.putString("checksum", RecoveryRecord.checksum(tag));
        return tag;
    }

    private static void rejectRecord(CompoundTag tag, UUID player, String world) {
        try { RecoveryRecord.fromTag(tag, player, world); fail("invalid recovery record must fail"); }
        catch (IllegalArgumentException expected) { /* fail closed */ }
    }

    private static void rejectSnapshot(ListTag tag, int selected) {
        try { new InventorySnapshot(tag, selected); fail("invalid inventory snapshot must fail"); }
        catch (IllegalArgumentException expected) { /* fail closed */ }
    }
}
