package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Each selectable row retains the exact listed path and the snapshot loaded from that path. */
final class ProfileSourceScreen extends EditorFormScreen {
    private final Screen parent;
    private final TrainerClient trainer;
    private String error = "";
    ProfileSourceScreen(Screen parent, TrainerClient trainer) {
        super(text("chooseProfile")); this.parent = parent; this.trainer = trainer;
    }
    @Override protected void buildForm() {
        trainer.refreshProfiles();
        paragraph(text("chooseProfileHelp"), 0xDDDDDD);
        if (!error.isEmpty()) paragraph(error, 0xFF8888);
        if (!trainer.canEditProfiles()) paragraph(text("gate"), 0xFFAA88);
        else {
            ProfileRepository repository = trainer.profileRepository();
            try {
                Set<String> listedIds = new HashSet<>();
                for (Path source : repository.listProfileFiles()) {
                    paragraph(source.getFileName().toString(), 0xAAAAAA);
                    try {
                        LoadoutProfile snapshot = repository.load(source);
                        boolean selectable = listedIds.add(snapshot.getId());
                        if (!selectable) paragraph(text("duplicateLayout"), 0xFF8888);
                        profileRow(source, snapshot, selectable);
                    } catch (Exception failure) { paragraph(ProfileEditorSession.readable(failure), 0xFF8888); }
                }
            } catch (Exception failure) { paragraph(ProfileEditorSession.readable(failure), 0xFF8888); }
        }
        footer(text("refresh"), text("back"));
    }
    private void profileRow(Path source, LoadoutProfile snapshot, boolean selectable) {
        boolean selected = selectable && trainer.profileAvailable() && snapshot.getId().equals(trainer.profile());
        if (selected) paragraph(text("selectedLayout"), 0x88FF88);
        int editWidth = 48, gap = 4;
        int selectWidth = Math.min(200, viewport.width - editWidth - gap);
        int left = viewport.left + (viewport.width - selectWidth - gap - editWidth) / 2;
        String label = snapshot.getName() + " [" + snapshot.getId() + "]";
        ButtonWidget selectButton = new ButtonWidget(left, 0, selectWidth, 20,
                new LiteralText(textRenderer.trimToWidth(label, selectWidth - 8)), ignored -> select(snapshot.getId()));
        selectButton.active = selectable;
        placedControl(selectButton, cursor);
        placedControl(new ButtonWidget(left + selectWidth + gap, 0, editWidth, 20,
                new LiteralText(text("editLayout")), ignored -> open(source, snapshot)), cursor);
        cursor += 24;
    }
    private void select(String id) {
        if (!trainer.canEditProfiles()) { error = text("gate"); rebuild(); return; }
        error = trainer.selectProfile(id) ? "" : text("layoutUnavailable");
        rebuild();
    }
    private void open(Path source, LoadoutProfile expected) {
        if (!trainer.canEditProfiles()) { error = text("gate"); rebuild(); return; }
        try {
            ProfileEditorSession editor = new ProfileEditorSession(trainer.profileRepository(), source, expected, trainer::canEditProfiles);
            client.openScreen(new ProfileEditorScreen(this, trainer, editor));
        } catch (Exception failure) { error = text("openFailed", ProfileEditorSession.readable(failure)); rebuild(); }
    }
    @Override protected void footerAction(int choice) { if (choice == 0) rebuild(); else onClose(); }
    @Override public void onClose() { trainer.refreshProfiles(); client.openScreen(parent); }
}
