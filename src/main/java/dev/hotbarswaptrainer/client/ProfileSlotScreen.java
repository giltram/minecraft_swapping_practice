package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.SlotRule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

import static dev.hotbarswaptrainer.client.EditorText.text;

import java.util.ArrayList;
import java.util.List;

/** One slot's rule and arbitrarily long candidate list. */
final class ProfileSlotScreen extends EditorFormScreen {
    private final Screen parent;
    private final ProfileEditorSession editor;
    private final int slotIndex;
    private final List<Preview> previews = new ArrayList<>();
    ProfileSlotScreen(Screen parent, ProfileEditorSession editor, int slotIndex) {
        super(text("slotSettings", EditorText.slot(slotIndex)));
        this.parent = parent; this.editor = editor; this.slotIndex = slotIndex;
    }
    private ProfileDraft.Slot slot() { return editor.draft().getSlots().get(slotIndex); }
    @Override protected void buildForm() {
        previews.clear();
        LoadoutProfile validated = editor.validatedProfile();

        paragraph(ProfileSlotPresentation.summary(slot()), 0xFFDD88);
        for (SlotRule.Kind kind : SlotRule.Kind.values()) action(text("rule." + kind), () -> changeKind(kind));
        paragraph(text("ruleHelp"), 0xDDDDDD);
        action(text("addAllowed"), () -> client.openScreen(new ProfileItemPickerScreen(this, item -> {
            ProfileDraft.Candidate candidate = new ProfileDraft.Candidate(item, 1);
            slot().getAlternatives().add(candidate);
            client.openScreen(new ProfileCandidateScreen(this, editor, candidate));
        }))).active = slot().getKind() != SlotRule.Kind.EMPTY;
        int index = 0;
        for (ProfileDraft.Candidate candidate : slot().getAlternatives()) {
            paragraph(text("allowedItem", ++index, candidate.getItem(), editor.count(candidate)), 0xDDDDDD);
            ButtonWidget edit = new ButtonWidget(viewport.left + 24, 0, Math.min(200, viewport.width - 24), 20,
                    new LiteralText(text("editAllowed")), ignored ->
                    client.openScreen(new ProfileCandidateScreen(this, editor, candidate)));
            placedControl(edit, cursor);
            ItemStack icon = ProfileItemCatalog.preview(candidate, validated == null ? null :
                    validated.getSlots().get(slotIndex).getAlternatives().get(index - 1).getTemplate());
            previews.add(new Preview(cursor, icon)); cursor += 24;
            action(text("removeAllowed", index), () -> client.openScreen(new EditorConfirmScreen(this,
                    text("removeConfirm"), () -> {
                slot().getAlternatives().remove(candidate); client.openScreen(this);
            })));
        }
        footer(text("back"));
    }
    private void changeKind(SlotRule.Kind next) {
        if (slot().getKind() == next) return;
        String explanation = next == SlotRule.Kind.EMPTY ? text("emptyConfirm") : text("ruleConfirm", text("rule." + next));
        client.openScreen(new EditorConfirmScreen(this, explanation, () -> {
            if (next == SlotRule.Kind.EMPTY) slot().getAlternatives().clear();
            slot().setKind(next); client.openScreen(this);
        }));
    }
    @Override protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) {
        ItemStack hovered = ItemStack.EMPTY;
        for (Preview preview : previews) if (viewport.visible(preview.offset, 20)) {
            int iconY = viewport.y(preview.offset) + 2;
            ItemStack stack = preview.stack;
            client.getItemRenderer().renderInGuiWithOverrides(stack, viewport.left + 2, iconY);
            client.getItemRenderer().renderGuiItemOverlay(textRenderer, stack, viewport.left + 2, iconY);
            if (x >= viewport.left + 2 && x < viewport.left + 18 && y >= iconY && y < iconY + 16) hovered = stack;
        }
        return hovered;
    }
    @Override public void onClose() { client.openScreen(parent); }
    private static final class Preview {
        final int offset; final ItemStack stack;
        Preview(int offset, ItemStack stack) { this.offset = offset; this.stack = stack; }
    }
}
