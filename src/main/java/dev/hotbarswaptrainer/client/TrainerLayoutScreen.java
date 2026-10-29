package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileLoadResult;
import dev.hotbarswaptrainer.profile.SlotRule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/** Local profile editor. Never reads or changes a server session or the player's inventory. */
final class TrainerLayoutScreen extends Screen {
    private final Screen parent;
    private final TrainerClient trainer;
    private final LoadoutProfile profile;
    private final int[] sourcePositions = new int[LoadoutProfile.SLOT_COUNT];
    private SlotCandidateLayout layout;
    private ButtonWidget saveButton;
    private int selected = -1;
    private String error = "";

    TrainerLayoutScreen(Screen parent, TrainerClient trainer) {
        super(new LiteralText("Customize layout"));
        this.parent = parent;
        this.trainer = trainer;
        ProfileLoadResult loaded = trainer.profileRepository().loadAll();
        profile = loaded.getProfiles().stream().filter(value -> value.getId().equals(trainer.profile())).findFirst().orElse(null);
        if (profile == null) {
            error = "Profile unavailable. Repair its JSON and reopen this editor.";
            LogManager.getLogger("HotbarSwapTrainer").warn("Cannot edit profile {}: {}", trainer.profile(), loaded.getErrors());
        }
        resetOrder();
    }

    @Override
    protected void init() {
        rebuildLayout();
        int buttonWidth = Math.min(96, (width - 32) / 3);
        int start = width / 2 - (buttonWidth * 3 + 8) / 2;
        addButton(new ButtonWidget(start, height - 28, buttonWidth, 20, new LiteralText("Reset edits"), button -> resetOrder()));
        saveButton = addButton(new ButtonWidget(start + buttonWidth + 4, height - 28, buttonWidth, 20,
                new LiteralText("Save layout"), button -> save()));
        addButton(new ButtonWidget(start + 2 * (buttonWidth + 4), height - 28, buttonWidth, 20,
                new LiteralText("Cancel"), button -> onClose()));
        tick();
    }

    private void resetOrder() {
        Arrays.setAll(sourcePositions, index -> index);
        selected = -1;
        if (layout != null) rebuildLayout();
    }

    private void select(int position) {
        if (selected < 0) {
            selected = position;
        } else {
            int source = sourcePositions[selected];
            sourcePositions[selected] = sourcePositions[position];
            sourcePositions[position] = source;
            selected = -1;
            rebuildLayout();
        }
    }

    private void rebuildLayout() {
        int previousScroll = layout == null ? 0 : layout.scroll();
        int[] counts = new int[LoadoutProfile.SLOT_COUNT];
        if (profile != null) {
            for (int slot = 0; slot < counts.length; slot++) {
                counts[slot] = profile.getSlots().get(sourcePositions[slot]).getAlternatives().size();
            }
        }
        layout = new SlotCandidateLayout(width, height, counts);
        layout.setScroll(previousScroll);
    }

    private void save() {
        if (profile == null || trainer.session().active() || !client.isInSingleplayer()) return;
        try {
            trainer.profileRepository().saveSlotOrder(profile, sourcePositions);
            client.openScreen(parent);
        } catch (Exception failure) {
            error = "Save failed: " + failure.getMessage();
            LogManager.getLogger("HotbarSwapTrainer").error("Cannot save profile layout", failure);
        }
    }

    @Override
    public void tick() {
        if (saveButton != null) saveButton.active = profile != null && !trainer.session().active() && client.isInSingleplayer();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        line(matrices, "Customize layout · " + trainer.profile(), 12, 0xFFFFFF);
        line(matrices, "Click two slots to exchange. Scroll to see all slots.", 30, 0xDDDDBB);
        ItemStack hovered = ItemStack.EMPTY;
        double scale = client.getWindow().getScaleFactor();
        int bottomPixels = (int) Math.round(layout.bottom * scale);
        int topPixels = (int) Math.round(layout.top * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, client.getWindow().getFramebufferHeight() - bottomPixels,
                client.getWindow().getFramebufferWidth(), bottomPixels - topPixels);
        try {
            if (profile != null) {
                for (int slot = 0; slot < LoadoutProfile.SLOT_COUNT; slot++) {
                    if (!layout.visible(slot)) continue;
                    SlotRule rule = profile.getSlots().get(sourcePositions[slot]);
                    int y = layout.rowTop(slot);
                    int border = selected == slot ? 0xFFFFFF55 : layout.slotAt(mouseX, mouseY) == slot ? 0xFFFFFFFF : 0xFF888888;
                    fill(matrices, layout.left, y, layout.left + layout.width, y + layout.rowHeight(slot), border);
                    fill(matrices, layout.left + 1, y + 1, layout.left + layout.width - 1,
                            y + layout.rowHeight(slot) - 1, 0xFF404040);
                    String label = TrainerHud.slotName(slot) + " · " + rule.getKind() + " · "
                            + rule.getAlternatives().size() + " candidates";
                    textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(new LiteralText(label), layout.width - 16),
                            layout.left + 8, y + 6, 0xFFFFFF);
                    if (rule.getAlternatives().isEmpty()) {
                        textRenderer.drawWithShadow(matrices, "Empty", layout.left + 8, y + 24, 0xBBBBBB);
                    }
                    int hoveredCandidate = layout.candidateAt(slot, mouseX, mouseY);
                    for (int candidate = 0; candidate < rule.getAlternatives().size(); candidate++) {
                        int iconY = layout.candidateY(slot, candidate);
                        if (iconY + 20 <= layout.top || iconY >= layout.bottom) continue;
                        int iconX = layout.candidateX(candidate);
                        ItemStack stack = InventoryService.toItemStack(rule.getAlternatives().get(candidate).getTemplate());
                        client.getItemRenderer().renderInGuiWithOverrides(stack, iconX, iconY);
                        client.getItemRenderer().renderGuiItemOverlay(textRenderer, stack, iconX, iconY);
                        if (candidate == hoveredCandidate) hovered = stack;
                    }
                }
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        drawScrollbar(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        line(matrices, selected < 0 ? "Hover any candidate for item details." :
                "Selected: " + TrainerHud.slotName(selected) + ". Click another slot to exchange.", height - 66, 0xFFFFAA);
        line(matrices, "Both modes use this profile. Whole rules move together.", height - 54, 0xBBBBBB);
        line(matrices, error.isEmpty() ? "Cancel / Esc discards edits. Saving keeps a .bak of the original JSON." : error,
                height - 43, error.isEmpty() ? 0xBBBBBB : 0xFF8888);
        if (!hovered.isEmpty()) renderTooltip(matrices, hovered, mouseX, mouseY);
    }

    private void drawScrollbar(MatrixStack matrices) {
        if (layout.maximumScroll() == 0) return;
        int trackHeight = layout.bottom - layout.top;
        int thumbHeight = Math.min(trackHeight, Math.max(12, trackHeight * trackHeight / layout.contentHeight()));
        int thumbY = layout.top + (trackHeight - thumbHeight) * layout.scroll() / layout.maximumScroll();
        int x = layout.left + layout.width + 4;
        fill(matrices, x, layout.top, x + 4, layout.bottom, 0xFF222222);
        fill(matrices, x, thumbY, x + 4, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout.contains(mouseX, mouseY)) {
            layout.setScroll(layout.scroll() - (int) Math.round(amount * 32));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = layout.slotAt(mouseX, mouseY);
        if (button == 0 && slot >= 0 && profile != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            select(slot);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void line(MatrixStack matrices, String text, int y, int color) {
        textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(new LiteralText(text), width - 24), 12, y, color);
    }

    @Override public void onClose() { client.openScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
