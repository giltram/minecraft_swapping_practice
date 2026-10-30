package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.stats.PersonalBests;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import static dev.hotbarswaptrainer.client.EditorText.text;

/** Two mode PBs from an explicitly refreshed snapshot; history I/O and scoring stay outside the screen. */
final class TrainerHistoryScreen extends EditorFormScreen {
    private final Screen parent;
    private final HistoryBrowser browser;
    private final String[] times = new String[2];
    private int tableTop;

    TrainerHistoryScreen(Screen parent, HistoryBrowser browser) {
        super(text("personalBests")); this.parent = parent; this.browser = browser;
    }
    @Override protected void buildForm() {
        if (!browser.allowed()) { paragraph(text("gate"), 0xFFAA88); footer(text("back")); return; }
        if (browser.snapshot == null && !browser.refresh()) { footer(text("back")); return; }
        times[0] = best(TrainingMode.SINGLE_ITEM);
        times[1] = best(TrainingMode.FULL_INVENTORY);
        tableTop = cursor;
        cursor += 76;
        if (!browser.snapshot.errors.isEmpty()) paragraph(text("pbIncomplete"), 0xFFAA88);
        footer(text("back"));
    }
    private String best(TrainingMode mode) {
        return PersonalBests.fastestTaskForMode(browser.snapshot, mode)
                .map(best -> "PB " + TrainerHud.formatSeconds(best.nanos) + " s").orElse("PB —");
    }
    @Override protected ItemStack renderContents(MatrixStack matrices, int x, int y, float delta) {
        if (browser.snapshot == null || !browser.allowed()) return ItemStack.EMPTY;
        int left = viewport.left, top = viewport.y(tableTop), cellWidth = viewport.width / 2;
        for (int column = 0; column < 2; column++) {
            int cellLeft = left + column * cellWidth;
            for (int row = 0; row < 2; row++) {
                int cellTop = top + row * 34;
                fill(matrices, cellLeft, cellTop, cellLeft + cellWidth, cellTop + 34, 0xFF666666);
                fill(matrices, cellLeft + 1, cellTop + 1, cellLeft + cellWidth - 1, cellTop + 33, 0xFF292F39);
                String label = row == 0 ? TrainerHud.modeName(column == 0
                        ? TrainingMode.SINGLE_ITEM : TrainingMode.FULL_INVENTORY) : times[column];
                drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(label, cellWidth - 12),
                        cellLeft + cellWidth / 2, cellTop + 12, row == 0 ? 0xFFFFFF : 0x88FFAA);
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public void onClose() { client.openScreen(parent); }
    @Override public void tick() { super.tick(); if (!browser.allowed()) onClose(); }
}
