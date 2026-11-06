package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.arena.ArenaStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.TranslatableText;

/** Excluded fixture: exercises the actual title widget and package-private client coordinator. */
public final class ArenaUiSmokeAccess {
    private ArenaUiSmokeAccess() { }
    private static TrainerClient trainer() throws Exception {
        java.lang.reflect.Field field = TrainerClient.class.getDeclaredField("instance");
        field.setAccessible(true); return (TrainerClient) field.get(null);
    }
    public static ArenaStatus status() throws Exception { return trainer().arena().status(); }
    public static void enterViaTitleButton() {
        for (Element element : MinecraftClient.getInstance().currentScreen.children()) {
            if (!(element instanceof ButtonWidget)) continue;
            ButtonWidget button = (ButtonWidget) element;
            if (button.getMessage() instanceof TranslatableText && "trainer.arena.enter".equals(((TranslatableText) button.getMessage()).getKey())) {
                if (!button.active) throw new IllegalStateException("Arena entry is disabled");
                button.onPress(); return;
            }
        }
        throw new IllegalStateException("The title arena entry is missing");
    }
    public static void reset() throws Exception { trainer().arena().reset(); }
    public static void retry() throws Exception { trainer().arena().retry(); }
    public static void exit() { TrainerClient.exitPracticeArena(); }
    public static void exitViaPauseMenu() {
        net.minecraft.client.gui.screen.GameMenuScreen menu = new net.minecraft.client.gui.screen.GameMenuScreen(true);
        MinecraftClient.getInstance().openScreen(menu);
        for (Element element : menu.children()) {
            if (!(element instanceof ButtonWidget)) continue;
            ButtonWidget button = (ButtonWidget) element;
            if (button.getMessage() instanceof TranslatableText && "trainer.arena.exit".equals(((TranslatableText) button.getMessage()).getKey())) {
                button.onPress(); return;
            }
        }
        throw new IllegalStateException("The managed pause-menu exit adapter is missing");
    }
    public static void request(dev.hotbarswaptrainer.network.ArenaPackets.Action action) throws Exception { trainer().arena().request(action); }
    public static void convert(boolean parkour) throws Exception {
        if (trainer().arena().prepareLayout(parkour, () -> { })) return;
        if (!(MinecraftClient.getInstance().currentScreen instanceof EditorConfirmScreen)) throw new IllegalStateException("Missing conversion confirmation");
        for (Element element : MinecraftClient.getInstance().currentScreen.children()) {
            if (element instanceof ButtonWidget && ((ButtonWidget) element).getMessage().getString().equals(EditorText.text("confirm"))) {
                ((ButtonWidget) element).onPress(); return;
            }
        }
        throw new IllegalStateException("Missing conversion confirmation button");
    }
}
