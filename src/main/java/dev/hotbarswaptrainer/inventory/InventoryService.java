package dev.hotbarswaptrainer.inventory;

import dev.hotbarswaptrainer.profile.StackValue;
import dev.hotbarswaptrainer.task.InventoryLayout;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.HeldItemChangeS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns all mod inventory mutations. RUNNING swaps belong exclusively to vanilla. */
public final class InventoryService {
    public enum Mutation { SETUP, RESTORE, RECOVERY }
    private final ConfirmedPlayerSave persistence = new ConfirmedPlayerSave();

    public static boolean isProtected(ServerPlayerEntity player) {
        return ((RecoveryHolder) player).trainer$hasRecovery();
    }

    public static String worldIdentity(ServerPlayerEntity player) {
        return player.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().toString();
    }

    public void backup(ServerPlayerEntity player, UUID transaction) throws IOException {
        if (isProtected(player)) throw new IllegalStateException("An inventory recovery is already active");
        RecoveryRecord record = new RecoveryRecord(player.getUuid(), transaction, worldIdentity(player), InventorySnapshot.capture(player.inventory));
        ((RecoveryHolder) player).trainer$setRecovery(record.toTag());
        persistence.save(player); // No training items exist until both authoritative records read back correctly.
    }

    public void prepare(ServerPlayerEntity player, InventoryLayout layout, int selectedSlot) {
        if (!isProtected(player)) throw new IllegalStateException("Inventory setup requires a durable backup");
        replaceTrainingInventory(player, layout, selectedSlot, Mutation.SETUP);
        if (!layout.equals(readLayout(player.inventory))) {
            throw new IllegalStateException("Prepared inventory differs from the frozen task; original inventory will be restored");
        }
    }

    private void replaceTrainingInventory(ServerPlayerEntity player, InventoryLayout layout, int selectedSlot, Mutation reason) {
        player.inventory.clear();
        player.inventory.setCursorStack(ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) player.inventory.main.set(i, toItemStack(layout.get(i)));
        player.inventory.offHand.set(0, toItemStack(layout.get(InventoryLayout.OFFHAND)));
        player.inventory.selectedSlot = selectedSlot;
        player.inventory.markDirty();
        sync(player);
    }

    /** Recovery load replay is idempotent. Keep active until a separate confirmed save clears it. */
    public void restoreLoaded(ServerPlayerEntity player, CompoundTag recordTag) {
        ((RecoveryHolder) player).trainer$setRecovery(recordTag.copy());
        RecoveryRecord.fromTag(recordTag, player.getUuid(), worldIdentity(player)).getOriginal().restore(player.inventory);
    }

    public void restore(ServerPlayerEntity player) throws IOException {
        CompoundTag recordTag = ((RecoveryHolder) player).trainer$getRecovery();
        if (recordTag == null) return;
        RecoveryRecord record = RecoveryRecord.fromTag(recordTag, player.getUuid(), worldIdentity(player));
        record.getOriginal().restore(player.inventory);
        ((RecoveryHolder) player).trainer$setRecovery(null);
        try {
            persistence.save(player);
        } catch (IOException | RuntimeException failure) {
            ((RecoveryHolder) player).trainer$setRecovery(recordTag);
            throw failure;
        }
        sync(player);
    }

    public static boolean outsideTrainingAreaEmpty(PlayerInventory inventory) {
        for (int i = 9; i < inventory.main.size(); i++) if (!inventory.main.get(i).isEmpty()) return false;
        for (ItemStack armor : inventory.armor) if (!armor.isEmpty()) return false;
        return inventory.getCursorStack().isEmpty();
    }

    public static InventoryLayout readLayout(PlayerInventory inventory) {
        List<StackValue> values = new ArrayList<>();
        for (int i = 0; i < 9; i++) values.add(fromStack(inventory.main.get(i)));
        values.add(fromStack(inventory.offHand.get(0)));
        return new InventoryLayout(values);
    }

    public static StackValue fromStack(ItemStack stack) {
        return stack.isEmpty() ? StackValue.EMPTY : StackValue.of(Registry.ITEM.getId(stack.getItem()).toString(), stack.getCount(), stack.getTag());
    }

    public static ItemStack toItemStack(StackValue value) {
        if (value.isEmpty()) return ItemStack.EMPTY;
        Item item = Registry.ITEM.get(new Identifier(value.getItemId()));
        ItemStack stack = new ItemStack(item, value.getCount());
        CompoundTag tag = value.getNbt();
        if (!tag.isEmpty()) stack.setTag(tag);
        return stack;
    }

    public static void sync(ServerPlayerEntity player) {
        if (player.networkHandler == null) return;
        try {
            player.playerScreenHandler.sendContentUpdates();
            player.networkHandler.sendPacket(new InventoryS2CPacket(player.playerScreenHandler.syncId, player.playerScreenHandler.getStacks()));
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-1, -1, player.inventory.getCursorStack()));
            player.networkHandler.sendPacket(new HeldItemChangeS2CPacket(player.inventory.selectedSlot));
        } catch (RuntimeException disconnected) {
            org.apache.logging.log4j.LogManager.getLogger("HotbarSwapTrainer")
                    .warn("Inventory is authoritative on the server; could not synchronize client", disconnected);
        }
    }
}
