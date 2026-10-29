package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.TranslatableText;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Opt-in real-screen automation, excluded from production. Never starts training or changes inventory. */
public final class ProfileEditorUiSmoke implements ClientModInitializer {
    private boolean opened, finished;
    private int ticks, step;
    private final long started = System.nanoTime();
    private TrainerClient trainer;
    private TrainerOptionsScreen options;
    private ProfileEditorSession editor;
    private Path source;
    private byte[] before;
    private ProfileDraft.Candidate candidate;

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("hotbarswaptrainer.editorSmoke")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() - started < 180_000_000_000L, "UI acceptance timeout");
            require(client.runDirectory.getName().equals("run-profile-editor-ui"), "isolated directory required");
            if (!opened && client.currentScreen instanceof TitleScreen) {
                Path marker = client.runDirectory.toPath().resolve("saves/hst-smoke/.hst-smoke-owned");
                require(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim()
                        .equals("Hotbar Swap Trainer automated disposable smoke world v1"), "world ownership");
                opened = true; client.options.pauseOnLostFocus = false; client.options.viewDistance = 2;
                client.startIntegratedServer("hst-smoke"); return;
            }
            if (opened && client.currentScreen instanceof BackupPromptScreen) {
                require(client.currentScreen.getTitle() instanceof TranslatableText
                        && ((TranslatableText) client.currentScreen.getTitle()).getKey().equals("selectWorld.backupQuestion.experimental"), "owned warning");
                for (Element child : client.currentScreen.children()) if (child instanceof ButtonWidget
                        && ((ButtonWidget) child).getMessage() instanceof TranslatableText
                        && ((TranslatableText) ((ButtonWidget) child).getMessage()).getKey().equals("selectWorld.backupJoinSkipButton")) {
                    ((ButtonWidget) child).onPress(); return;
                }
            }
            if (client.player == null || ++ticks % 12 != 0) return;
            if (trainer == null) {
                Field instance = TrainerClient.class.getDeclaredField("instance"); instance.setAccessible(true);
                trainer = (TrainerClient) instance.get(null);
            }
            if (step == 0 && !trainer.canEditProfiles()) return;
            runStep(client);
            step++;
        } catch (Exception failure) {
            failure.printStackTrace(); finish(client, "failed", "step " + step + ": " + failure);
        }
    }
    private void runStep(MinecraftClient client) throws Exception {
        switch (step) {
            case 0:
                source = client.runDirectory.toPath().resolve("config/hotbar-swap-trainer/profiles/explicit-editor-source.json");
                String fixture = new String(Files.readAllBytes(source.resolveSibling("default.json")), StandardCharsets.UTF_8)
                        .replace("\"id\": \"default\"", "\"id\": \"editor_fixture\"");
                Files.write(source, fixture.getBytes(StandardCharsets.UTF_8)); before = Files.readAllBytes(source);
                options = new TrainerOptionsScreen(trainer); client.openScreen(options);
                input(client, "Successful tasks").setText(""); break;
            case 1: screenshot(client, "01-options"); click(client, "Edit profile"); break;
            case 2: click(client, "Stationary basics [editor_fixture]"); break;
            case 3:
                editor = session(client.currentScreen);
                require(editor.source.equals(source), "exact selected source");
                require(!editor.dirty(), "opening is read-only");
                input(client, "Display name").setText("unsaved UI draft");
                for (int i = 0; i < 120; i++) editor.draft().getSlots().get(9).getAlternatives()
                        .add(new ProfileDraft.Candidate("minecraft:stone", 1));
                click(client, "offhand:"); break;
            case 4:
                click(client, "Remove candidate 121");
                require(client.currentScreen instanceof EditorConfirmScreen, "long-list last candidate reachable");
                client.currentScreen.keyPressed(256, 0, 0);
                require(editor.draft().getSlots().get(9).getAlternatives().size() == 121, "Esc cancels removal");
                click(client, "Add candidate"); break;
            case 5: input(client, "Search item").setText("minecraft:oak_planks"); break;
            case 6: screenshot(client, "02-picker"); click(client, "Oak Planks (minecraft:oak_planks)"); break;
            case 7:
                candidate = editor.draft().getSlots().get(9).getAlternatives().get(121);
                TextFieldWidget quantity = input(client, "Quantity");
                quantity.setText(""); focusInput(client, quantity);
                require(client.currentScreen.charTyped('9', 0) && quantity.getText().equals("9"), "focused text input");
                quantity.setText("");
                client.currentScreen.keyPressed(258, 0, 0);
                require(client.currentScreen.getFocused() instanceof ButtonWidget
                        && ((ButtonWidget) client.currentScreen.getFocused()).getMessage().getString().startsWith("Advanced"), "Tab moves to next action");
                client.currentScreen.keyPressed(257, 0, 0);
                require(client.currentScreen instanceof ProfileAdvancedScreen, "Enter activates focused child"); break;
            case 8:
                input(client, "Generation compound").setText("{Draft:");
                client.currentScreen.resize(client, 320, 240);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                require(input(client, "Generation compound").getText().equals("{Draft:"), "small-window raw SNBT");
                require(candidate.getMatch() == null && candidate.getMatchNbt() == null, "implicit match preserved");
                break;
            case 9:
                screenshot(client, "03-small-advanced");
                client.options.guiScale = 1; client.onResolutionChanged();
                require(input(client, "Generation compound").getText().equals("{Draft:"), "GUI scale raw SNBT");
                click(client, "Back (keep"); break;
            case 10:
                require(input(client, "Quantity").getText().isEmpty(), "child return raw quantity");
                click(client, "Back to slot"); break;
            case 11:
                click(client, "Use EMPTY"); break;
            case 12:
                require(client.currentScreen instanceof EditorConfirmScreen, "destructive rule confirmation");
                click(client, "Keep editing");
                require(editor.draft().getSlots().get(9).getAlternatives().size() == 122, "rule cancel retains candidates"); break;
            case 13: click(client, "Back to profile"); break;
            case 14:
                click(client, "Save profile");
                require(editor.message().contains("quantity"), "readable raw quantity error");
                require(Arrays.equals(before, Files.readAllBytes(source)), "invalid save writes nothing"); break;
            case 15: screenshot(client, "04-validation-error"); click(client, "Cancel"); break;
            case 16:
                require(client.currentScreen instanceof EditorConfirmScreen, "dirty exit prompts");
                screenshot(client, "05-unsaved-exit"); click(client, "Keep editing"); break;
            case 17:
                click(client, "Cancel"); click(client, "Confirm");
                require(Arrays.equals(before, Files.readAllBytes(source)), "confirmed cancel writes nothing");
                click(client, "Stationary basics [editor_fixture]"); editor = session(client.currentScreen);
                require(!editor.dirty(), "cancel reopens clean source");
                input(client, "Display name").setText("reset this draft"); click(client, "Reset draft"); break;
            case 18: click(client, "Confirm"); require(!editor.dirty(), "reset baseline"); break;
            case 19:
                input(client, "Display name").setText("M2-C UI saved"); click(client, "Save profile");
                require(!editor.dirty() && editor.expected().getName().equals("M2-C UI saved"), "save reloads handle"); break;
            case 20: screenshot(client, "06-saved"); click(client, "Cancel"); break;
            case 21: click(client, "M2-C UI saved [editor_fixture]"); break;
            case 22:
                editor = session(client.currentScreen);
                require(input(client, "Display name").getText().equals("M2-C UI saved"), "fresh reopen");
                Files.write(source, (new String(Files.readAllBytes(source), StandardCharsets.UTF_8) + " \n").getBytes(StandardCharsets.UTF_8));
                input(client, "Display name").setText("retained conflict draft"); click(client, "Save profile");
                require(editor.reloadRequired() && editor.draft().getName().equals("retained conflict draft"), "stale save preserves draft"); break;
            case 23: screenshot(client, "07-stale-error"); click(client, "Reload this source"); break;
            case 24: click(client, "Confirm"); require(!editor.reloadRequired() && !editor.dirty(), "explicit reload"); break;
            case 25: click(client, "Cancel"); break;
            case 26: click(client, "Back"); break;
            case 27:
                require(client.currentScreen == options && input(client, "Successful tasks").getText().isEmpty(), "M2-A unfinished goal survives editor");
                click(client, "Slot order"); break;
            case 28:
                require(client.currentScreen instanceof TrainerLayoutScreen, "existing slot editor reachable");
                screenshot(client, "08-slot-order"); client.currentScreen.onClose(); break;
            case 29: finish(client, "passed", "30 real-client steps: entry/source binding, offhand/122-candidate scroll, registry picker, focused text/Tab/Enter/Esc, raw quantity/SNBT, actual window resize/GUI scale, rule/exit confirmations, confirmed cancel/reopen, reset, save/reopen, conflict/reload, M2-A draft and slot-order entry. Programmatic input; physical/manual acceptance remains separate."); break;
            default: throw new IllegalStateException("Unexpected step");
        }
    }
    private static ProfileEditorSession session(Screen screen) throws Exception {
        Field field = ProfileEditorScreen.class.getDeclaredField("editor"); field.setAccessible(true);
        return (ProfileEditorSession) field.get(screen);
    }
    private static TextFieldWidget input(MinecraftClient client, String prefix) {
        for (Element child : client.currentScreen.children()) if (child instanceof TextFieldWidget
                && ((TextFieldWidget) child).getMessage().getString().startsWith(prefix)) return (TextFieldWidget) child;
        throw new IllegalStateException("Missing input: " + prefix);
    }
    private static void click(MinecraftClient client, String prefix) {
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
    private static boolean isFooter(EditorFormScreen screen, ButtonWidget button) {
        try {
            Field field = EditorFormScreen.class.getDeclaredField("footer"); field.setAccessible(true);
            return ((java.util.List<?>) field.get(screen)).contains(button);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void focusInput(MinecraftClient client, TextFieldWidget input) {
        EditorFormScreen screen = (EditorFormScreen) client.currentScreen;
        int offset = input.y - screen.viewport.top + screen.viewport.scroll();
        screen.viewport.reveal(offset, 20);
        require(screen.mouseClicked(input.x + 8, screen.viewport.y(offset) + 10, 0), "text focus click");
    }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name + ".png", client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), ignored -> { });
    }
    private void finish(MinecraftClient client, String status, String detail) {
        finished = true;
        try {
            Files.write(client.runDirectory.toPath().resolve("editor-ui-result.txt"),
                    Arrays.asList(status, detail), StandardCharsets.UTF_8);
        } catch (Exception failure) { failure.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }
}
