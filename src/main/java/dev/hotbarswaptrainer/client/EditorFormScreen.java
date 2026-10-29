package dev.hotbarswaptrainer.client;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Vanilla form controls with one viewport for rendering, pointer events and focus scrolling. */
abstract class EditorFormScreen extends Screen {
    private final List<Control> controls = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    protected EditorViewport viewport;
    protected int cursor;
    private int retainedScroll;
    private int retainedFocus = -1;
    private final List<ButtonWidget> footer = new ArrayList<>();

    EditorFormScreen(String title) { super(new LiteralText(title)); }
    protected abstract void buildForm();

    @Override protected final void init() {
        client.keyboard.enableRepeatEvents(true);
        controls.clear(); labels.clear(); footer.clear();
        viewport = new EditorViewport(width, height);
        cursor = 2;
        buildForm();
        viewport.contentHeight(cursor + 2);
        viewport.setScroll(retainedScroll);
        positionControls();
        if (retainedFocus >= 0 && retainedFocus < children.size()) setInitialFocus(children.get(retainedFocus));
    }
    protected final void rebuild() {
        retain(); buttons.clear(); children.clear(); setFocused(null); init();
    }
    private void retain() {
        if (viewport != null) retainedScroll = viewport.scroll();
        retainedFocus = children.indexOf(getFocused());
    }
    @Override public void removed() { retain(); client.keyboard.enableRepeatEvents(false); }
    @Override public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        retain(); super.resize(client, width, height);
    }

    protected void paragraph(String text, int color) {
        // Wrap complete errors/paths. No invisible truncated repair instructions.
        for (String part : text.split("\n", -1)) {
            do {
                String line = textRenderer.trimToWidth(part, viewport.width - 4);
                if (line.isEmpty() && !part.isEmpty()) line = part.substring(0, 1);
                labels.add(new Label(cursor, line, color)); cursor += 11;
                part = part.substring(line.length());
            } while (!part.isEmpty());
        }
        cursor += 3;
    }
    protected ButtonWidget action(String label, Runnable action) {
        // The 1.16.1 vanilla atlas renderer assumes at most 200px; wider buttons sample outside its strip.
        int buttonWidth = Math.min(200, viewport.width);
        ButtonWidget button = new ButtonWidget(viewport.left + (viewport.width - buttonWidth) / 2, 0, buttonWidth, 20,
                new LiteralText(textRenderer.trimToWidth(label, buttonWidth - 8)), ignored -> action.run());
        control(button); return button;
    }
    protected TextFieldWidget field(String label, String value, int maxLength, Consumer<String> changed) {
        paragraph(label, 0xDDDDDD);
        TextFieldWidget input = new TextFieldWidget(textRenderer, viewport.left + 2, 0,
                viewport.width - 4, 20, new LiteralText(label));
        input.setMaxLength(maxLength);
        // Install listener after restoring text: opening/reflow must never change omission semantics.
        input.setText(value);
        input.setChangedListener(changed);
        control(input); return input;
    }
    private void control(AbstractButtonWidget widget) {
        controls.add(new Control(cursor, widget)); addButton(widget); cursor += 24;
    }
    protected void placedControl(AbstractButtonWidget widget, int offset) {
        controls.add(new Control(offset, widget)); addButton(widget);
    }
    protected void footer(String... labels) {
        int gap = 4, buttonWidth = Math.min(200, (viewport.width - gap * (labels.length - 1)) / labels.length);
        int left = viewport.left + (viewport.width - (buttonWidth + gap) * labels.length + gap) / 2;
        for (int index = 0; index < labels.length; index++) {
            final int choice = index;
            footer.add(addButton(new ButtonWidget(left + index * (buttonWidth + gap), height - 28,
                    buttonWidth, 20, new LiteralText(labels[index]), ignored -> footerAction(choice))));
        }
    }
    protected void footerAction(int choice) { onClose(); }
    protected void setFooterActive(int index, boolean active) { footer.get(index).active = active; }
    private void positionControls() { for (Control row : controls) row.widget.y = viewport.y(row.offset); }
    @Override public void tick() {
        for (Control row : controls) if (row.widget instanceof TextFieldWidget) ((TextFieldWidget) row.widget).tick();
    }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        positionControls();
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 10, 0xFFFFFF);
        double scale = client.getWindow().getScaleFactor();
        int bottom = (int) Math.round(viewport.bottom * scale), top = (int) Math.round(viewport.top * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.round(viewport.left * scale), client.getWindow().getFramebufferHeight() - bottom,
                (int) Math.round(viewport.width * scale), bottom - top);
        ItemStack hovered;
        try {
            for (Label label : labels) if (viewport.visible(label.offset, 10))
                textRenderer.drawWithShadow(matrices, label.text, viewport.left + 2, viewport.y(label.offset), label.color);
            boolean inside = viewport.contains(mouseX, mouseY);
            for (Control row : controls) if (viewport.visible(row.offset, 20))
                row.widget.render(matrices, inside ? mouseX : -1000, inside ? mouseY : -1000, delta);
            hovered = renderContents(matrices, inside ? mouseX : -1000, inside ? mouseY : -1000, delta);
        } finally { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
        if (viewport.maximumScroll() > 0) {
            int track = viewport.bottom - viewport.top;
            int thumb = Math.max(8, track * track / (track + viewport.maximumScroll()));
            int y = viewport.top + (track - thumb) * viewport.scroll() / viewport.maximumScroll();
            fill(matrices, viewport.left + viewport.width + 3, viewport.top,
                    viewport.left + viewport.width + 6, viewport.bottom, 0xFF333333);
            fill(matrices, viewport.left + viewport.width + 3, y,
                    viewport.left + viewport.width + 6, y + thumb, 0xFFAAAAAA);
        }
        for (ButtonWidget button : footer) button.render(matrices, mouseX, mouseY, delta);
        if (!hovered.isEmpty()) renderTooltip(matrices, hovered, mouseX, mouseY);
    }
    protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) { return ItemStack.EMPTY; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        positionControls();
        boolean inside = viewport.contains(x, y);
        if (!inside && (y >= viewport.top && y < viewport.bottom)) return false;
        // Never dispatch a footer/header click to a partially clipped form control.
        List<? extends Element> targets = inside ? new ArrayList<Element>(children) : new ArrayList<Element>(footer);
        for (Element target : targets) {
            if (inside && footer.contains(target)) continue;
            if (target.isMouseOver(x, y)) {
                for (Element other : children) if (other != target && other instanceof AbstractButtonWidget
                        && ((AbstractButtonWidget) other).isFocused()) other.changeFocus(false);
                if (target.mouseClicked(x, y, button)) {
                    // A callback can navigate or rebuild this screen; never focus a removed control.
                    if (children.contains(target)) {
                        if (target instanceof AbstractButtonWidget && !((AbstractButtonWidget) target).isFocused()) target.changeFocus(true);
                        setFocused(target); setDragging(button == 0);
                    }
                    return true;
                }
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return viewport.contains(x, y) && super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (!viewport.contains(x, y)) return false;
        viewport.setScroll(viewport.scroll() - (int) Math.round(amount * 28)); positionControls(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // Reveal focused text before editing after pointer scrolling.
        if (key != 256) revealFocus();
        boolean handled = super.keyPressed(key, scan, modifiers);
        revealFocus(); return handled;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        revealFocus(); return super.charTyped(character, modifiers);
    }
    private void revealFocus() {
        for (Control row : controls) if (row.widget == getFocused()) viewport.reveal(row.offset, 20);
        positionControls();
    }
    @Override public boolean isPauseScreen() { return false; }
    private static final class Control {
        final int offset; final AbstractButtonWidget widget;
        Control(int offset, AbstractButtonWidget widget) { this.offset = offset; this.widget = widget; }
    }
    private static final class Label {
        final int offset, color; final String text;
        Label(int offset, String text, int color) { this.offset = offset; this.text = text; this.color = color; }
    }
}
