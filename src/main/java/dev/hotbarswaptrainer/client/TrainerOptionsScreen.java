package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** Training preferences and entry point for reordering profile slots. */
final class TrainerOptionsScreen extends Screen {
    private final TrainerClient trainer;
    private ButtonWidget startButton;
    private ButtonWidget layoutButton;
    private ButtonWidget editorButton;
    private ButtonWidget profileButton;
    private TextFieldWidget goalInput;
    private String goalDraft;
    private boolean validGoal = true;

    TrainerOptionsScreen(TrainerClient trainer) {
        super(new LiteralText("Hotbar Swap Trainer"));
        this.trainer = trainer;
        goalDraft = Integer.toString(trainer.goal());
    }

    @Override
    protected void init() {
        client.keyboard.enableRepeatEvents(true);
        trainer.refreshProfiles();
        int x = width / 2 - 100;
        int y = optionsTop();
        addButton(new ButtonWidget(x, y, 200, 20, modeLabel(), button -> {
            trainer.cycleMode(); button.setMessage(modeLabel());
        }));
        profileButton = addButton(new ButtonWidget(x, y + 24, 200, 20, profileLabel(), button -> {
            trainer.cycleProfile(); button.setMessage(profileLabel());
        }));
        goalInput = new TextFieldWidget(textRenderer, x + 80, y + 48, 120, 20,
                new LiteralText("Successful tasks (0 = unlimited)"));
        // Leave room for out-of-range input so ordinary overflow pastes show an error instead of truncating to a valid count.
        goalInput.setMaxLength(32);
        goalInput.setTextPredicate(text -> text.matches("[0-9]*"));
        goalInput.setChangedListener(text -> {
            goalDraft = text;
            validGoal = trainer.setGoal(text);
            updateButtons();
        });
        goalInput.setText(goalDraft);
        addChild(goalInput);
        setInitialFocus(goalInput);
        addButton(new ButtonWidget(x, y + 88, 200, 20, hintsLabel(), button -> {
            trainer.toggleHints(); button.setMessage(hintsLabel());
        }));
        layoutButton = addButton(new ButtonWidget(x, y + 112, 98, 20, new LiteralText("Slot order…"), button ->
                client.openScreen(new TrainerLayoutScreen(this, trainer))));
        editorButton = addButton(new ButtonWidget(x + 102, y + 112, 98, 20, new LiteralText("Edit profile…"), button -> {
            if (trainer.canEditProfiles()) client.openScreen(new ProfileSourceScreen(this, trainer));
        }));
        startButton = addButton(new ButtonWidget(x, y + 140, 98, 20, new LiteralText("Start"), button -> startTraining()));
        addButton(new ButtonWidget(x + 102, y + 140, 98, 20, new LiteralText("Done"), button -> client.openScreen(null)));
        updateButtons();
    }

    @Override
    public void tick() {
        goalInput.tick();
        updateButtons();
    }

    private void updateButtons() {
        if (startButton == null) return;
        boolean idle = !trainer.session().active() && client.isInSingleplayer();
        startButton.active = idle && validGoal && trainer.profileAvailable();
        layoutButton.active = idle && trainer.profileAvailable();
        editorButton.active = trainer.canEditProfiles();
        profileButton.setMessage(profileLabel());
        startButton.setMessage(new LiteralText(trainer.session().active() ? "Restoring…" : "Start"));
    }

    private void startTraining() {
        updateButtons();
        if (!startButton.active) return;
        client.openScreen(null);
        trainer.start();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (goalInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            startTraining();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void removed() { client.keyboard.enableRepeatEvents(false); }

    private LiteralText modeLabel() { return new LiteralText("Mode: " + TrainerHud.modeName(trainer.mode())); }
    private LiteralText profileLabel() {
        return new LiteralText(trainer.profileAvailable() ? "Profile: " + trainer.profile() : "Profile: none (retry)");
    }
    private LiteralText hintsLabel() { return new LiteralText("Target hints: " + (trainer.showHints() ? "ON" : "OFF")); }
    private int optionsTop() { return Math.max(30, height / 2 - 110); }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, "Hotbar Swap Trainer", width / 2, 15, 0xFFFFFF);
        textRenderer.drawWithShadow(matrices, "Successes:", width / 2 - 100, optionsTop() + 54, 0xFFFFFF);
        textRenderer.drawWithShadow(matrices, "0 = unlimited", width / 2 - 100, optionsTop() + 74, 0xDDDDBB);
        goalInput.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
        int textY = optionsTop() + 166;
        textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(
                new LiteralText("Profiles: config/hotbar-swap-trainer/profiles/*.json"), width - 24), 12, textY, 0xBBBBBB);
        TrainerProtocol.View view = trainer.session().get();
        if (!validGoal) {
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(
                    new LiteralText("Enter a whole number from 0 to 2147483647."), width - 24), 12, textY + 16, 0xFF8888);
        } else if (view != null && !view.message.isEmpty()) {
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(new LiteralText(view.message), width - 24),
                    12, textY + 16, view.phase.contains("ERROR") ? 0xFF8888 : 0xDDDDBB);
        }
        int messageY = textY + 30;
        for (String message : trainer.optionMessages()) {
            // Errors include repair instructions and must remain readable instead of being truncated.
            while (!message.isEmpty()) {
                String line = textRenderer.trimToWidth(message, Math.max(1, width - 24));
                if (line.isEmpty()) break;
                textRenderer.drawWithShadow(matrices, line, 12, messageY, 0xFFAA88);
                message = message.substring(line.length());
                messageY += 10;
            }
        }
    }

    // Restoration and its server acknowledgement must continue while this selector is open.
    @Override
    public boolean isPauseScreen() { return false; }
}
