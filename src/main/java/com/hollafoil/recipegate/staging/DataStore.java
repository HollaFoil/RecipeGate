package com.hollafoil.recipegate.staging;

import com.alessandro.astages.integration.kubejs.AStagesKubeJSUtil;
import com.hollafoil.recipegate.RecipeGate;
import com.hollafoil.recipegate.util.Gatherer.IngredientGatherer;
import com.hollafoil.recipegate.util.Gatherer.OutputGatherer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {
    private final Map<ResourceLocation, Recipe<?>> recipes = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> recipesByIngredient = new HashMap<>();
    private final Map<ResourceLocation, Integer> recipesLeft = new HashMap<>();
    private final Set<String> recipeRestrictions = new HashSet<>();
    private final Map<ResourceLocation, Map<ResourceLocation, Integer>> ingredientToRecipeGroup = new HashMap<>();
    private final Map<ResourceLocation, Map<Integer, Integer>> ingredientGroupCountByRecipe = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Map<Integer, Integer>> totalIngredientsInRecipe = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> recipesByOutput = new HashMap<>();
    private final Map<String, ResourceLocation> stageToItem = new HashMap<>();
    private final Map<String, ResourceLocation> stageToRecipe = new HashMap<>();
    private final Map<ResourceLocation, Set<String>> itemToStages = new HashMap<>();

    public DataStore() {}

    public DataStore(RecipeManager recipeManager, MinecraftServer server) {
        init(recipeManager, server);
    }

    public void init(RecipeManager recipeManager, MinecraftServer server) {
        clear();
        gatherRecipeSets(recipeManager);
        resyncStages(server);
    }

    public void init(RecipeManager recipeManager) {
        clear();
        gatherRecipeSets(recipeManager);
    }

    public Recipe<?> getRecipe(ResourceLocation recipeId) {
        return recipes.get(recipeId);
    }

    public Set<ResourceLocation> getRecipesUsingItem(ResourceLocation item) {
        return recipesByIngredient.get(item);
    }

    public Map<ResourceLocation, Integer> getIngredientGroupsForItem(ResourceLocation item) {
        return ingredientToRecipeGroup.get(item);
    }

    public Map<Integer, Integer> getCurrentIngredientGroupsLeftForRecipe(ResourceLocation recipe) {
        return ingredientGroupCountByRecipe.computeIfAbsent(recipe, totalIngredientsInRecipe::get);
    }

    public Map<Integer, Integer> getIngredientGroupsForRecipe(ResourceLocation recipe) {
        return totalIngredientsInRecipe.get(recipe);
    }

    public Boolean lockRecipeForOutputItem(ResourceLocation outputItemId) {
        if (outputItemId == null) return false;
        int count = recipesLeft.getOrDefault(outputItemId, 0) - 1;
        recipesLeft.put(outputItemId, count);
        return count <= 0;
    }

    public Boolean stageItem(ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return false;
        String stage = "item/" + itemId;

        RecipeGate.getLogger().info("ITEM: Restricting {} via stage {}", itemId, stage);
        stageToItem.put(stage, itemId);
        itemToStages.computeIfAbsent(itemId, k -> new HashSet<>()).add(stage);
        AStagesKubeJSUtil.addRestrictionForItem(stage, stage, item).setCanBePlaced(false);
        return true;
    }

    public Boolean stageRecipe(ResourceLocation recipeId, Integer groupId) {
        Recipe<?> recipe = getRecipe(recipeId);
        if (recipe == null) {
            RecipeGate.getLogger().warn("Attempted to stage non-existent recipe: {}", recipeId);
            return false;
        }

        String stage = "recipe/" + recipe.getId().getPath() + "/group_" + groupId;
        recipeRestrictions.add(stage);
        stageToRecipe.put(stage, recipe.getId());

        RecipeGate.getLogger().info("RECIPE: Restricting {} via group {}", recipe.getId(), groupId);
        AStagesKubeJSUtil.addRestrictionForRecipe(
                stage,
                stage,
                recipe.getType(),
                recipe.getId()
        );
        return true;
    }

    public Boolean isRecipeRestricted(String recipeStage) {
        return recipeRestrictions.contains(recipeStage);
    }

    public Boolean isRecipeRestricted(ResourceLocation recipeId, int groupId) {
        String stageId = recipeId.getPath() + "_group_" + groupId;
        return isRecipeRestricted(stageId);
    }

    public ResourceLocation getStagedItem(String stage) {
        return stageToItem.get(stage);
    }

    public Set<String> getStagesUsingItem(ResourceLocation item) {
        return itemToStages.get(item);
    }

    public ResourceLocation getStagedRecipe(String stage) {
        return stageToRecipe.get(stage);
    }

    public void resyncStages(MinecraftServer server) {
        resync(AStagesKubeJSUtil.getServerData(server).get());
    }

    public void resyncStages(Player player) {
        resync(AStagesKubeJSUtil.getStagesFromPlayer(player));
    }

    public void grantRecipeStage(ResourceLocation recipeId, Player player) {
        Recipe<?> recipe = getRecipe(recipeId);
        if (recipe == null) return;
        List<ItemStack> outputs = OutputGatherer.process(recipe);
        for (ItemStack output : outputs) {
            if (output.isEmpty()) continue;

            ResourceLocation outputItemId = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (outputItemId == null) continue;

            Set<String> itemStages = getStagesUsingItem(outputItemId);
            if (itemStages == null) continue;

            for (String itemStage : itemStages) {
                RecipeGate.getLogger().info("Granting stage {} for output of recipe {}", itemStage, recipeId);
                AStagesKubeJSUtil.addStageToPlayer(itemStage, player);
            }
        }
    }

    private void clear() {
        recipes.clear();
        recipesByIngredient.clear();
        recipesLeft.clear();
        ingredientToRecipeGroup.clear();
        ingredientGroupCountByRecipe.clear();
        totalIngredientsInRecipe.clear();
    }

    private void clearDynamicStagingData() {
        stageToItem.clear();
        stageToRecipe.clear();
        recipeRestrictions.clear();
        itemToStages.clear();
        recipesLeft.clear();
        ingredientGroupCountByRecipe.clear();
    }

    private void gatherRecipeSets(RecipeManager recipeManager) {
        RecipeGate.getLogger().info("Collecting all recipes by ingredients");
        recipeManager.getRecipes().forEach(this::gatherRecipe);;
    }

    private void gatherRecipe(Recipe<?> recipe) {
        RecipeGate.getLogger().info("Gathering recipe {}...", recipe.getId());
        ResourceLocation recipeId = recipe.getId();
        recipes.put(recipeId, recipe);

        gatherIngredients(recipe);
        gatherOutputs(recipe);
    }

    private void gatherIngredients(Recipe<?> recipe) {
        List<Ingredient> ingredients = IngredientGatherer.process(recipe);
        if (ingredients == null) return;
        List<Ingredient> uniqueIngredients = reduceIngredients(ingredients);

        Map<Integer, Integer> groupCounts = new ConcurrentHashMap<>();
        Map<Integer, Integer> groupCountsCopy = new ConcurrentHashMap<>();
        int ingredientCount = 0;

        for (Ingredient ingredient : uniqueIngredients) {
            for (ItemStack itemStack : ingredient.getItems()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
                if (itemId != null) {
                    recipesByIngredient.computeIfAbsent(itemId, k -> new HashSet<>()).add(recipe.getId());
                    ingredientToRecipeGroup.computeIfAbsent(itemId, k -> new HashMap<>()).put(recipe.getId(), ingredientCount);
                }
            }
            groupCounts.put(ingredientCount, ingredient.getItems().length);
            groupCountsCopy.put(ingredientCount, ingredient.getItems().length);
            ingredientCount++;
        }
        ingredientGroupCountByRecipe.put(recipe.getId(), groupCounts);
        totalIngredientsInRecipe.put(recipe.getId(), groupCountsCopy);
    }

    private void gatherOutputs(Recipe<?> recipe) {
        List<ItemStack> outputs = OutputGatherer.process(recipe);
        for (ItemStack output : outputs) {
            if (!output.isEmpty()) {
                ResourceLocation outputItemId = ForgeRegistries.ITEMS.getKey(output.getItem());
                if (outputItemId != null) {
                    recipesLeft.put(outputItemId, recipesLeft.getOrDefault(outputItemId, 0) + 1);
                    recipesByOutput.computeIfAbsent(outputItemId, k -> new HashSet<>()).add(recipe.getId());
                }
            }
        }
    }

    private void resync(List<String> stageIds) {
        clearDynamicStagingData();
        for (String stageId : stageIds) {
            if (stageId.startsWith("item/")) {
                ResourceLocation itemId = ResourceLocation.tryParse(stageId.substring(5));
                if (itemId != null) {
                    stageToItem.put(stageId, itemId);
                    itemToStages.computeIfAbsent(itemId, k -> new HashSet<>()).add(stageId);
                }
            } else if (stageId.startsWith("recipe/")) {
                recipeRestrictions.add(stageId);
                String[] parts = stageId.substring(7).split("/group_");
                if (parts.length == 2) {
                    ResourceLocation recipeId = ResourceLocation.tryParse(parts[0]);
                    if (recipeId != null) {
                        stageToRecipe.put(stageId, recipeId);
                    }
                }
            }
        }

        for (String stageId : recipeRestrictions) {
            ResourceLocation recipeId = getStagedRecipe(stageId);
            if (recipeId != null) {
                Recipe<?> recipe = getRecipe(recipeId);
                if (recipe != null) {
                    // Update recipesLeft
                    if (recipe.getType().equals(RecipeType.CRAFTING)) {
                        ItemStack outputStack = recipe.getResultItem(null);
                        if (!outputStack.isEmpty()) {
                            ResourceLocation outputItemId = ForgeRegistries.ITEMS.getKey(outputStack.getItem());
                            if (outputItemId != null) {
                                recipesLeft.put(outputItemId, recipesLeft.getOrDefault(outputItemId, 0) - 1);
                            }
                        }
                    }

                    // Update ingredientGroupCountByRecipe
                    String[] parts = stageId.substring(7).split("/group_");
                    if (parts.length == 2) {
                        try {
                            int groupId = Integer.parseInt(parts[1]);
                            ingredientGroupCountByRecipe.get(recipeId).remove(groupId);
                        } catch (NumberFormatException e) {
                            RecipeGate.getLogger().warn("Failed to parse group ID from stage: {}", stageId);
                        }
                    }
                }
            }
        }
    }

    private static List<Ingredient> reduceIngredients(List<Ingredient> ingredients) {
        List<Ingredient> unique = new ArrayList<>();
        for (Ingredient current : ingredients) {
            boolean found = false;
            for (Ingredient existing : unique) {
                if (doIngredientsMatch(existing, current)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                unique.add(current);
            }
        }
        return unique;
    }

    private static boolean doIngredientsMatch(Ingredient ing1, Ingredient ing2) {
        if (ing1.isEmpty() && ing2.isEmpty()) return true;
        if (ing1.getItems().length != ing2.getItems().length) return false;

        Set<ResourceLocation> items1 = new HashSet<>();
        for (ItemStack stack : ing1.getItems()) {
            items1.add(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }

        Set<ResourceLocation> items2 = new HashSet<>();
        for (ItemStack stack : ing2.getItems()) {
            items2.add(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }

        return items1.equals(items2);
    }
}
