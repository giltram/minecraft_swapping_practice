package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import net.minecraft.client.gui.screen.Screen;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Raw quantity text belongs to the session, so incomplete input survives every screen lifecycle. */
final class ProfileCandidateScreen extends EditorFormScreen {
    private final Screen parent;
    private final ProfileEditorSession editor;
    private final ProfileDraft.Candidate candidate;
    ProfileCandidateScreen(Screen parent, ProfileEditorSession editor, ProfileDraft.Candidate candidate) {
        super(text("editAllowed")); this.parent = parent; this.editor = editor; this.candidate = candidate;
    }
    @Override protected void buildForm() {
        paragraph(text("item", candidate.getItem()), 0xDDDDDD);
        action(text("chooseItem"), () -> client.openScreen(new ProfileItemPickerScreen(this, item -> {
            candidate.setItem(item); client.openScreen(this);
        })));
        field(text("quantity", ProfileItemCatalog.preview(candidate).getMaxCount()), editor.count(candidate), 1048576,
                value -> editor.setCount(candidate, value));
        action(text("nbtSettings"), () -> client.openScreen(new ProfileAdvancedScreen(this, candidate)));

        footer(text("back"));
    }
    @Override public void onClose() { client.openScreen(parent); }
}
