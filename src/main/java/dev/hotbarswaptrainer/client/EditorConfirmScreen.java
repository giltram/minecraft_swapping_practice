package dev.hotbarswaptrainer.client;

import net.minecraft.client.gui.screen.Screen;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Scrollable confirmation text; Esc always keeps the draft. */
final class EditorConfirmScreen extends EditorFormScreen {
    private final Screen parent;
    private final String explanation;
    private final Runnable confirm;
    EditorConfirmScreen(Screen parent, String explanation, Runnable confirm) {
        super(text("confirmTitle"));
        this.parent = parent; this.explanation = explanation; this.confirm = confirm;
    }
    @Override protected void buildForm() { paragraph(explanation, 0xFFDD88); footer(text("confirm"), text("keepEditing")); }
    @Override protected void footerAction(int choice) { if (choice == 0) confirm.run(); else onClose(); }
    @Override public void onClose() { client.openScreen(parent); }
}
