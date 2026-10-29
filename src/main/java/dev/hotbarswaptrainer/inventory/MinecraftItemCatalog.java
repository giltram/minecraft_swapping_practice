package dev.hotbarswaptrainer.inventory;

import dev.hotbarswaptrainer.profile.ItemCatalog;
import dev.hotbarswaptrainer.profile.StackValue;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** Runtime registry/materialization boundary; domain generation never accesses game registries. */
public final class MinecraftItemCatalog implements ItemCatalog {
    @Override public int maximumStackSize(String itemId) {
        Identifier id = new Identifier(itemId);
        if (!Registry.ITEM.containsId(id) || Registry.ITEM.get(id) == Items.AIR) return 0;
        return Registry.ITEM.get(id).getMaxCount();
    }

    @Override public StackValue normalizeTemplate(StackValue value) {
        return InventoryService.fromStack(InventoryService.toItemStack(value));
    }
}
