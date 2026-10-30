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
    private volatile Throwable trainingFailure;
    private volatile boolean trainingDone;
    private TrainerClient trainer;
    private TrainerOptionsScreen options;
    private ProfileEditorSession editor;
    private Path source;
    private byte[] before, gateSource;
    private Screen gateEditor;
    private Path duplicateSource, corruptOptions;
    private TrainerClient faultOptions;
    private ProfileDraft.Candidate candidate;

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("hotbarswaptrainer.editorSmoke")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if (finished) return;
        try {
            require(System.nanoTime() - started < 180_000_000_000L, "UI acceptance timeout");
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
            if (client.player == null || ++ticks % 12 != 0) return;
            if (trainer == null) {
                Field instance = TrainerClient.class.getDeclaredField("instance"); instance.setAccessible(true);
                trainer = (TrainerClient) instance.get(null);
            }
            if (step == 0 && !trainer.canEditProfiles()) return;
            if (Boolean.getBoolean("hotbarswaptrainer.historyReopen")) { reopen(client); return; }
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
            case 29:
                if (Boolean.getBoolean("hotbarswaptrainer.historyUi")) { historyFixture(client); click(client,"History / PB"); break; }
                finish(client, "passed", "30 real-client steps: entry/source binding, offhand/122-candidate scroll, registry picker, focused text/Tab/Enter/Esc, raw quantity/SNBT, actual window resize/GUI scale, rule/exit confirmations, confirmed cancel/reopen, reset, save/reopen, conflict/reload, M2-A draft and slot-order entry. Programmatic input; physical/manual acceptance remains separate."); break;
            case 30:
                require(client.currentScreen instanceof TrainerHistoryScreen,"history entry");
                screenshot(client,"09-history"); click(client,"Next page"); break;
            case 31:
                screenshot(client,"10-history-page2"); click(client,"Details / PB"); break;
            case 32:
                screenshot(client,"11-history-details");
                client.currentScreen.resize(client,320,240); client.currentScreen.mouseScrolled(160,100,-30);
                break;
            case 33:
                screenshot(client,"12-small-history-details"); click(client,"Back");
                require(historyBrowser(client.currentScreen).page==1,"detail return retains page");
                click(client,"Target hints: All");
                require(Boolean.TRUE.equals(historyBrowser(client.currentScreen).filter.hints),"hints filter");
                click(client,"Profile hash:"); break;
            case 34:
                screenshot(client,"13-history-filter-warning"); click(client,"Refresh");
                require(!historyBrowser(client.currentScreen).snapshot.errors.isEmpty(),"corrupt warning retained");
                click(client,"Back");
                trainer.setGoal("73");
                if(trainer.mode()!=dev.hotbarswaptrainer.task.TrainingMode.FULL_INVENTORY) trainer.cycleMode();
                if(!trainer.showHints()) trainer.toggleHints();
                break;
            case 35:
                click(client,"Edit profile"); click(client,"M2-C UI saved [editor_fixture]"); break;
            case 36: editor=session(client.currentScreen); click(client,"hotbar4:"); break;
            case 37: click(client,"Use OPTIONAL"); click(client,"Edit item / quantity"); break;
            case 38: input(client,"Quantity").setText("32"); click(client,"Choose item"); break;
            case 39: input(client,"Search item").setText("minecraft:dirt"); break;
            case 40: click(client,"Dirt (minecraft:dirt)"); break;
            case 41: click(client,"Advanced NBT"); break;
            case 42: input(client,"Generation compound").setText("{M2:1b}"); click(client,"Back (keep"); break;
            case 43: click(client,"Back to slot"); break;
            case 44: click(client,"Back to profile"); break;
            case 45:
                click(client,"Save profile"); require(!editor.dirty(),"valid rule/item/count/NBT saved"); break;
            case 46:
                screenshot(client,"17-valid-rule-item-count-nbt-saved"); click(client,"Cancel"); click(client,"Back"); break;
            case 47:
                java.util.UUID startPlayer=client.player.getUuid();
                client.getServer().execute(() -> {
                    try { dev.hotbarswaptrainer.smoke.ProfileTrainingAcceptance.start(client.getServer().getPlayerManager().getPlayer(startPlayer)); }
                    catch(Throwable e) { trainingFailure=e; }
                }); break;
            case 48:
                require(trainingFailure==null,"saved-profile start: "+trainingFailure);
                require(!trainer.canEditProfiles(),"actual PREPARING blocks editor/history entry");
                screenshot(client,"15-active-entry-blocked");
                java.util.UUID finishPlayer=client.player.getUuid();
                client.getServer().execute(() -> {
                    try { dev.hotbarswaptrainer.smoke.ProfileTrainingAcceptance.complete(client.getServer().getPlayerManager().getPlayer(finishPlayer)); }
                    catch(Throwable e) { trainingFailure=e; } finally { trainingDone=true; }
                }); break;
            case 49:
                require(trainingDone&&trainingFailure==null,"saved-profile training/restore: "+trainingFailure);
                require(trainer.canEditProfiles(),"post-training restored entry");
                click(client,"History / PB"); break;
            case 50:
                require(historyBrowser(client.currentScreen).snapshot.records.size()==24,"real saved-profile history plus fixtures");
                screenshot(client,"16-trained-profile-history");
                click(client,"Back"); click(client,"Edit profile"); click(client,"M2-C UI saved [editor_fixture]");
                editor=session(client.currentScreen); gateSource=Files.readAllBytes(source);
                input(client,"Display name").setText("blocked gate draft"); break;
            case 51:
                java.util.Set<java.nio.file.attribute.PosixFilePermission> sourcePermissions=Files.getPosixFilePermissions(source.getParent());
                java.util.Set<java.nio.file.attribute.PosixFilePermission> deniedPermissions=java.util.EnumSet.copyOf(sourcePermissions);
                deniedPermissions.remove(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                deniedPermissions.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
                deniedPermissions.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
                try { Files.setPosixFilePermissions(source.getParent(),deniedPermissions); click(client,"Save profile"); }
                finally { Files.setPosixFilePermissions(source.getParent(),sourcePermissions); }
                require(editor.reloadRequired()&&editor.dirty()&&Arrays.equals(gateSource,Files.readAllBytes(source)),"real profile write denial preserves draft/source"); break;
            case 52:
                screenshot(client,"21-profile-write-denied"); click(client,"Reload this source"); click(client,"Confirm");
                require(!editor.reloadRequired()&&!editor.dirty(),"write denial repair/reload"); break;
            case 53:
                duplicateSource=source.resolveSibling("m2-duplicate-owned.json"); require(!Files.exists(duplicateSource),"new duplicate fixture only");
                Files.write(duplicateSource,Files.readAllBytes(source)); input(client,"Display name").setText("duplicate retained draft");
                click(client,"Save profile"); require(editor.reloadRequired()&&editor.dirty()&&Arrays.equals(gateSource,Files.readAllBytes(source)),"duplicate preserves draft/source"); break;
            case 54:
                screenshot(client,"22-profile-duplicate");
                Files.move(duplicateSource,duplicateSource.resolveSibling("m2-duplicate-owned.evidence"));
                click(client,"Reload this source"); click(client,"Confirm"); input(client,"Display name").setText("blocked gate draft"); break;
            case 55:
                gateEditor=client.currentScreen; corruptOptions=source.getParent().getParent().resolve("m2-corrupt-options-owned.json");
                Files.write(corruptOptions,"{broken".getBytes(StandardCharsets.UTF_8));
                faultOptions=new TrainerClient(); faultOptions.initializeOptions(trainer.profileRepository(),new ClientOptionsStore(corruptOptions));
                faultOptions.session().accept(trainer.session().get()); client.openScreen(new TrainerOptionsScreen(faultOptions)); break;
            case 56:
                screenshot(client,"23-corrupt-options-feedback"); click(client,"Mode:");
                require(!faultOptions.optionMessages().isEmpty()&&new String(Files.readAllBytes(corruptOptions),StandardCharsets.UTF_8).equals("{broken"),"corrupt options preserved and saving disabled"); break;
            case 57: client.openScreen(gateEditor); require(editor.dirty(),"return to retained gate draft"); break;
            case 58:
                trainingDone=false; trainingFailure=null; java.util.UUID gatePlayer=client.player.getUuid();
                client.getServer().execute(()-> { try { dev.hotbarswaptrainer.smoke.ProfileTrainingAcceptance.start(client.getServer().getPlayerManager().getPlayer(gatePlayer)); }
                    catch(Throwable e) { trainingFailure=e; } }); break;
            case 59:
                require(trainingFailure==null&&!trainer.canEditProfiles()&&!editor.canSave(),"active gate disables save");
                dev.hotbarswaptrainer.smoke.UiRestoreGate.arm(); java.util.UUID deniedPlayer=client.player.getUuid();
                client.getServer().execute(()-> { try { dev.hotbarswaptrainer.smoke.ProfileTrainingAcceptance.denyRestore(client.getServer().getPlayerManager().getPlayer(deniedPlayer)); }
                    catch(Throwable e) { trainingFailure=e; } finally { trainingDone=true; } }); break;
            case 60:
                require(trainer.session().get().phase.equals("RESTORING"),"actual RESTORING projection");
                require(!trainer.canEditProfiles()&&!editor.canSave(),"RESTORING entry/save blocked");
                editor.save(); require(Arrays.equals(gateSource,Files.readAllBytes(source))&&editor.dirty(),"restore gate retains draft and source");
                screenshot(client,"18-restoring-save-blocked"); dev.hotbarswaptrainer.smoke.UiRestoreGate.release.countDown(); break;
            case 61:
                require(trainingDone&&trainingFailure==null,"denied restore completed: "+trainingFailure);
                require(trainer.session().get().phase.equals("ERROR_LOCKED")&&!trainer.canEditProfiles()&&!editor.canSave(),"actual ERROR_LOCKED gate");
                editor.save(); require(Arrays.equals(gateSource,Files.readAllBytes(source))&&editor.dirty(),"locked gate retains draft and source");
                screenshot(client,"19-error-locked-save-blocked");
                trainingDone=false; java.util.UUID retryPlayer=client.player.getUuid();
                client.getServer().execute(()-> { try { dev.hotbarswaptrainer.smoke.ProfileTrainingAcceptance.retryRestore(client.getServer().getPlayerManager().getPlayer(retryPlayer)); }
                    catch(Throwable e) { trainingFailure=e; } finally { trainingDone=true; } }); break;
            case 62:
                require(trainingDone&&trainingFailure==null&&trainer.canEditProfiles(),"restore retry releases UI gate");
                require(editor.dirty()&&Arrays.equals(gateSource,Files.readAllBytes(source)),"retry preserves blocked draft/source");
                click(client,"Cancel"); click(client,"Confirm"); click(client,"Back"); break;
            case 63:
                trainingDone=false;
                client.getServer().execute(()-> { try {
                    require(((net.minecraft.server.integrated.IntegratedServer)client.getServer()).openToLan(net.minecraft.world.GameMode.SURVIVAL,false,0),"UI LAN publication");
                } catch(Throwable e) { trainingFailure=e; } finally { trainingDone=true; } }); break;
            case 64:
                require(trainingDone&&trainingFailure==null&&!trainer.canEditProfiles(),"actual LAN blocks history/editor/save");
                screenshot(client,"20-lan-entry-blocked");
                finish(client,"passed","65 real-client steps: prior M2-C workflow plus history entry, empty history, 3 pages, details/PB, saved long name/hash, hints/hash filters, refresh, corrupt/unknown warnings, small-window scrolling and retained page. Valid OPTIONAL rule, Dirt candidate, count32 and typed NBT saved through screens; actual PREPARING blocks entry; visually saved editor_fixture is reloaded and solved by real server packet handlers, both saved inventories restored, new history generated. Actual profile write denial, duplicate-ID feedback/reload and corrupt-options UI preserve source/drafts. Actual RESTORING, real write-denial ERROR_LOCKED, successful restore retry and LAN transitions block entry/save while retaining draft/source. Options saved for fresh-process reopen. Programmatic input only."); break;
            default: throw new IllegalStateException("Unexpected step");
        }
    }
    private static HistoryBrowser historyBrowser(Screen screen) throws Exception {
        Field field=TrainerHistoryScreen.class.getDeclaredField("browser"); field.setAccessible(true); return (HistoryBrowser)field.get(screen);
    }
    private void historyFixture(MinecraftClient client) throws Exception {
        java.util.UUID player=client.player.getUuid();
        java.nio.file.Path root=client.runDirectory.toPath().resolve("config/hotbar-swap-trainer/history");
        dev.hotbarswaptrainer.stats.HistoryStore store=new dev.hotbarswaptrainer.stats.HistoryStore(root);
        require(store.read(player).records.isEmpty(),"empty history");
        client.openScreen(new TrainerHistoryScreen(options,trainer.historyBrowser()));
        screenshot(client,"09-empty-history"); client.openScreen(options);
        String hash=editor.expected().getHash();
        for(int i=0;i<23;i++) {
            java.util.UUID id=new java.util.UUID(8675309,i);
            dev.hotbarswaptrainer.stats.SessionMetadata metadata=new dev.hotbarswaptrainer.stats.SessionMetadata(player,100,200+i,
                    "deleted-profile","Saved historical name with a very long label — configuration may now be deleted",i%2==0,
                    dev.hotbarswaptrainer.stats.SessionMetadata.TIMING,dev.hotbarswaptrainer.stats.SessionMetadata.SCORING,
                    dev.hotbarswaptrainer.stats.SessionMetadata.ENVIRONMENT,dev.hotbarswaptrainer.stats.SessionMetadata.EndState.COMPLETED);
            store.save(player,new dev.hotbarswaptrainer.stats.SessionResult(id,dev.hotbarswaptrainer.task.TrainingMode.SINGLE_ITEM,
                    hash,1,java.util.Collections.singletonList(new dev.hotbarswaptrainer.stats.TaskResult(java.util.UUID.randomUUID(),
                    dev.hotbarswaptrainer.stats.TaskResult.Outcome.SUCCESS,1000000L+i,1,1,0,0,0,"")),metadata));
        }
        Files.write(root.resolve(player.toString()).resolve(java.util.UUID.randomUUID()+".json"),"{broken".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve(player.toString()).resolve(java.util.UUID.randomUUID()+".json"),"{\"schemaVersion\":999}".getBytes(StandardCharsets.UTF_8));
    }
    private void reopen(MinecraftClient client) throws Exception {
        require(trainer.goal()==73&&trainer.showHints()&&trainer.mode()==dev.hotbarswaptrainer.task.TrainingMode.FULL_INVENTORY,"fresh process options");
        HistoryBrowser browser=trainer.historyBrowser(); require(browser.refresh()&&browser.snapshot.records.size()==25,"restart history count");
        require(browser.snapshot.errors.size()==2,"restart damaged history warnings");
        dev.hotbarswaptrainer.stats.PersonalBests pb=dev.hotbarswaptrainer.stats.PersonalBests.forConditions(browser.snapshot,browser.snapshot.records.get(0));
        require(pb.task.isPresent()&&pb.total.isPresent()&&pb.incomplete,"restart PB derived from valid records");
        client.openScreen(new TrainerHistoryScreen(new TrainerOptionsScreen(trainer),browser)); screenshot(client,"14-history-restart");
        finish(client,"passed","Fresh production client process restored all four options; 25 history records and PBs survive restart, with 2 preserved damaged-history warnings.");
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
