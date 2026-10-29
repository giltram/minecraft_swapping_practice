package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

import java.util.Locale;

final class TrainerHud {
    private final TrainerClient trainer;

    TrainerHud(TrainerClient trainer) { this.trainer = trainer; }

    void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        trainer.checkInterruption();
        TrainerProtocol.View view = trainer.session().get();
        if (view == null) return;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        TextRenderer font = client.textRenderer;
        String progress = view.completed + "/" + (view.goal == 0 ? "∞" : view.goal);
        line(matrices, font, modeName(view.mode) + " · " + view.profileId + " · " + progress + " · " + view.phase,
                8, 8, width - 16, 0xFFFFFF);
        line(matrices, font, view.message, 8, 20, width - 16,
                view.phase.contains("ERROR") || "INVALID".equals(view.phase) ? 0xFF8888 : 0xDDDDBB);
        if (view.isActive() || view.completed > 0) {
            line(matrices, font, formatSeconds(trainer.session().displayElapsedNanos()) + " s  |  swaps "
                            + view.swapAttempts + " (effective " + view.effectiveSwaps + ")  |  selections " + view.slotChanges,
                    8, 32, width - 16, 0xFFFFFF);
            line(matrices, font, "Mistakes " + (view.noOpSwaps + view.forbiddenActions) + "  (no-op "
                            + view.noOpSwaps + ", forbidden " + view.forbiddenActions + ")",
                    8, 44, width - 16, 0xBBBBBB);
        }
        if (trainer.showHints() && view.target != null && view.isActive()) {
            int targetY = Math.max(78, height - 94);
            line(matrices, font, view.mode == TrainingMode.SINGLE_ITEM
                            ? "Destination: " + slotName(view.destination) : "One valid target · rules below",
                    8, targetY - 14, width - 16, 0xFFFFAA);
            drawTargets(matrices, client, view, width / 2, targetY);
            int selected = client.player.inventory.selectedSlot;
            String hotbarRule = selected < view.slotHints.size() ? view.slotHints.get(selected) : "";
            String offhandRule = view.slotHints.size() > 9 ? view.slotHints.get(9) : "";
            line(matrices, font, hotbarRule, 8, targetY + 30, width - 16, 0xDDDDBB);
            line(matrices, font, offhandRule, 8, targetY + 42, width - 16, 0xDDDDBB);
        }
        if ("COUNTDOWN".equals(view.phase) || "GO".equals(view.phase)) {
            String text = "GO".equals(view.phase) ? "GO" : Integer.toString(Math.max(1, (view.countdownTicks + 19) / 20));
            matrices.push();
            matrices.translate(width / 2.0, height / 2.0 - 20, 0);
            matrices.scale(2, 2, 1);
            font.drawWithShadow(matrices, text, -font.getWidth(text) / 2f, 0, 0xFFFF55);
            matrices.pop();
            if ("GO".equals(view.phase)) trainer.afterGoRendered();
        }
    }

    private static void drawTargets(MatrixStack matrices, MinecraftClient client, TrainerProtocol.View view, int center, int y) {
        for (int i = 0; i < 10; i++) {
            int x = i == 9 ? center - 120 : center - 90 + i * 20;
            DrawableHelper.fill(matrices, x - 1, y - 1, x + 19, y + 21, 0xB0000000);
            if (i == view.destination) DrawableHelper.fill(matrices, x - 1, y - 1, x + 19, y + 1, 0xFFFFFF55);
            ItemStack stack = InventoryService.toItemStack(view.target.get(i));
            client.getItemRenderer().renderInGuiWithOverrides(stack, x + 1, y + 1);
            client.getItemRenderer().renderGuiItemOverlay(client.textRenderer, stack, x + 1, y + 1);
            client.textRenderer.drawWithShadow(matrices, i == 9 ? "O" : Integer.toString(i + 1), x + 6, y + 20, 0xBBBBBB);
        }
    }

    private static void line(MatrixStack matrices, TextRenderer font, String text, int x, int y, int width, int color) {
        font.drawWithShadow(matrices, font.trimToWidth(new LiteralText(text), Math.max(1, width)), x, y, color);
    }

    static String formatSeconds(long nanos) { return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000_000.0); }
    static String modeName(TrainingMode mode) { return mode == TrainingMode.SINGLE_ITEM ? "Single Item" : "Full Inventory"; }
    static String slotName(int slot) { return slot == 9 ? "Offhand" : slot >= 0 ? "Hotbar " + (slot + 1) : "—"; }
}
