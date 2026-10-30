package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileDraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** One searchable catalogue: toggle allowed items, inspect every selection and edit quantities inline. */
final class ProfileItemPickerScreen extends EditorFormScreen {
    private final Screen parent;
    private final Consumer<String> selected;
    private final ProfileEditorSession editor;
    private final int slotIndex;
    private final List<ProfileItemCatalog.Choice> items = ProfileItemCatalog.items();
    private final List<ProfileItemCatalog.Choice> found = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();
    private ProfileDraft.Candidate activeItem;
    private TextFieldWidget searchInput, quantityInput;
    private String query = "";
    private boolean searchChanged;

    /** Single selection is used only by the existing advanced per-item editor. */
    ProfileItemPickerScreen(Screen parent, Consumer<String> selected) {
        this(parent, null, -1, selected);
    }
    ProfileItemPickerScreen(Screen parent, ProfileEditorSession editor, int slotIndex) {
        this(parent, editor, slotIndex, null);
    }
    private ProfileItemPickerScreen(Screen parent, ProfileEditorSession editor, int slotIndex, Consumer<String> selected) {
        super(text("chooseItems")); this.parent = parent; this.editor = editor; this.slotIndex = slotIndex; this.selected = selected;
    }
    private List<ProfileDraft.Candidate> allowedItems() { return editor.draft().getSlots().get(slotIndex).getAlternatives(); }
    private void toggle(ProfileItemCatalog.Choice item) {
        if (editor == null) { selected.accept(item.id); return; }
        List<ProfileDraft.Candidate> existing = editor.selectedItems(slotIndex, item.id);
        if (existing.isEmpty()) { activeItem = editor.selectItem(slotIndex, item.id); refreshSelection(); }
        else deselect(existing);
    }
    private void deselect(List<ProfileDraft.Candidate> removed) {
        Runnable change = () -> { editor.deselectItems(slotIndex, removed); client.openScreen(this); };
        if (editor.removalNeedsConfirmation(slotIndex, removed))
            client.openScreen(new EditorConfirmScreen(this, text("deselectCustomConfirm"), change));
        else { editor.deselectItems(slotIndex, removed); refreshSelection(); }
    }
    private void refreshSelection() { setFocused(null); rebuild(); }

