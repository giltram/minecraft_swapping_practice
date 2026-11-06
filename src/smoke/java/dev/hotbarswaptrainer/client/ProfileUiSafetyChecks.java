package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.smoke.ProfileGateAcceptance;
import dev.hotbarswaptrainer.smoke.UiRestoreGate;
import dev.hotbarswaptrainer.stats.PersonalBests;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static dev.hotbarswaptrainer.client.EditorText.text;
import static dev.hotbarswaptrainer.client.UiSmokeControls.*;

/** Current-screen replacements for the retired driver's persistence and restoration checks. */
final class ProfileUiSafetyChecks {
    private final TrainerClient trainer;
    private final TrainerOptionsScreen options;
    private final Path source;
    private ProfileEditorSession editor;
    private byte[] original;
    private Path duplicate, corruptOptions;
    private TrainerClient faultOptions;
    private net.minecraft.client.gui.screen.Screen gateEditor;
    private int step;
    private volatile boolean serverDone;
    private volatile Throwable serverFailure;

    ProfileUiSafetyChecks(TrainerClient trainer, TrainerOptionsScreen options, Path source) {
        this.trainer = trainer; this.options = options; this.source = source;
    }

    boolean tick(MinecraftClient client) throws Exception {
        try {
            switch (step) {
                case 0:
                    client.openScreen(options);
                    HistoryBrowser empty = trainer.historyBrowser();
                    require(empty.refresh() && empty.snapshot.records.size() >= 2, "real finite/unlimited history");
                    Path history = source.getParent().getParent().resolve("history").resolve(client.player.getUuid().toString());
                    Files.write(history.resolve(UUID.randomUUID()+".json"), "{broken".getBytes(StandardCharsets.UTF_8));
                    Files.write(history.resolve(UUID.randomUUID()+".json"), "{\"schemaVersion\":999}".getBytes(StandardCharsets.UTF_8));
                    HistoryBrowser browser = trainer.historyBrowser();
                    require(browser.refresh() && browser.snapshot.errors.size()==2, "preserved damaged-history warnings");
                    require(PersonalBests.fastestTaskForMode(browser.snapshot, TrainingMode.SINGLE_ITEM,
                            trainer.environment()).isPresent(), "PB excludes damaged records");
                    click(client,text("history")); break;
                case 1:
                    require(client.currentScreen instanceof TrainerHistoryScreen && client.currentScreen.children().size()==1,
                            "current PB screen has only Back");
                    screenshot(client,"s01-pb-incomplete"); click(client,text("back"));
                    click(client,text("layout")); editLayout(client,"Usability fixture [editor_fixture]");
                    editor=session(client.currentScreen); original=Files.readAllBytes(source);
                    input(client,text("name")).setText("duplicate draft");
                    duplicate=source.resolveSibling("ui-duplicate-owned.json");
                    require(!Files.exists(duplicate),"fresh duplicate fixture"); Files.write(duplicate,original);
                    click(client,text("save"));
                    require(editor.reloadRequired()&&editor.dirty()&&Arrays.equals(original,Files.readAllBytes(source)),
                            "duplicate ID preserves source/draft"); break;
                case 2:
                    screenshot(client,"s02-duplicate-id");
                    Files.move(duplicate,duplicate.resolveSibling("ui-duplicate-owned.evidence"));
                    click(client,text("reload")); click(client,text("confirm"));
                    input(client,text("name")).setText("retained protected draft"); gateEditor=client.currentScreen;
                    corruptOptions=source.getParent().getParent().resolve("ui-corrupt-options-owned.json");
                    Files.write(corruptOptions,"{broken".getBytes(StandardCharsets.UTF_8));
                    faultOptions=new TrainerClient();
                    faultOptions.initializeOptions(trainer.profileRepository(),new ClientOptionsStore(corruptOptions));
                    faultOptions.session().accept(trainer.session().get()); client.openScreen(new TrainerOptionsScreen(faultOptions)); break;
                case 3:
                    click(client,text("mode",""));
                    require(!faultOptions.optionMessages().isEmpty()&&new String(Files.readAllBytes(corruptOptions),StandardCharsets.UTF_8).equals("{broken"),
                            "corrupt options stay intact and saving stays disabled");
                    screenshot(client,"s03-corrupt-options"); client.openScreen(gateEditor);
                    server(client,ProfileGateAcceptance::start); break;
                case 4:
                    if (!serverDone) return false;
                    require(serverFailure==null,"gate START: "+serverFailure);
                    require(trainer.session().get().phase.equals("PREPARING")&&!trainer.canEditProfiles()&&!editor.canSave(),
                            "actual PREPARING blocks entry/save");
                    editor.save(); unchanged(); screenshot(client,"s04-preparing");
                    UiRestoreGate.arm(); server(client,ProfileGateAcceptance::denyRestore); break;
                case 5:
                    if (!trainer.session().get().phase.equals("RESTORING")) return false;
                    require(!trainer.canEditProfiles()&&!editor.canSave(),"actual RESTORING blocks entry/save");
                    editor.save(); unchanged(); screenshot(client,"s05-restoring"); UiRestoreGate.release.countDown(); break;
                case 6:
                    if (!serverDone) return false;
                    require(serverFailure==null,"restore denial: "+serverFailure);
                    require(trainer.session().get().phase.equals("ERROR_LOCKED")&&!trainer.canEditProfiles()&&!editor.canSave(),
                            "actual ERROR_LOCKED blocks entry/save");
                    editor.save(); unchanged(); screenshot(client,"s06-error-locked");
                    server(client,ProfileGateAcceptance::retryRestore); break;
                case 7:
                    if (!serverDone) return false;
                    require(serverFailure==null&&trainer.canEditProfiles()&&editor.canSave(),"restore retry releases gate: "+serverFailure);
                    unchanged();
                    server(client,player -> require(((net.minecraft.server.integrated.IntegratedServer)player.getServer())
                            .openToLan(GameMode.SURVIVAL,false,0),"actual LAN publication")); break;
                case 8:
                    if (!serverDone) return false;
                    require(serverFailure==null&&!trainer.canEditProfiles()&&!editor.canSave(),"actual LAN blocks entry/save: "+serverFailure);
                    editor.save(); unchanged(); screenshot(client,"s07-lan");
                    click(client,text("cancel")); click(client,text("confirm")); click(client,text("back"));
                    require(!requireButton(client,text("layout")).active&&!requireButton(client,text("history")).active,
                            "LAN disables options entry buttons");
                    return true;
                default: throw new IllegalStateException("Unexpected safety step "+step);
            }
            step++;
            return false;
        } catch (Exception failure) {
            UiRestoreGate.release.countDown();
            throw new IllegalStateException("UI safety step "+step,failure);
        }
    }
    private void unchanged() throws Exception {
        require(editor.dirty()&&editor.draft().getName().equals("retained protected draft")
                &&Arrays.equals(original,Files.readAllBytes(source)),"protected save retains exact source and draft");
    }
    private interface ServerCheck { void run(ServerPlayerEntity player) throws Exception; }
    private void server(MinecraftClient client, ServerCheck check) {
        UUID player=client.player.getUuid(); serverDone=false; serverFailure=null;
        client.getServer().execute(() -> {
            try { check.run(client.getServer().getPlayerManager().getPlayer(player)); }
            catch (Throwable failure) { serverFailure=failure; failure.printStackTrace(); }
            finally { serverDone=true; }
        });
    }
}
