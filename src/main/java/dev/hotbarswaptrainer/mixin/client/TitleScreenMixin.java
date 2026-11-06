package dev.hotbarswaptrainer.mixin.client;

import dev.hotbarswaptrainer.client.TrainerClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds exactly one title action; vanilla retains world creation/loading and its progress rendering. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void trainer$practiceEntry(CallbackInfo ci) {
        // Split the added row's height above/below vanilla's layout, including its small-window case.
        for (AbstractButtonWidget button : buttons) button.y += 12;
        ButtonWidget entry = addButton(new ButtonWidget(width / 2 - 100, height / 4 + 36, 200, 20,
                new TranslatableText("trainer.arena.enter"), ignored -> TrainerClient.enterPracticeArena()));
        entry.active = !client.isDemo();
    }
}
