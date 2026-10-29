package dev.hotbarswaptrainer.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

final class TrainerKeyBindings {
    final KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hotbar-swap-trainer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.hotbar-swap-trainer"));
    final KeyBinding options = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hotbar-swap-trainer.options", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.hotbar-swap-trainer"));
}
