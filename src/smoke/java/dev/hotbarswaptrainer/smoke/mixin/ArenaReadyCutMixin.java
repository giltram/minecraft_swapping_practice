package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.arena.ArenaJournal;
import dev.hotbarswaptrainer.smoke.ArenaReadyCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ArenaJournal.class, remap = false)
public abstract class ArenaReadyCutMixin {
    @Inject(method = "transition", at = @At("RETURN"))
    private void acceptance$ready(ArenaJournal.Record previous, ArenaJournal.Phase phase,
                                  CallbackInfoReturnable<ArenaJournal.Record> ci) throws Exception {
        ArenaReadyCut.observe(ci.getReturnValue());
    }
}
