package com.hollafoil.recipegate.util.Gatherer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("RedundantIfStatement")
public class IngredientGatherer {
    public static List<Ingredient> process(Recipe<?> recipe) {
        List<Ingredient> vanillaIngredients = handleVanilla(recipe);
        if (vanillaIngredients != null) return vanillaIngredients;

        List<Ingredient> createIngredients = handleCreate(recipe);
        if (createIngredients != null) return createIngredients;

        // Unsupported recipe type
        return null;
    }

    public static List<Ingredient> handleVanilla(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe r) {
            return r.getIngredients();
        }

        else if (recipe instanceof AbstractCookingRecipe r) {
            return r.getIngredients();
        }

        else if (recipe instanceof StonecutterRecipe r) {
            return r.getIngredients();
        }

        else if (recipe instanceof SingleItemRecipe r) {
            return r.getIngredients();
        }

        return null;
    }

    public static List<Ingredient> handleCreate(Recipe<?> recipe) {
        return null;
    }
}