package dev.hotbarswaptrainer.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Search all registered non-air items by ID or localized display name, using vanilla icons/tooltips. */
final class ProfileItemPickerScreen extends EditorFormScreen {
    private final Screen parent;
    private final Consumer<String> selected;
    private final List<ProfileItemCatalog.Choice> items = ProfileItemCatalog.items();
    private final List<Icon> icons = new ArrayList<>();
    private String query = "";
    private boolean searchChanged;
    ProfileItemPickerScreen(Screen parent, Consumer<String> selected) {
        super("Choose item"); this.parent = parent; this.selected = selected;
    }
    @Override protected void buildForm() {
        icons.clear();
        field("Search item ID or display name", query, 256, text -> { query = text; searchChanged = true; });
        List<ProfileItemCatalog.Choice> found = new ArrayList<>();
        for (ProfileItemCatalog.Choice item : items) if (item.matches(query)) found.add(item);
        paragraph(found.size() + " items. Hover for details; Tab to navigate; click/Enter to select for the draft.", 0xBBBBBB);
        SlotCandidateLayout grid = new SlotCandidateLayout(width, height, new int[] { found.size() });
        int start = cursor;
        for (int index = 0; index < found.size(); index++) {
            ProfileItemCatalog.Choice item = found.get(index);
            int offset = start + grid.candidateY(0, index) - grid.top;
            int x = grid.candidateX(index);
            ButtonWidget button = new ButtonWidget(x, 0, 20, 20, new LiteralText(item.name + " (" + item.id + ")"),
                    ignored -> selected.accept(item.id)) {
                @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                    // Keep vanilla focus/hover background while the item occupies its face.
                    net.minecraft.text.Text label = getMessage();
                    long narration = nextNarration;
                    setMessage(LiteralText.EMPTY); super.renderButton(matrices, mouseX, mouseY, delta); setMessage(label);
                    nextNarration = narration;
                    client.getItemRenderer().renderInGuiWithOverrides(item.icon, this.x + 2, this.y + 2);
                }
            };
            placedControl(button, offset); icons.add(new Icon(offset, x, item.icon));
        }
        cursor = start + grid.contentHeight();
        footer("Back (no selection)");
    }
    @Override public void tick() {
        super.tick();
        if (searchChanged) { searchChanged = false; viewport.setScroll(0); rebuild(); }
    }
    @Override protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) {
        for (Icon icon : icons) if (viewport.visible(icon.offset, 20) && x >= icon.x + 2 && x < icon.x + 18
                && y >= viewport.y(icon.offset) + 2 && y < viewport.y(icon.offset) + 18) return icon.stack;
        return ItemStack.EMPTY;
    }
    @Override public void onClose() { client.openScreen(parent); }
    private static final class Icon {
        final int offset, x; final ItemStack stack;
        Icon(int offset, int x, ItemStack stack) { this.offset = offset; this.x = x; this.stack = stack; }
    }
}
