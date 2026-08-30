package net.lmor.botanicalextramachinery.blocks.base;

import com.google.common.collect.Streams;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.lib.BotaniaTags;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class RecipeTile<T extends Recipe<Container>> extends ExtraBotanicalTile {
    private final RecipeType<T> recipeType;
    private final int firstInputSlot;
    private final int firstOutputSlot;
    protected T recipe;
    private boolean needsRecipeUpdate;
    private final int countCraft;
    private int countCraftPerRecipe;
    /**
     * Transient cache for simple ingredient item arrays for the currently selected recipe.
     * If an entry is null, the corresponding Ingredient is not simple and Ingredient.test() must be used.
     */
    private transient List<Item[]> cachedIngredientItems = null;


    public RecipeTile(BlockEntityType<?> blockEntityType, RecipeType<T> recipeType, BlockPos pos, BlockState state, int manaCap, int firstInputSlot, int firstOutputSlot, int countCraft) {
        super(blockEntityType, pos, state, manaCap);
        this.recipeType = recipeType;
        this.firstInputSlot = firstInputSlot;
        this.firstOutputSlot = firstOutputSlot;
        this.needsRecipeUpdate = true;
        this.countCraft = countCraft;
        this.countCraftPerRecipe = countCraft;
    }

    public int getCountCraft() {
        return countCraftPerRecipe;
    }

    protected void updateRecipeIfNeeded() {
        this.updateRecipeIfNeeded(() -> {
        }, (stack, slot) -> {
        });
    }

    protected void updateRecipeIfNeeded(Runnable doUpdate, BiConsumer<ItemStack, Integer> usedStacks) {
        if (this.level != null && !this.level.isClientSide) {
            if (this.needsRecipeUpdate) {
                this.needsRecipeUpdate = false;
                doUpdate.run();
                this.updateRecipe(usedStacks);
            }

        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void updateRecipe(BiConsumer<ItemStack, Integer> usedStacks) {
        if (this.level != null && !this.level.isClientSide) {
            if (!this.canMatchRecipes()) {
                this.recipe = null;
            } else {
                IAdvancedItemHandlerModifiable inventory = this.getInventory().getUnrestricted();
                IntStream range = IntStream.range(this.firstInputSlot, this.firstOutputSlot);
                Objects.requireNonNull(inventory);
                List<ItemStack> stacks = range.mapToObj(inventory::getStackInSlot).toList();

                Iterator iterator = this.level.getRecipeManager().getAllRecipesFor(this.recipeType).iterator();

                Recipe recipe;
                do {
                    if (!iterator.hasNext()) {
                        this.recipe = null;
                        return;
                    }

                    recipe = (Recipe)iterator.next();
                } while(this.matchRecipe((T) recipe, stacks));

                // Build a simple per-ingredient item array cache for fast matching when possible
                List<Ingredient> ingredients = recipe.getIngredients();
                int nIngredients = ingredients.size();
                this.cachedIngredientItems = new ArrayList<>(nIngredients);
                for (int ingIdx = 0; ingIdx < nIngredients; ingIdx++) {
                    Ingredient ing = ingredients.get(ingIdx);
                    ItemStack[] items = ing.getItems();
                    if (items.length == 0) {
                        this.cachedIngredientItems.add(null);
                        continue;
                    }
                    boolean simple = true;
                    Item[] arr = new Item[items.length];
                    for (int i = 0; i < items.length; i++) {
                        ItemStack is = items[i];
                        if (is == null || is.hasTag()) {
                            simple = false;
                            break;
                        }
                        arr[i] = is.getItem().asItem();
                    }
                    this.cachedIngredientItems.add(simple ? arr : null);
                }

                List<ItemStack> consumedStacks = new ArrayList<>();

                this.countCraftPerRecipe = maxCountCraft(recipe.getIngredients().iterator());

                // DISABLED: Output space check - let craftRecipe handle the actual extraction
                // This can cause issues if it reduces countCraftPerRecipe incorrectly

                if (recipe.getType() == BotaniaRecipeTypes.RUNE_TYPE) {
                    // collect all ingredient items and check for runes
                    List<ItemStack> inputItemRes = new ArrayList<>();
                    for (int ingIdx = 0; ingIdx < nIngredients; ingIdx++) {
                        Ingredient ingredient = ingredients.get(ingIdx);
                        inputItemRes.addAll(Arrays.stream(ingredient.getItems()).toList());
                    }

                    List<ItemStack> res = Streams.concat(new Stream[]{
                            inputItemRes.stream()
                                    .filter(s -> s.is(BotaniaTags.Items.RUNES))
                                    .map(ItemStack::copy)
                    }).toList();

                    if (!res.isEmpty()){
                        int emptySlot = 0;
                        for (int slot = this.firstOutputSlot; slot < inventory.getSlots(); ++slot) {
                            ItemStack slotItem = inventory.getStackInSlot(slot);
                            if (slotItem.isEmpty()){
                                emptySlot++;
                            }
                        }
                        if (emptySlot == 0 || emptySlot < res.size() + 1){
                            this.recipe = null;
                            return;
                        }
                    }
                }

                // Match ingredients using index-based loops and the cachedIngredientItems fast-path
                for (int ingIdx = 0; ingIdx < nIngredients; ingIdx++) {
                    Ingredient ingredient = ingredients.get(ingIdx);
                    Item[] simple = this.cachedIngredientItems.get(ingIdx);

                    for (int stackIdx = 0; stackIdx < stacks.size(); ++stackIdx) {
                        ItemStack candidate = stacks.get(stackIdx);
                        boolean matched = false;
                        if (simple != null) {
                            Item it = candidate.getItem().asItem();
                            for (Item si : simple) {
                                if (si == it) { matched = true; break; }
                            }
                        } else {
                            if (ingredient.test(candidate)) matched = true;
                        }

                        if (matched) {
                            ItemStack theStack = stacks.get(stackIdx).copy();
                            theStack.setCount(this.countCraftPerRecipe);
                            consumedStacks.add(theStack.copy());
                            usedStacks.accept(theStack, this.firstInputSlot + stackIdx);
                            break;
                        }
                    }
                }

                List<ItemStack> resultItems = this.resultItems((T) recipe, consumedStacks);

                if (!resultItems.isEmpty() && !inventory.hasSpaceFor(resultItems, this.firstOutputSlot, inventory.getSlots())) {
                    this.recipe = null;
                } else {
                    this.recipe = (T) recipe;
                }

            }
        }
    }

    public int maxCountCraft(@SuppressWarnings("rawtypes") Iterator iteratorRecipe){
        List<Ingredient> ingredients = new ArrayList<>();
        while (iteratorRecipe.hasNext()) {
            ingredients.add((Ingredient) iteratorRecipe.next());
        }

        // Count how many of each ingredient we have available (cumulative across all slots)
        Map<Ingredient, Integer> ingredientCounts = new HashMap<>();

        for (Ingredient ingredient : ingredients) {
            int totalAvailable = 0;

            // Sum up all matching items across ALL input slots
            for (int slotIdx = this.firstInputSlot; slotIdx < this.firstOutputSlot; slotIdx++) {
                ItemStack slotStack = this.getInventory().getStackInSlot(slotIdx);
                if (!slotStack.isEmpty() && ingredient.test(slotStack)) {
                    totalAvailable += slotStack.getCount();
                }
            }

            ingredientCounts.put(ingredient, totalAvailable);
        }

        // Find the minimum possible crafts (bottleneck ingredient)
        int minCraft = Integer.MAX_VALUE;
        for (Ingredient ingredient : ingredients) {
            int available = ingredientCounts.getOrDefault(ingredient, 0);
            if (available == 0) {
                return 0;  // Missing ingredient
            }
            minCraft = Math.min(minCraft, available);
        }

        // Limit by max craft count
        if (minCraft == Integer.MAX_VALUE) minCraft = 0;
        minCraft = Math.min(this.countCraft, minCraft);

        return minCraft;
    }

    protected void craftRecipe() {
        this.craftRecipe((stack, slot) -> {
        });
    }

    protected void craftRecipe(BiConsumer<ItemStack, Integer> usedStacks) {
        if (this.level != null && !this.level.isClientSide) {
            if (this.recipe != null) {
                IAdvancedItemHandlerModifiable inventory = this.getInventory().getUnrestricted();
                List<ItemStack> consumedStacks = new ArrayList<>();

                List<Ingredient> ingredients = this.recipe.getIngredients();
                int nIngredients = ingredients.size();

                // Use the pre-calculated countCraftPerRecipe which was validated in updateRecipe()
                int countItemCraft = this.countCraftPerRecipe;


                // Track actual minimum that can be extracted
                int actualExtractedMin = Integer.MAX_VALUE;

                // SINGLE PASS: Extract and track actual amounts
                for (int ingIdx = 0; ingIdx < nIngredients; ingIdx++) {
                    Ingredient ingredient = ingredients.get(ingIdx);
                    Item[] simple = this.cachedIngredientItems == null ? null : this.cachedIngredientItems.get(ingIdx);

                    int totalExtractedForIngredient = 0;

                    for (int slot = this.firstInputSlot; slot < this.firstOutputSlot; ++slot) {
                        ItemStack cand = inventory.getStackInSlot(slot);
                        boolean matched = false;
                        if (simple != null) {
                            Item it = cand.getItem().asItem();
                            for (Item si : simple) { if (si == it) { matched = true; break; } }
                        } else {
                            if (ingredient.test(cand)) matched = true;
                        }
                        if (matched) {
                            // Calculate how much we still need
                            int stillNeeded = countItemCraft - totalExtractedForIngredient;
                            if (stillNeeded <= 0) break;

                            // Extract from this slot
                            ItemStack extracted = inventory.extractItem(slot, stillNeeded, false);
                            if (!extracted.isEmpty()) {
                                totalExtractedForIngredient += extracted.getCount();
                                consumedStacks.add(extracted);
                                usedStacks.accept(extracted, slot);

                                // If we got enough, stop searching for this ingredient
                                if (totalExtractedForIngredient >= countItemCraft) {
                                    break;
                                }
                            }
                        }
                    }

                    if (totalExtractedForIngredient == 0) {
                        actualExtractedMin = 0;
                        break;
                    }

                    // Track what was actually extracted
                    actualExtractedMin = Math.min(actualExtractedMin, totalExtractedForIngredient);
                }

                if (actualExtractedMin == Integer.MAX_VALUE) {
                    actualExtractedMin = 0;
                }

                // Only produce if something was actually extracted
                if (actualExtractedMin > 0) {
                    List<ItemStack> results = this.resultItems(this.recipe, consumedStacks);
                    for (ItemStack result : results) {
                        // Scale by actual extracted, not planned
                        result.setCount(result.getCount() * actualExtractedMin);
                        this.putIntoOutputOrDrop(result.copy());
                    }

                    this.onCrafted(this.recipe, actualExtractedMin);
                }

                this.recipe = null;
                this.needsRecipeUpdate();
                this.countCraftPerRecipe = this.countCraft;

            }

        }
    }

    protected boolean canMatchRecipes() {
        return true;
    }

    protected boolean matchRecipe(T recipe, List<ItemStack> stacks) {
        return !RecipeHelper.matches(recipe, stacks, false);
    }

    protected void onCrafted(T recipe, int countItemCraft) {
    }

    protected List<ItemStack> resultItems(T recipe, List<ItemStack> stacks) {
        assert this.level != null;
        return recipe.getResultItem(this.level.registryAccess()).isEmpty() ? List.of() : List.of(recipe.getResultItem(this.level.registryAccess()).copy());
    }

    protected void putIntoOutputOrDrop(ItemStack stack) {
        if (this.level != null && !this.level.isClientSide) {
            IAdvancedItemHandlerModifiable inventory = this.getInventory().getUnrestricted();
            ItemStack left = stack.copy();

            for(int slot = this.firstOutputSlot; slot < inventory.getSlots(); ++slot) {
                left = inventory.insertItem(slot, left, false);
                if (left.isEmpty()) {
                    return;
                }
            }

            if (!left.isEmpty()) {


                ItemEntity ie = new ItemEntity(this.level, (double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.7, (double)this.worldPosition.getZ() + 0.5, left.copy());
                this.level.addFreshEntity(ie);
            }

        }
    }

    public void needsRecipeUpdate() {
        this.needsRecipeUpdate = true;
    }

    public void load(@Nonnull CompoundTag nbt) {
        super.load(nbt);
        this.needsRecipeUpdate = true;
    }
}