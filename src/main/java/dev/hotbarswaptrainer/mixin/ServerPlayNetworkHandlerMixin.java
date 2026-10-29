package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.task.InventoryLayout;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only adapts verified vanilla boundaries; session policy remains in SessionManager. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Unique private static final String TRAINER_THREAD_BOUNDARY =
            "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Shadow public ServerPlayerEntity player;
    @Unique private InventoryLayout trainer$beforeSwap;
    @Unique private int trainer$beforeSelectedSlot;
    @Unique private boolean trainer$observeSelection;

    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER), cancellable = true)
    private void trainer$beforePlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        trainer$beforeSwap = null;
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            if (!TrainerMod.SESSIONS.beforeSwap(player)) {
                ci.cancel();
            } else if (InventoryService.isProtected(player) && !player.isSpectator()) {
                trainer$beforeSwap = InventoryService.readLayout(player.inventory);
            }
        } else if (TrainerMod.SESSIONS.guardAction(player, "Only hotbar selection and Swap Hands are allowed")) {
            PlayerActionC2SPacket.Action action = packet.getAction();
            if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    || action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                    || action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
                player.networkHandler.sendPacket(new PlayerActionResponseS2CPacket(packet.getPos(),
                        player.getServerWorld().getBlockState(packet.getPos()), action, false, "trainer protection"));
                player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getServerWorld(), packet.getPos()));
            }
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At("RETURN"))
    private void trainer$afterPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        InventoryLayout before = trainer$beforeSwap;
        trainer$beforeSwap = null;
        if (before != null) TrainerMod.SESSIONS.afterSwap(player, before);
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER))
    private void trainer$beforeSelection(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        trainer$beforeSelectedSlot = player.inventory.selectedSlot;
        trainer$observeSelection = InventoryService.isProtected(player);
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("RETURN"))
    private void trainer$afterSelection(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        boolean observe = trainer$observeSelection;
        trainer$observeSelection = false;
        if (observe) TrainerMod.SESSIONS.afterSelectedSlot(player, trainer$beforeSelectedSlot);
    }

    @Inject(method = {"onClickWindow", "onCreativeInventoryAction", "onPickFromInventory", "onCraftRequest",
            "onButtonClick", "onBookUpdate", "onPlayerInteractItem", "onPlayerInteractEntity"},
            at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER), cancellable = true)
    private void trainer$guardInventoryAction(CallbackInfo ci) {
        if (TrainerMod.SESSIONS.guardAction(player, "Inventory transfer or item interaction is not allowed")) ci.cancel();
    }

    @Inject(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER), cancellable = true)
    private void trainer$guardBlockInteraction(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (!TrainerMod.SESSIONS.guardAction(player, "Block interaction is not allowed")) return;
        BlockPos hit = packet.getBlockHitResult().getBlockPos();
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getServerWorld(), hit));
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getServerWorld(), hit.offset(packet.getBlockHitResult().getSide())));
        ci.cancel();
    }

    @Inject(method = "onGameMessage", at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER), cancellable = true)
    private void trainer$guardCommands(ChatMessageC2SPacket packet, CallbackInfo ci) {
        // Match vanilla's normalization so leading whitespace cannot bypass the command guard.
        if (StringUtils.normalizeSpace(packet.getChatMessage()).startsWith("/")
                && TrainerMod.SESSIONS.guardAction(player, "Commands are unavailable during an inventory transaction")) ci.cancel();
    }

    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = TRAINER_THREAD_BOUNDARY, shift = At.Shift.AFTER), cancellable = true)
    private void trainer$guardMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (TrainerMod.SESSIONS.guardMovement(player, packet)) ci.cancel();
    }
}
