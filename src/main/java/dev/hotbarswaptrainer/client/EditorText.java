package dev.hotbarswaptrainer.client;

import net.minecraft.client.resource.language.I18n;

/** Localized copy for the configuration pages. */
final class EditorText {
    static String text(String key, Object... args) { return I18n.translate("trainer.config." + key, args); }
    static String slot(int index) { return index == 9 ? text("offhand") : text("hotbar", index + 1); }
    private EditorText() { }
}
