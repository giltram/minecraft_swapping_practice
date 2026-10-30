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

/** Opt-in real-screen automation, excluded from production. Uses only the explicitly owned disposable instance; programmatic input, not human acceptance. */
public final class UsabilityUiSmoke implements ClientModInitializer {
    private boolean opened, finished;
    private int ticks, step;
    private final long started = System.nanoTime();
    private volatile Throwable trainingFailure;
    private volatile boolean trainingDone;
    private TrainerClient trainer;
    private TrainerOptionsScreen options;
    private ProfileEditorSession editor;
    private Path source;
    private byte[] before;

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("hotbarswaptrainer.usabilityUi")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private byte[] saved;
    private java.util.concurrent.CompletableFuture<Void> languageReload;
    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() - started < 240_000_000_000L, "UI acceptance timeout");
            require(client.runDirectory.getName().equals("run-profile-editor-ui") || client.runDirectory.getName().startsWith("run-m2-ui-"), "isolated directory required");
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
            if (client.player == null || client.getOverlay() != null || ++ticks % 12 != 0) return;
            if (trainer == null) {
                Field instance = TrainerClient.class.getDeclaredField("instance"); instance.setAccessible(true);
                trainer = (TrainerClient) instance.get(null);
            }
            if (step == 0 && (!trainer.canEditProfiles() || ticks < 180)) return;
            if (Boolean.getBoolean("hotbarswaptrainer.usabilityReopen")) { reopenUsability(client); return; }
            runStep(client);
            step++;
        } catch (Exception failure) {
            failure.printStackTrace(); finish(client, "failed", "step " + step + ": " + failure);
        }
    }
    private void runStep(MinecraftClient client) throws Exception {
        switch (step) {
            case 0:
                source=client.runDirectory.toPath().resolve("config/hotbar-swap-trainer/profiles/usability.json");
                String fixture=new String(Files.readAllBytes(source.resolveSibling("default.json")),StandardCharsets.UTF_8)
                        .replace("\"id\": \"default\"","\"id\": \"editor_fixture\"").replace("Stationary basics","Usability fixture");
                Files.write(source,fixture.getBytes(StandardCharsets.UTF_8)); before=Files.readAllBytes(source);
                trainer.refreshProfiles();
                require(trainer.selectProfile("editor_fixture"),"fixture available");
                require(trainer.profile().equals("editor_fixture"),"selected fixture");
                client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client.options.guiScale=2; client.onResolutionChanged();
                options=new TrainerOptionsScreen(trainer); client.openScreen(options); break;
            case 1:
                require(TrainerClient.parseGoal(input(client,"Number of successful training tasks").getText())==10,"legacy goal maps directly");
                require(!TrainerOptionsScreen.showSessionMessage(trainer.session().get()),"idle Ready hidden");
                requireNoButton(client,"Fixed task count"); requireNoButton(client,"Unlimited practice");
                requireNoButton(client,"Profile:");
                screenshot(client,"m01-options-single-count");
                input(client,"Number of successful training tasks").setText("0"); require(trainer.goal()==0,"zero unlimited");
                input(client,"Number of successful training tasks").setText("73"); require(trainer.goal()==73,"positive count");
                input(client,"Number of successful training tasks").setText(""); focusInput(client,input(client,"Number of successful training tasks"));
                client.currentScreen.keyPressed(257,0,0); require(client.currentScreen==options,"blank cannot start");
                click(client,"Edit item layout"); break;
            case 2:
                require(client.currentScreen instanceof ProfileSourceScreen,"layout selection page");
                screenshot(client,"m00-layout-selection");
                click(client,"Stationary basics [default]");
                require(trainer.profile().equals("default"),"layout click selects training profile");
                require(client.currentScreen instanceof ProfileSourceScreen,"selection stays on list");
                click(client,"Usability fixture [editor_fixture]");
                require(trainer.profile().equals("editor_fixture"),"fixture reselected through list");
                require(new ClientOptionsStore(client.runDirectory.toPath().resolve(
                        "config/hotbar-swap-trainer/client-options.json")).load().profile.equals("editor_fixture"),"selection persisted");
                editLayout(client,"Usability fixture [editor_fixture]"); break;
            case 3:
                editor=session(client.currentScreen); require(!editor.dirty(),"opening lossless");
                requireNoButton(client,"Exchange"); requireNoButton(client,"Advanced");
                screenshot(client,"m02-main"); click(client,"Hotbar 1:");
                require(client.currentScreen instanceof ProfileItemPickerScreen,"slot directly opens catalogue");
                input(client,"Search item").setText("minecraft:iron_sword"); break;
            case 4:
                TextFieldWidget quantity=input(client,"Item quantity");
                ButtonWidget delete=requireButton(client,"Delete");
                require(quantity.x==((EditorFormScreen)client.currentScreen).viewport.left+2,"quantity input left aligned");
                require(delete.x==quantity.x+56&&delete.y==quantity.y,"Delete adjacent to quantity");
                click(client,"Delete");
                require(client.currentScreen instanceof ProfileItemPickerScreen,"toggle stays on same page");
                require(editor.draft().getSlots().get(0).getKind()==dev.hotbarswaptrainer.profile.SlotRule.Kind.EMPTY,"none selected becomes empty");
                require(Arrays.equals(before,Files.readAllBytes(source)),"selection does not write"); break;
            case 5:
                screenshot(client,"m03-none-selected"); click(client,"Done"); click(client,"Save layout");
                require(!editor.dirty()&&editor.expected().getSlots().get(0).getAlternatives().isEmpty(),"empty saved");
                click(client,"Hotbar 1:"); input(client,"Search item").setText("minecraft:stone"); break;
            case 6:
                click(client,"Stone (minecraft:stone)");
                require(input(client,"Item quantity").getText().equals("1"),"default one inline");
                input(client,"Item quantity").setText("32"); input(client,"Search item").setText("minecraft:dirt"); break;
            case 7:
                click(client,"Dirt (minecraft:dirt)"); input(client,"Item quantity").setText("7");
                require(editor.draft().getSlots().get(0).getAlternatives().size()==2,"multiple selections coexist");
                require(editor.selectedItems(0,"minecraft:stone").get(0).getCount()==32,"first quantity retained");
                input(client,"Search item").setText(""); break;
            case 8:
                screenshot(client,"m04-multiselect-all-items");
                requireButton(client,"Edit quantity: Stone"); requireButton(client,"Edit quantity: Dirt");
                input(client,"Item quantity").setText("");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);
                client.options.guiScale=4; client.onResolutionChanged(); break;
            case 9:
                require(input(client,"Item quantity").getText().isEmpty(),"raw quantity survives scale");
                screenshot(client,"m05-minimum-inline-quantity");
                require(input(client,"Item quantity").x==((EditorFormScreen)client.currentScreen).viewport.left+2,"left aligned after reflow");
                require(requireButton(client,"Delete").x+requireButton(client,"Delete").getWidth()<=client.currentScreen.width-16,"Delete fits minimum viewport");
                focusInput(client,input(client,"Item quantity")); client.currentScreen.keyPressed(257,0,0);
                require(client.currentScreen instanceof ProfileItemPickerScreen,"invalid quantity Enter retained");
                click(client,"Advanced"); click(client,"Back");
                require(input(client,"Item quantity").getText().isEmpty(),"raw count survives advanced return");
                click(client,"Edit quantity: Stone"); require(input(client,"Item quantity").getText().equals("32"),"per-item count32");
                click(client,"Edit quantity: Dirt"); require(input(client,"Item quantity").getText().isEmpty(),"each raw count retained");
                input(client,"Item quantity").setText("7"); click(client,"Done"); click(client,"Save layout");
                require(!editor.dirty(),"both items saved"); saved=Files.readAllBytes(source);
                click(client,"Cancel"); click(client,"Back"); break;
            case 10:
                require(client.currentScreen==options&&input(client,"Number of successful training tasks").getText().isEmpty(),"count draft survives editor");
                screenshot(client,"m06-invalid-training-count"); input(client,"Number of successful training tasks").setText("2147483648");
                require(!footerButton(client,"Start").active,"overflow cannot start");
                input(client,"Number of successful training tasks").setText("1");
                click(client,"Edit item layout"); editLayout(client,"Usability fixture [editor_fixture]");
                editor=session(client.currentScreen); click(client,"Hotbar 1:");
                click(client,"Edit quantity: Dirt"); input(client,"Item quantity").setText("9");
                click(client,"Done"); click(client,"Cancel"); break;
            case 11:
                require(client.currentScreen instanceof EditorConfirmScreen,"dirty exit confirmation"); click(client,"Confirm");
                require(Arrays.equals(saved,Files.readAllBytes(source)),"cancel keeps file bytes");
                editLayout(client,"Usability fixture [editor_fixture]"); editor=session(client.currentScreen); click(client,"Hotbar 5:"); break;
            case 12:
                require(editor.draft().getSlots().get(4).getAlternatives().size()==3,"existing pool loaded");
                screenshot(client,"m07-existing-custom-items");
                input(client,"Search item").setText("minecraft:potion"); break;
            case 13:
                clickItemId(client,"minecraft:potion"); require(client.currentScreen instanceof EditorConfirmScreen,"custom removal confirms");
                client.currentScreen.keyPressed(256,0,0);
                require(editor.draft().getSlots().get(4).getAlternatives().size()==3,"declined custom removal lossless");
                click(client,"Advanced"); click(client,"Random item or empty"); click(client,"Confirm");
                click(client,"Edit item / quantity"); click(client,"NBT and matching"); break;
            case 14:
                input(client,"Generation compound").setText("{unfinished:"); client.currentScreen.resize(client,320,240);
                require(input(client,"Generation compound").getText().equals("{unfinished:"),"raw SNBT resize");
                click(client,"Back"); click(client,"NBT and matching");
                require(input(client,"Generation compound").getText().equals("{unfinished:"),"raw SNBT child return");
                input(client,"Generation compound").setText("{Usability:1b}");
                click(client,"Back"); click(client,"Back"); click(client,"Back"); click(client,"Done");
                click(client,"Save layout"); require(!editor.dirty(),"complex pool and custom NBT saved"); saved=Files.readAllBytes(source); break;
            case 15:
                input(client,"Profile name").setText("conflict draft");
                Files.write(source,(new String(saved,StandardCharsets.UTF_8)+" \n").getBytes(StandardCharsets.UTF_8));
                click(client,"Save layout"); require(editor.reloadRequired()&&editor.dirty(),"conflict retains draft"); break;
            case 16:
                screenshot(client,"m08-conflict"); click(client,"Reload file"); click(client,"Confirm");
                require(!editor.dirty(),"explicit reload"); input(client,"Profile name").setText("write denial draft");
                java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions=Files.getPosixFilePermissions(source.getParent());
                try {
                    Files.setPosixFilePermissions(source.getParent(),java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
                    click(client,"Save layout");
                } finally { Files.setPosixFilePermissions(source.getParent(),permissions); }
                require(editor.reloadRequired()&&editor.dirty(),"write denial keeps draft"); break;
            case 17:
                screenshot(client,"m09-save-denied"); click(client,"Reload file"); click(client,"Confirm");
                click(client,"Cancel"); click(client,"Back");
                require(input(client,"Number of successful training tasks").getText().equals("1"),"training count retained");
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.capture(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()))); break;
            case 18:
                require(trainingDone&&trainingFailure==null,"finite baseline"); trainingDone=false; click(client,"Start"); break;
            case 19:
                require(!trainer.canEditProfiles()&&!editor.canSave(),"active gate unchanged");
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.complete(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()),1)); break;
            case 20:
                require(trainingDone&&trainingFailure==null&&trainer.canEditProfiles(),"finite solved/restored: "+trainingFailure);
                if (Boolean.getBoolean("hotbarswaptrainer.pbHudUi")) {
                    String message = trainer.session().get().message;
                    require(message.contains("Avg ") && message.contains("Best ") && !message.contains("Session complete")
                            && !message.contains("successes"), "compact completed summary");
                    screenshot(client,"p01-completed-summary");
                }
                options=new TrainerOptionsScreen(trainer); client.openScreen(options); input(client,"Number of successful training tasks").setText("0"); break;
            case 21:
                screenshot(client,"m10-training-count-zero");
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.capture(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()))); break;
            case 22:
                require(trainingDone&&trainingFailure==null,"unlimited baseline"); trainingDone=false; click(client,"Start"); break;
            case 23:
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.complete(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()),0)); break;
            case 24:
                require(trainingDone&&trainingFailure==null&&trainer.canEditProfiles(),"unlimited solved/restored: "+trainingFailure);
                options=new TrainerOptionsScreen(trainer); client.openScreen(options); break;
            case 25:
                screenshot(client,"m11-options-no-ready");
                click(client,"Edit item layout"); editLayout(client,"Usability fixture [editor_fixture]");
                editor=session(client.currentScreen); break;
            case 26:
                require(client.currentScreen.width==320&&client.currentScreen.height==240,"native minimum logical320x240");
                requireNoButton(client,"Exchange"); screenshot(client,"m12-compact-main");
                click(client,"Hotbar 1:"); break;
            case 27:
                screenshot(client,"m13-compact-multiselect");
                requireButton(client,"Edit quantity: Stone"); requireButton(client,"Edit quantity: Dirt");
                click(client,"Done"); click(client,"Cancel"); click(client,"Back");
                if (Boolean.getBoolean("hotbarswaptrainer.pbHudUi")) { click(client,"PB"); break; }
                finish(client,"passed","28 production-client steps: sole layout selector switches/persists training profile, separate Edit opens the exact source, no options Profile control; direct same-page multi-selection and marks, compact headings, left-aligned labelled quantities and Delete, all selected icons/counts, zero selections→EMPTY saved, multiple quantities, no exchange/required distinction/fixed-unlimited buttons, raw inline quantities and SNBT across resize/children, cancel unchanged file, custom removal confirmation, retained advanced pools, conflict/write-denial, 0/positive/invalid training count, hidden idle Ready, finite/unlimited real training and two-save restoration, actual logical320x240. Programmatic input only."); break;
            case 28:
                require(client.currentScreen instanceof TrainerHistoryScreen, "simplified PB entry");
                require(client.currentScreen.children().size()==1,"only Back, no refresh, filters or details");
                HistoryBrowser browser=trainer.historyBrowser(); require(browser.refresh(),"PB snapshot");
                require(dev.hotbarswaptrainer.stats.PersonalBests.fastestTaskForMode(browser.snapshot,
                        dev.hotbarswaptrainer.task.TrainingMode.SINGLE_ITEM).isPresent(),"real Single PB");
                screenshot(client,"p02-pb-minimum"); click(client,"Back");
                require(!trainer.showSwapCounts(),"swap counts default off"); click(client,"Show swap counts: OFF");
                require(trainer.showSwapCounts(),"swap counts enabled");
                if(trainer.mode()!=dev.hotbarswaptrainer.task.TrainingMode.FULL_INVENTORY) trainer.cycleMode();
                input(client,"Number of successful training tasks").setText("1");
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.capture(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()))); break;
            case 29:
                require(trainingDone&&trainingFailure==null,"Full baseline");
                screenshot(client,"p03-swap-count-option"); click(client,"Start"); break;
            case 30:
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.readyToSolve(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()))); break;
            case 31:
                require(trainingDone&&trainingFailure==null,"Full RUNNING: "+trainingFailure);
                require(trainer.session().get().phase.equals("RUNNING"),"running projection");
                screenshot(client,"p04-hud-swaps-on"); trainer.toggleSwapCounts(); break;
            case 32:
                screenshot(client,"p05-hud-swaps-off");
                server(client,()->dev.hotbarswaptrainer.smoke.UsabilityTrainingAcceptance.complete(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()),1)); break;
            case 33:
                require(trainingDone&&trainingFailure==null&&trainer.canEditProfiles(),"Full completed/restored: "+trainingFailure);
                screenshot(client,"p06-full-completed");
                client.openScreen(new TrainerHistoryScreen(new TrainerOptionsScreen(trainer),trainer.historyBrowser())); break;
            case 34:
                screenshot(client,"p07-both-mode-pbs");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1000,700);
                client.options.guiScale=2; client.onResolutionChanged(); break;
            case 35:
                screenshot(client,"p08-pb-normal-size");
                finish(client,"passed","36 production stages: existing usability suite plus compact Avg/Best summary, PB table with only Back at minimum and normal sizes, real Single/Full successful records, default-hidden swap count option and RUNNING HUD with counts on/off; both saved inventories restored. Programmatic input only."); break;
        }
    }
    private static void clickItemId(MinecraftClient client, String id) {
        for(Element child:client.currentScreen.children()) if(child instanceof ButtonWidget) {
            String label=((ButtonWidget)child).getMessage().getString();
            if(label.contains("("+id+")")) { click(client,label); return; }
        }
        throw new IllegalStateException("Missing item ID: "+id);
    }
    private static ButtonWidget requireButton(MinecraftClient client, String prefix) {
        for(Element child:client.currentScreen.children()) if(child instanceof ButtonWidget&&((ButtonWidget)child).getMessage().getString().startsWith(prefix)) return (ButtonWidget)child;
        throw new IllegalStateException("Missing button: "+prefix);
    }
    private static void requireNoButton(MinecraftClient client, String prefix) {
        for(Element child:client.currentScreen.children()) if(child instanceof ButtonWidget)
            require(!((ButtonWidget)child).getMessage().getString().startsWith(prefix),"Removed control: "+prefix);
    }
    private static ButtonWidget footerButton(MinecraftClient client, String label) {
        return (ButtonWidget)client.currentScreen.children().stream().filter(child -> child instanceof ButtonWidget
                && ((ButtonWidget)child).getMessage().getString().equals(label)).findFirst().get();
    }
    private interface Checked { void run() throws Exception; }
    private void server(MinecraftClient client, Checked work) {
        trainingDone=false; trainingFailure=null;
        client.getServer().execute(() -> { try { work.run(); } catch(Throwable e) {trainingFailure=e; e.printStackTrace();} finally {trainingDone=true;} });
    }
    private void reopenUsability(MinecraftClient client) throws Exception {
        if (step==0) {
            require(trainer.profile().equals("editor_fixture")&&trainer.goal()==0,"restart options");
            source=client.runDirectory.toPath().resolve("config/hotbar-swap-trainer/profiles/usability.json");
            dev.hotbarswaptrainer.profile.LoadoutProfile profile=trainer.profileRepository().load(source);
            require(profile.getSlots().get(0).getAlternatives().size()==2&&profile.getSlots().get(0).getAlternatives().get(0).getTemplate().getCount()==32
                    &&profile.getSlots().get(0).getAlternatives().get(1).getTemplate().getCount()==7,"restart both allowed items/quantities");
            require(profile.getSlots().get(4).getAlternatives().size()==3&&profile.getSlots().get(4).getKind()==dev.hotbarswaptrainer.profile.SlotRule.Kind.OPTIONAL,"restart complex profile");
            require(profile.getSlots().get(4).getAlternatives().get(0).getTemplate().getNbt().getByte("Usability")==1,"restart custom NBT");
            HistoryBrowser browser=trainer.historyBrowser(); require(browser.refresh()&&browser.snapshot.records.size()==2,"restart finite/unlimited history");
            client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            client.openScreen(new TrainerOptionsScreen(trainer)); step=1; return;
        }
        if(step==1) {
            screenshot(client,"u17-restart-options");
            client.getLanguageManager().setLanguage(client.getLanguageManager().getLanguage("zh_cn"));
            client.options.language="zh_cn"; languageReload=client.reloadResources(); step=2; return;
        }
        if(step==2) {
            if(!languageReload.isDone()) return;
            languageReload.join(); require(EditorText.text("name").equals("配置名称"),"Chinese resources loaded");
            client.openScreen(new TrainerOptionsScreen(trainer)); step=3; return;
        }
        if(step==3) {
            screenshot(client,"u20-chinese-options"); click(client,EditorText.text("layout")); editLayout(client,"Usability fixture [editor_fixture]"); step=4; return;
        }
        if(step==4) {
            screenshot(client,"u21-chinese-main"); click(client,EditorText.slot(0)+":"); step=5; return;
        }
        if(step==5) { screenshot(client,"u22-chinese-picker"); client.currentScreen.keyPressed(256,0,0); step=6; return; }
        finish(client,"passed","Fresh production process: selected profile/unlimited, stone32+dirt7 and three-item OPTIONAL/custom NBT profile and both histories survive restart. Actual en_us→zh_cn resource reload and Chinese options/main/picker rendered. Programmatic input, human experience acceptance remains pending.");
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
    private static void editLayout(MinecraftClient client, String name) {
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
            Files.write(client.runDirectory.toPath().resolve("usability-ui-result.txt"),
                    Arrays.asList(status, detail), StandardCharsets.UTF_8);
        } catch (Exception failure) { failure.printStackTrace(); }
        client.scheduleStop();
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }
}
