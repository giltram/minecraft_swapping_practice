package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.SlotRule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Compact layout overview. Slot clicks open the multi-select item page. */
final class ProfileEditorScreen extends EditorFormScreen {
    private final Screen parent;
    private final TrainerClient trainer;
    private final ProfileEditorSession editor;
    private final List<SlotIcon> icons = new ArrayList<>();
    ProfileEditorScreen(Screen parent, TrainerClient trainer, ProfileEditorSession editor) {
        super(text("layout")); this.parent = parent; this.trainer = trainer; this.editor = editor;
    }
    @Override protected void buildForm() {
        icons.clear();
        field(text("name"), editor.draft().getName(), 1048576, editor.draft()::setName);
        paragraph(text("chooseSlot"), 0xDDDDDD);
        LoadoutProfile valid = editor.validatedProfile();
        int spacing = Math.min(28, viewport.width / 9);
        int start = viewport.left + (viewport.width - spacing * 9) / 2;
        int row = cursor + 12;
        for (int index = 0; index < 9; index++) slotButton(index, start + index * spacing, row, valid);
        cursor = row + 36;
        paragraph(text("offhand"), 0xDDDDDD);
        slotButton(9, viewport.left + viewport.width / 2 - 10, cursor, valid); cursor += 36;
        if (editor.feedback() != ProfileEditorSession.Feedback.NONE) paragraph(feedback(), 0xFFDD88);
        String error = editor.validate();
        if (!error.isEmpty()) paragraph(text("fixBeforeSave", error), 0xFF8888);
        if (editor.reloadRequired()) {
            paragraph(text("saveLocked"), 0xFF8888);
            action(text("reload"), () -> confirm(text("reloadConfirm"), () -> { editor.reload(); client.openScreen(this); }));
        }
        action(text("reset"), () -> confirm(text("resetConfirm"), () -> { editor.reset(); client.openScreen(this); }));
        footer(text("save"), text("cancel"));
    }
    private void slotButton(int index, int x, int offset, LoadoutProfile valid) {
        ProfileDraft.Slot rule = editor.draft().getSlots().get(index);
        ItemStack stack = rule.getAlternatives().isEmpty() ? ItemStack.EMPTY : ProfileItemCatalog.preview(
                rule.getAlternatives().get(0), valid == null ? null : valid.getSlots().get(index).getAlternatives().get(0).getTemplate());
        SlotIcon icon = new SlotIcon(index, offset, x, stack, rule); icons.add(icon);
        ButtonWidget button = new ButtonWidget(x, 0, 20, 20,
                new LiteralText(EditorText.slot(index) + ": " + ProfileSlotPresentation.summary(rule)), ignored -> select(index)) {
            @Override public void renderButton(MatrixStack matrices, int mx, int my, float delta) {
                int border = isHovered() || isFocused() ? 0xFFFFFFFF : 0xFF777777;
                fill(matrices, this.x, this.y, this.x + 20, this.y + 20, border);
                fill(matrices, this.x + 1, this.y + 1, this.x + 19, this.y + 19, 0xFF292929);
                if (!stack.isEmpty()) {
                    client.getItemRenderer().renderInGuiWithOverrides(stack, this.x + 2, this.y + 2);
                    client.getItemRenderer().renderGuiItemOverlay(textRenderer, stack, this.x + 2, this.y + 2, Integer.toString(stack.getCount()));
                } else drawCenteredString(matrices, textRenderer, rule.getKind() == SlotRule.Kind.EMPTY ? "×" : "*", this.x + 10, this.y + 5,
                        rule.getKind() == SlotRule.Kind.EMPTY ? 0xFF9999 : 0x99DDFF);
            }
        };
        placedControl(button, offset);
    }
    private void select(int index) { client.openScreen(new ProfileItemPickerScreen(this, editor, index)); }
    @Override protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) {
        for (SlotIcon icon : icons) {
            int top = viewport.y(icon.offset);
            if (icon.index < 9) drawCenteredString(matrices, textRenderer, Integer.toString(icon.index + 1), icon.x + 10, top - 10, 0xDDDDDD);
            drawCenteredString(matrices, textRenderer, icon.rule.getAlternatives().size() > 99 ? "99+" : "[" + icon.rule.getAlternatives().size() + "]",
                    icon.x + 10, top + 22, 0xDDDDDD);
            if (x >= icon.x && x < icon.x + 20 && y >= top && y < top + 20) return icon.stack;
        }
        return ItemStack.EMPTY;
    }
    @Override protected void renderDetails(MatrixStack matrices, int x, int y) {
        if (!viewport.contains(x, y)) return;
        for (SlotIcon icon : icons) if (x >= icon.x && x < icon.x + 20 && y >= viewport.y(icon.offset) && y < viewport.y(icon.offset) + 20) {
            // Supplemental rule tooltip, including for empty slots. Item details remain vanilla.
            if (icon.stack.isEmpty()) renderTooltip(matrices, new LiteralText(EditorText.slot(icon.index) + " · " + ProfileSlotPresentation.summary(icon.rule)), x, y);
        }
    }
    private String feedback() {
        switch (editor.feedback()) {
            case SAVED: return text("saved");
            case RESET: return text("resetDone");
            case RELOADED: return text("reloaded");
            case SAVE_FAILED: return text("saveFailed", editor.message());
            case REOPEN_FAILED: return text("reopenFailed", editor.message());
            case RELOAD_FAILED: return text("reloadFailed", editor.message());
            case RELOAD_REQUIRED: return text("saveLocked");
            case BLOCKED: return text("gate");
            default: return text("fixBeforeSave", editor.message());
        }
    }
    private void confirm(String explanation, Runnable confirmed) { client.openScreen(new EditorConfirmScreen(this, explanation, confirmed)); }
    @Override public void tick() { super.tick(); setFooterActive(0, editor.canSave()); }
    @Override protected void footerAction(int choice) {
        if (choice == 0) { editor.save(); trainer.refreshProfiles(); viewport.setScroll(viewport.maximumScroll()); rebuild(); }
        else onClose();
    }
    @Override public void onClose() {
        if (editor.dirty()) confirm(text("discardConfirm"), () -> client.openScreen(parent));
        else client.openScreen(parent);
    }
    private static final class SlotIcon {
        final int index, offset, x; final ItemStack stack; final ProfileDraft.Slot rule;
        SlotIcon(int index, int offset, int x, ItemStack stack, ProfileDraft.Slot rule) {
            this.index = index; this.offset = offset; this.x = x; this.stack = stack; this.rule = rule;
        }
    }
}
