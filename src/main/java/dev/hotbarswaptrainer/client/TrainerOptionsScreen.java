package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import static dev.hotbarswaptrainer.client.EditorText.text;

/** Preferences use the existing accepted options; raw training count is screen-local. */
final class TrainerOptionsScreen extends EditorFormScreen {
    private final TrainerClient trainer;
    private String goalDraft;
    private ButtonWidget editorButton, historyButton;
    private TextFieldWidget goalInput;
    TrainerOptionsScreen(TrainerClient trainer) {
        super(text("options")); this.trainer = trainer; goalDraft = Integer.toString(trainer.goal());
    }
    @Override protected void buildForm() {
        trainer.refreshProfiles();
        action(text("mode", TrainerHud.modeName(trainer.mode())), () -> { trainer.cycleMode(); rebuild(); });
        goalInput = field(text("trainingCountHelp"), goalDraft, 64, value -> {
            goalDraft = value; trainer.setGoal(value); updateButtons();
        });
        dynamicLine(() -> validGoal() ? "" : text("countError"), 0xFF8888);
        action(text("hints", text(trainer.showHints() ? "on" : "off")), () -> { trainer.toggleHints(); rebuild(); });
        action(text("swapCounts", text(trainer.showSwapCounts() ? "on" : "off")), () -> { trainer.toggleSwapCounts(); rebuild(); });
        editorButton = action(text("layout"), () -> {
            if (trainer.canEditProfiles()) client.openScreen(new ProfileSourceScreen(this, trainer));
        });
        historyButton = action(text("history"), () -> {
            if (trainer.canEditProfiles()) client.openScreen(new TrainerHistoryScreen(this, trainer.historyBrowser()));
        });
        TrainerProtocol.View view = trainer.session().get();
        if (showSessionMessage(view)) paragraph(view.message, view.phase.contains("ERROR") ? 0xFF8888 : 0xDDDDBB);
        for (String message : trainer.optionMessages()) paragraph(message, 0xFFAA88);
        footer(text("start"), text("done")); updateButtons();
    }
    private boolean validGoal() { return TrainerClient.parseGoal(goalDraft) != null; }
    private void acceptGoal() { if (validGoal()) trainer.setGoal(goalDraft); }
    static boolean showSessionMessage(TrainerProtocol.View view) {
        // The server sends IDLE/Ready with catalogue responses; it is not an option or error.
        return view != null && !view.message.isEmpty() && !("IDLE".equals(view.phase) && "Ready".equals(view.message));
    }
    @Override public void tick() { super.tick(); updateButtons(); }
    private void updateButtons() {
        if (historyButton == null) return;
        boolean idle = trainer.canEditProfiles();
        setFooterActive(0, idle && validGoal() && trainer.profileAvailable());
        editorButton.active = idle; historyButton.active = idle;
    }
    private void startTraining() {
        if (!trainer.canEditProfiles() || !validGoal() || !trainer.profileAvailable()) return;
        acceptGoal(); client.openScreen(null); trainer.start();
    }
    @Override protected void footerAction(int choice) { if (choice == 0) startTraining(); else onClose(); }
    @Override public void onClose() { client.openScreen(null); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (goalInput.isFocused() && (key == 257 || key == 335)) { startTraining(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
