package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Path;

/** Each selectable row retains the exact listed path and the snapshot loaded from that path. */
final class ProfileSourceScreen extends EditorFormScreen {
    private final Screen parent;
    private final TrainerClient trainer;
    private String error = "";
    ProfileSourceScreen(Screen parent, TrainerClient trainer) {
        super("Select profile source"); this.parent = parent; this.trainer = trainer;
    }
    @Override protected void buildForm() {
        paragraph("Choose an existing file. Profile ID is read-only. No changes are written until Save profile.", 0xDDDDDD);
        if (!error.isEmpty()) paragraph(error, 0xFF8888);
        if (!trainer.canEditProfiles()) paragraph("Wait for completed restoration in local single-player with LAN closed.", 0xFFAA88);
        else {
            ProfileRepository repository = trainer.profileRepository();
            try {
                for (Path source : repository.listProfileFiles()) {
                    paragraph(source.getFileName().toString(), 0xAAAAAA);
                    try {
                        LoadoutProfile snapshot = repository.load(source);
                        action(snapshot.getName() + " [" + snapshot.getId() + "]", () -> open(source, snapshot));
                    } catch (Exception failure) { paragraph(ProfileEditorSession.readable(failure), 0xFF8888); }
                }
            } catch (Exception failure) { paragraph(ProfileEditorSession.readable(failure), 0xFF8888); }
        }
        footer("Reload list", "Back");
    }
    private void open(Path source, LoadoutProfile expected) {
        try {
            ProfileEditorSession editor = new ProfileEditorSession(trainer.profileRepository(), source, expected, trainer::canEditProfiles);
            client.openScreen(new ProfileEditorScreen(this, trainer, editor));
        } catch (Exception failure) { error = "Cannot open: " + ProfileEditorSession.readable(failure); rebuild(); }
    }
    @Override protected void footerAction(int choice) { if (choice == 0) rebuild(); else onClose(); }
    @Override public void onClose() { trainer.refreshProfiles(); client.openScreen(parent); }
}
