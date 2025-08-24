package com.hollafoil.recipegate.util.Gatherer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.List;

@SuppressWarnings("RedundantIfStatement")
public class OutputGatherer {
    public static List<ItemStack> process(Recipe<?> recipe) {
        List<ItemStack> vanillaItemStacks = handleVanilla(recipe);
        if (vanillaItemStacks != null) return vanillaItemStacks;

        List<ItemStack> createItemStacks = handleCreate(recipe);
        if (createItemStacks != null) return createItemStacks;

        // Unsupported recipe type
        return null;
    }

    public static List<ItemStack> handleVanilla(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe r) {
            return List.of(r.getResultItem(null));
        }

        else if (recipe instanceof AbstractCookingRecipe r) {
            return List.of(r.getResultItem(null));
        }

        else if (recipe instanceof StonecutterRecipe r) {
            return List.of(r.getResultItem(null));
        }

        else if (recipe instanceof SingleItemRecipe r) {
            return List.of(r.getResultItem(null));
        }

        return null;
    }

    public static List<ItemStack> handleCreate(Recipe<?> recipe) {
        return null;
    }
}
