package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.smoke.SaveWriteFault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.*;
import java.util.zip.GZIPOutputStream;

/** Exact 1.16.1 intermediary hook: NbtIo.writeCompressed(CompoundTag, OutputStream). */
@Mixin(value = NbtIo.class, remap = false)
public abstract class CompressedSaveCutMixin {
    @Inject(method = "method_10634", at = @At("HEAD"))
    private static void acceptance$select(CompoundTag tag, OutputStream stream, CallbackInfo ci) {
        SaveWriteFault.begin(tag);
    }

    @Redirect(method = "method_10634", at = @At(value = "NEW", target = "java/util/zip/GZIPOutputStream"))
    private static GZIPOutputStream acceptance$wrap(OutputStream stream) throws IOException {
        return new GZIPOutputStream(SaveWriteFault.wrap(stream));
    }
}
