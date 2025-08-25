package com.hollafoil.recipegate.staging; // New package for data analysis

import com.hollafoil.recipegate.RecipeGate;
import com.hollafoil.recipegate.util.Gatherer.OutputGatherer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Restrictions {
    private final DataStore dataStore;


    private int countOfRestrictions = 0;

    public Restrictions(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    private void stageItem(ResourceLocation itemId, boolean initial) {
        if (dataStore.stageItem(itemId, initial)) countOfRestrictions++;
    }

    private void stageItemCascade(ResourceLocation itemId) {
        RecipeGate.getLogger().info("Cascading initially with {}", itemId);
        stageItem(itemId, true);
        cascadeRecipesIterative(itemId);
    }

    private void stageRecipe(ResourceLocation recipeId, int groupId) {
        if (dataStore.stageRecipe(recipeId, groupId)) countOfRestrictions++;;
    }

    private boolean isRecipeRestricted(ResourceLocation recipeId, int groupId) {
        return dataStore.isRecipeRestricted(recipeId, groupId);
    }

    private void cascadeRecipesIterative(ResourceLocation initialItem) {
        List<ResourceLocation> stack = new ArrayList<>();
        Set<ResourceLocation> processed = new HashSet<>();
        stack.add(initialItem);
        processed.add(initialItem);

        while (!stack.isEmpty()) {
            ResourceLocation currentItem = stack.remove(stack.size() - 1);

            Set<ResourceLocation> recipesUsingCurrentItem = dataStore.getRecipesUsingItem(currentItem);
            if (recipesUsingCurrentItem == null) continue;

            for (ResourceLocation recipeId : recipesUsingCurrentItem) {
                Recipe<?> recipe = dataStore.getRecipe(recipeId);
                if (recipe == null) continue;

                Map<ResourceLocation, Integer> itemToGroupId = dataStore.getIngredientGroupsForItem(currentItem);
                if (itemToGroupId == null) continue;

                Integer ingredientId = itemToGroupId.get(recipeId);
                if (ingredientId == null) throw new IllegalStateException("ingredientId was null");; // Should not happen

                if (isRecipeRestricted(recipeId, ingredientId)) continue;

                Map<Integer, Integer> groupCounts = dataStore.getCurrentIngredientGroupsLeftForRecipe(recipeId);
                if (groupCounts == null) throw new IllegalStateException("groupCount was null"); // Should not happen

                int currentIngredientCount = groupCounts.getOrDefault(ingredientId, 0);
                groupCounts.put(ingredientId, currentIngredientCount - 1);

                if (currentIngredientCount - 1 > 0) continue;

                stageRecipe(recipeId, ingredientId);
                List<ItemStack> outputs = OutputGatherer.process(recipe);
                for (ItemStack output : outputs) {
                    if (!output.isEmpty()) {
                        ResourceLocation outputItemId = ForgeRegistries.ITEMS.getKey(output.getItem());
                        if (dataStore.lockRecipeForOutputItem(outputItemId)) {
                            if (processed.contains(outputItemId) || outputItemId == null) continue;
                            stageItem(outputItemId, false);
                            stack.add(outputItemId);
                            processed.add(outputItemId);
                        }
                    }
                }
            }
        }
    }

    public void addInitialRestrictions() {
        RecipeGate.getLogger().info("Starting recipe and tag analysis in memory.");

        stageItemCascade(ResourceLocation.parse("minecraft:coal"));
        stageItemCascade(ResourceLocation.parse("minecraft:charcoal"));
        stageItemCascade(ResourceLocation.parse("minecraft:iron_ingot"));
        stageItemCascade(ResourceLocation.parse("minecraft:gold_ingot"));
        stageItemCascade(ResourceLocation.parse("minecraft:redstone"));
        stageItemCascade(ResourceLocation.parse("minecraft:stone"));
        stageItemCascade(ResourceLocation.parse("minecraft:cobblestone"));
        stageItemCascade(ResourceLocation.parse("minecraft:gold_ingot"));
        stageItemCascade(ResourceLocation.parse("minecraft:oak_door"));

        RecipeGate.getLogger().info("Finished analysis. Total restrictions: {}", countOfRestrictions);
    }

    public void addItemRestriction(ResourceLocation item) {
        stageItemCascade(item);
    }
}