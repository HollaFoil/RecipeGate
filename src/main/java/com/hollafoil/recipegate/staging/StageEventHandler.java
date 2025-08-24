package com.hollafoil.recipegate.staging;// StageGrantingHandler.java

import com.alessandro.astages.event.custom.actions.StageAddedPlayerEvent;
import com.hollafoil.recipegate.RecipeGate;
import com.hollafoil.recipegate.util.Gatherer.OutputGatherer;
import com.ibm.icu.util.Output;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.alessandro.astages.integration.kubejs.AStagesKubeJSUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StageEventHandler {

    private final ThreadLocal<Set<String>> processingStages = ThreadLocal.withInitial(HashSet::new);
    private final DataStore dataStore;

    public StageEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @SubscribeEvent
    public void onStageAdded(StageAddedPlayerEvent event) {
        if (processingStages.get().contains(event.stage) || dataStore == null) {
            return;
        }

        try {
            processingStages.get().add(event.stage);

            String stage = event.stage;
            Player player = event.getEntity();

            ResourceLocation itemId = dataStore.getStagedItem(stage);
            if (itemId != null) {
                handleItemStage(itemId, player);
            }

            ResourceLocation recipeId = dataStore.getStagedRecipe(stage);
            if (recipeId != null) {
                handleRecipeStage(stage, player, recipeId);
            }
        } finally {
            processingStages.get().remove(event.stage);
        }
    }

    private void handleItemStage(ResourceLocation itemId, Player player) {
        Set<ResourceLocation> recipesThatUseThis = dataStore.getRecipesUsingItem(itemId);

        if (recipesThatUseThis != null) {
            for (ResourceLocation recipeId : recipesThatUseThis) {
                Map<ResourceLocation, Integer> itemToGroupId = dataStore.getIngredientGroupsForItem(itemId);
                if (itemToGroupId == null) continue;

                Integer groupId = itemToGroupId.get(recipeId);
                if (groupId == null) continue;

                String recipeStageId = "recipe/" + recipeId.getPath() + "/group_" + groupId;

                RecipeGate.getLogger().info("Granting recipe stage {} for item {}", recipeStageId, itemId);
                AStagesKubeJSUtil.addStageToPlayer(recipeStageId, player);
            }
        }
    }

    private void handleRecipeStage(String stageId, Player player, ResourceLocation recipeId) {
        // Parse the recipe ID and group ID from the stage string
        int lastGroupIndex = stageId.lastIndexOf("/group_");
        if (lastGroupIndex == -1) return;
        if (recipeId == null) return;

        int groupId = Integer.parseInt(stageId.substring(lastGroupIndex + 7));

        // Get the current unlocked count for this recipe and group
        Map<Integer, Integer> unlockedCounts = dataStore.getCurrentIngredientGroupsLeftForRecipe(recipeId);

        // Increment the count for the ingredient group that was just unlocked
        unlockedCounts.compute(groupId, (k, v) -> v == null ? 1 : v + 1);

        // Get the total required ingredients for this recipe and group
        Map<Integer, Integer> totalCounts = dataStore.getIngredientGroupsForRecipe(recipeId);
        if (totalCounts == null) return;

        // Check if all ingredients for this recipe are now unlocked
        boolean allIngredientsUnlocked = true;
        for (Map.Entry<Integer, Integer> entry : totalCounts.entrySet()) {
            Integer total = entry.getValue();
            Integer unlocked = unlockedCounts.getOrDefault(entry.getKey(), 0);
            if (unlocked < total) {
                allIngredientsUnlocked = false;
                break;
            }
        }

        // If all ingredients are unlocked, grant the output stage
        if (allIngredientsUnlocked) {
            dataStore.grantRecipeStage(recipeId, player);
        }
    }
}