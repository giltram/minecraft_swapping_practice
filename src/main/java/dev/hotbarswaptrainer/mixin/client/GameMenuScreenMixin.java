package dev.hotbarswaptrainer.mixin.client;

import dev.hotbarswaptrainer.client.TrainerClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route the ordinary pause-menu exit through confirmed restoration before vanilla disconnects. */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) { super(title); }
    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void trainer$safePracticeExit(CallbackInfo ci) {
        if (!TrainerClient.inPracticeArena()) return;
        AbstractButtonWidget exit = null;
        for (AbstractButtonWidget button : buttons) {
            Text message = button.getMessage();
            if (message instanceof TranslatableText && "menu.returnToMenu".equals(((TranslatableText) message).getKey())) exit = button;
        }
        if (exit == null) throw new IllegalStateException("Minecraft 1.16.1 pause-menu exit was not found");
        buttons.remove(exit); children.remove(exit);
        addButton(new ButtonWidget(exit.x, exit.y, exit.getWidth(), exit.getHeight(),
                new TranslatableText("trainer.arena.exit"), ignored -> TrainerClient.exitPracticeArena()));
    }
}
