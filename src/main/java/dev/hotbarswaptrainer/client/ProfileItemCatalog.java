package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.profile.StackValue;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registry/search/preview adapter. Every displayed stack is newly created, never borrowed from a player. */
final class ProfileItemCatalog {
    static final class Choice {
        final String id, name;
        final ItemStack icon;
        Choice(String id, String name, ItemStack icon) { this.id = id; this.name = name; this.icon = icon; }
        boolean matches(String query) {
            String needle = query.toLowerCase(Locale.ROOT);
            return id.toLowerCase(Locale.ROOT).contains(needle) || name.toLowerCase(Locale.ROOT).contains(needle);
        }
    }
    static List<Choice> items() {
        List<Choice> items = new ArrayList<>();
        for (Item item : Registry.ITEM) if (item != Items.AIR) {
            ItemStack stack = new ItemStack(item);
            items.add(new Choice(Registry.ITEM.getId(item).toString(), stack.getName().getString(), stack));
        }
        return items;
    }
    static ItemStack preview(ProfileDraft.Candidate candidate) {
        return preview(candidate, null);
    }
    static ItemStack preview(ProfileDraft.Candidate candidate, StackValue validatedTemplate) {
        if (validatedTemplate != null) return InventoryService.toItemStack(validatedTemplate);
        try {
            Item item = Registry.ITEM.get(new Identifier(candidate.getItem()));
            // Raw SNBT must go through the backend's depth/size checks before any parsing/materialization.
            // Invalid drafts still have safe base icons; no preview can crash on deeply nested input.
            return new ItemStack(item, Math.max(1, candidate.getCount()));
        } catch (RuntimeException invalidItem) { return ItemStack.EMPTY; }
    }
    private ProfileItemCatalog() { }
}