    @Override protected void buildForm() {
        icons.clear(); found.clear(); quantityInput = null;
        searchInput = field(text("search"), query, 256, value -> { query = value; searchChanged = true; });
        if (editor != null) buildSelections();
        for (ProfileItemCatalog.Choice item : items) if (item.matches(query)) found.add(item);
        Set<String> selectedIds = new HashSet<>();
        if (editor != null) for (ProfileDraft.Candidate item : allowedItems()) selectedIds.add(item.getItem());
        SlotCandidateLayout grid = new SlotCandidateLayout(width, height, new int[] { found.size() });
        int start = cursor - SlotCandidateLayout.HEADER_HEIGHT;
        for (int index = 0; index < found.size(); index++) {
            ProfileItemCatalog.Choice item = found.get(index);
            int offset = start + grid.candidateY(0, index) - grid.top;
            iconButton(grid.candidateX(index), offset, item.icon, null, selectedIds.contains(item.id),
                    item.name + " (" + item.id + ")", () -> toggle(item));
        }
        cursor = start + grid.contentHeight();
        if (editor == null) footer(text("back"));
        else footer(text("done"), text("advancedCompact"));
        setInitialFocus(searchInput);
    }
    private void buildSelections() {
        List<ProfileDraft.Candidate> allowed = allowedItems();
        if (!allowed.contains(activeItem)) activeItem = allowed.isEmpty() ? null : allowed.get(0);
        paragraph(EditorText.slot(slotIndex) + " · " + allowed.size(), 0xDDDDDD);
        if (allowed.isEmpty()) { paragraph(text("noSelection"), 0xBBBBBB); return; }
        LoadoutProfile validated = editor.validatedProfile();
        int columns = Math.max(1, (viewport.width - 4) / 22), start = cursor;
        for (int index = 0; index < allowed.size(); index++) {
            ProfileDraft.Candidate item = allowed.get(index);
            ItemStack stack = ProfileItemCatalog.preview(item, validated == null ? null :
                    validated.getSlots().get(slotIndex).getAlternatives().get(index).getTemplate());
            iconButton(viewport.left + 2 + (index % columns) * 22, start + (index / columns) * 22,
                    stack, item, item == activeItem, text("editQuantity", stack.getName().getString()), () -> {
                activeItem = item; refreshSelection();
            });
        }
        cursor += ((allowed.size() + columns - 1) / columns) * 22 + 2;
        String quantityLabel = text("itemQuantityFor", ProfileItemCatalog.preview(activeItem).getName().getString());
        paragraph(quantityLabel, 0xDDDDDD);
        quantityInput = new TextFieldWidget(textRenderer, viewport.left + 2, 0, 50, 20,
                new LiteralText(quantityLabel));
        quantityInput.setMaxLength(1048576);
        quantityInput.setText(editor.count(activeItem));
        quantityInput.setChangedListener(value -> editor.setCount(activeItem, value));
        placedControl(quantityInput, cursor);
        placedControl(new ButtonWidget(viewport.left + 58, 0, 64, 20, new LiteralText(text("deleteItem")),
                ignored -> deselect(Collections.singletonList(activeItem))), cursor);
        cursor += 24;
        dynamicLine(() -> validQuantity() ? "" : text("quantityError"), 0xFF8888);
    }
    private boolean validQuantity() {
        return activeItem != null && ProfileEditorSession.validCount(editor.count(activeItem))
                && Integer.parseInt(editor.count(activeItem)) <= ProfileItemCatalog.preview(activeItem).getMaxCount();
    }
    private void iconButton(int x, int offset, ItemStack stack, ProfileDraft.Candidate item, boolean marked,
                            String label, Runnable clicked) {
        ButtonWidget button = new ButtonWidget(x, 0, 20, 20, new LiteralText(label), ignored -> clicked.run()) {
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                int border = isHovered() || isFocused() ? 0xFFFFFFFF : marked ? 0xFF77DD88 : 0xFF666666;
                fill(matrices, this.x, this.y, this.x + 20, this.y + 20, border);
                fill(matrices, this.x + 1, this.y + 1, this.x + 19, this.y + 19, marked ? 0xFF294B35 : 0xFF292929);
                client.getItemRenderer().renderInGuiWithOverrides(stack, this.x + 2, this.y + 2);
                if (item != null) client.getItemRenderer().renderGuiItemOverlay(textRenderer, stack, this.x + 2, this.y + 2,
                        ProfileEditorSession.validCount(editor.count(item)) && item.getCount() <= stack.getMaxCount()
                                ? Integer.toString(item.getCount()) : "?");
                else if (marked) fill(matrices, this.x + 1, this.y + 1, this.x + 5, this.y + 5, 0xFF77FF88);
            }
        };
        placedControl(button, offset); icons.add(new Icon(offset, x, stack));
    }
    @Override protected void footerAction(int choice) {
        if (editor != null && choice == 1) client.openScreen(new ProfileSlotScreen(this, editor, slotIndex));
        else onClose();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {
            if (quantityInput != null && quantityInput.isFocused()) {
                if (validQuantity()) setInitialFocus(searchInput);
                return true;
            }
            if (searchInput.isFocused() && found.size() == 1) { toggle(found.get(0)); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void tick() {
        super.tick();
        if (searchChanged) { searchChanged = false; rebuild(); }
    }
    @Override protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) {
        for (Icon icon : icons) if (viewport.visible(icon.offset, 20) && x >= icon.x && x < icon.x + 20
                && y >= viewport.y(icon.offset) && y < viewport.y(icon.offset) + 20) return icon.stack;
        return ItemStack.EMPTY;
    }
    @Override public void onClose() { client.openScreen(parent); }
    private static final class Icon {
        final int offset, x; final ItemStack stack;
        Icon(int offset, int x, ItemStack stack) { this.offset = offset; this.x = x; this.stack = stack; }
    }
}
