package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import net.minecraft.client.gui.screen.Screen;

/** Profile overview only: name, structure/applicability, slot navigation and transaction actions. */
final class ProfileEditorScreen extends EditorFormScreen {
    private final Screen parent;
    private final TrainerClient trainer;
    private final ProfileEditorSession editor;
    ProfileEditorScreen(Screen parent, TrainerClient trainer, ProfileEditorSession editor) {
        super("Edit profile"); this.parent = parent; this.trainer = trainer; this.editor = editor;
    }
    @Override protected void buildForm() {
        paragraph("Source: " + editor.source + "\nID (read-only): " + editor.draft().getId(), 0xBBBBBB);
        if (!editor.message().isEmpty()) paragraph(editor.message(), 0xFFDD88);
        if (editor.reloadRequired()) paragraph("Saving is locked. Repair the source/conflict, then explicitly Reload source. Your draft is retained until you confirm.", 0xFF8888);
        field("Display name", editor.draft().getName(), 1048576, editor.draft()::setName);
        action("Validate draft / mode availability", this::showResults);
        String error = editor.validate();
        paragraph(error.isEmpty() ? "Structure: valid" : "Structure: " + error, error.isEmpty() ? 0xAAFFAA : 0xFF8888);
        paragraph(editor.applicability(), 0xDDDDDD);
        paragraph("Saving requires local single-player, LAN closed and confirmed completed restoration.", 0xBBBBBB);
        paragraph("Scroll for all ten slots and source/reset actions. Children edit this draft only.", 0xBBBBBB);
        for (int index = 0; index < editor.draft().getSlots().size(); index++) {
            final int slot = index;
            ProfileDraft.Slot rule = editor.draft().getSlots().get(slot);
            action(ProfileRepository.slotName(slot) + ": " + rule.getKind() + " (" + rule.getAlternatives().size() + ")",
                    () -> client.openScreen(new ProfileSlotScreen(this, editor, slot)));
        }
        action("Reset draft to opening values…", () -> confirm("Discard all draft edits and restore the values from the last successful open/save? No file is changed.", () -> {
            editor.reset(); client.openScreen(this);
        }));
        action("Reload this source…", () -> confirm("Discard the draft and explicitly reload only " + editor.source + "? Resolve external damage/duplicate IDs first. No other source will be selected.", () -> {
            editor.reload(); client.openScreen(this);
        }));
        footer("Save profile", "Cancel");
    }
    private void confirm(String explanation, Runnable confirmed) {
        client.openScreen(new EditorConfirmScreen(this, explanation, confirmed));
    }
    @Override public void tick() { super.tick(); setFooterActive(0, editor.canSave()); }
    @Override protected void footerAction(int choice) {
        if (choice == 0) {
            editor.save(); trainer.refreshProfiles(); showResults();
        } else onClose();
    }
    private void showResults() { viewport.setScroll(0); setFocused(null); rebuild(); }
    @Override public void onClose() {
        if (editor.dirty()) confirm("This profile has unsaved edits. Discard them and leave the editor?", () -> client.openScreen(parent));
        else client.openScreen(parent);
    }
}
