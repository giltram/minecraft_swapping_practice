package dev.hotbarswaptrainer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import java.lang.reflect.Field;

/** Shared navigation through actual screen events in the optional UI acceptance mod. */
final class UiSmokeControls {
    static ButtonWidget requireButton(MinecraftClient client, String prefix) {
        for(Element child:client.currentScreen.children()) if(child instanceof ButtonWidget&&((ButtonWidget)child).getMessage().getString().startsWith(prefix)) return (ButtonWidget)child;
        throw new IllegalStateException("Missing button: "+prefix);
    }
    static ProfileEditorSession session(Screen screen) throws Exception {
        Field field = ProfileEditorScreen.class.getDeclaredField("editor"); field.setAccessible(true);
        return (ProfileEditorSession) field.get(screen);
    }
    static TextFieldWidget input(MinecraftClient client, String prefix) {
        for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget
                && ((TextFieldWidget) child).getMessage().getString().startsWith(prefix)) return (TextFieldWidget) child;
        throw new IllegalStateException("Missing input: " + prefix);
    }
    static void editLayout(MinecraftClient client, String name) {
        ButtonWidget selected = requireButton(client, name);
        for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (button.y == selected.y && button.x > selected.x) {
                EditorFormScreen screen = (EditorFormScreen) client.currentScreen;
                int offset = button.y - screen.viewport.top + screen.viewport.scroll();
                screen.viewport.reveal(offset, 20);
                require(screen.mouseClicked(button.x + button.getWidth() / 2.0, screen.viewport.y(offset) + 10, 0), "edit selected layout");
                return;
            }
        }
        throw new IllegalStateException("Missing Edit for " + name);
    }
    static void click(MinecraftClient client, String prefix) {
        Screen screen = client.currentScreen;
        for (Element child : screen.children()) if (child instanceof ButtonWidget) {
            ButtonWidget button = (ButtonWidget) child;
            if (!button.getMessage().getString().startsWith(prefix)) continue;
            require(button.active, "active button " + prefix);
            double y = button.y + 10;
            if (screen instanceof EditorFormScreen && !isFooter((EditorFormScreen) screen, button)) {
                EditorViewport viewport = ((EditorFormScreen) screen).viewport;
                int offset = button.y - viewport.top + viewport.scroll();
                viewport.reveal(offset, 20); y = viewport.y(offset) + 10;
            }
            require(screen.mouseClicked(button.x + button.getWidth() / 2.0, y, 0), "pointer dispatch " + prefix);
            return;
        }
        throw new IllegalStateException("Missing button: " + prefix + " on " + screen.getClass());
    }
    static boolean isFooter(EditorFormScreen screen, ButtonWidget button) {
        try {
            Field field = EditorFormScreen.class.getDeclaredField("footer"); field.setAccessible(true);
            return ((java.util.List<?>) field.get(screen)).contains(button);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    static void focusInput(MinecraftClient client, TextFieldWidget input) {
        EditorFormScreen screen = (EditorFormScreen) client.currentScreen;
        int offset = input.y - screen.viewport.top + screen.viewport.scroll();
        screen.viewport.reveal(offset, 20);
        require(screen.mouseClicked(input.x + 8, screen.viewport.y(offset) + 10, 0), "text focus click");
    }
    static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name + ".png", client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> { });
    }
    static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }
    private UiSmokeControls() { }
}
