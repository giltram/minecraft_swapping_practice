package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import net.minecraft.client.gui.screen.Screen;

/** Raw quantity text belongs to the session, so incomplete input survives every screen lifecycle. */
final class ProfileCandidateScreen extends EditorFormScreen {
    private final Screen parent;
    private final ProfileEditorSession editor;
    private final ProfileDraft.Candidate candidate;
    ProfileCandidateScreen(Screen parent, ProfileEditorSession editor, ProfileDraft.Candidate candidate) {
        super("Edit candidate"); this.parent = parent; this.editor = editor; this.candidate = candidate;
    }
    @Override protected void buildForm() {
        paragraph("Item: " + candidate.getItem(), 0xDDDDDD);
        action("Choose item…", () -> client.openScreen(new ProfileItemPickerScreen(this, item -> {
            candidate.setItem(item); client.openScreen(this);
        })));
        field("Quantity (positive whole number, within item stack limit)", editor.count(candidate), 1048576,
                value -> editor.setCount(candidate, value));
        action("Advanced NBT / matching…", () -> client.openScreen(new ProfileAdvancedScreen(this, candidate)));
        paragraph("Item selection never reads or changes your inventory. Back keeps the draft; only Save profile writes it.", 0xBBBBBB);
        footer("Back to slot");
    }
    @Override public void onClose() { client.openScreen(parent); }
}
