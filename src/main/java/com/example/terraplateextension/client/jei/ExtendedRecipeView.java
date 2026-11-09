package com.example.terraplateextension.client.jei;

import com.example.terraplateextension.recipe.BlockLayoutEntry;
import com.example.terraplateextension.recipe.BlockMatch;
import com.example.terraplateextension.recipe.BlockTransform;
import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import com.example.terraplateextension.recipe.PlateCondition;
import com.example.terraplateextension.recipe.PlatePosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩展配方（ExtendedTerraPlateRecipe）的 JEI 视图。
 */
public class ExtendedRecipeView implements ITerraPlateRecipeView {

    private final ExtendedTerraPlateRecipe recipe;
    private final List<BlockLayoutEntry> before;
    private final List<BlockLayoutEntry> after;

    public ExtendedRecipeView(ExtendedTerraPlateRecipe recipe) {
        this.recipe = recipe;

        // 合成前布局：所有条件位置及其要求方块
        List<BlockLayoutEntry> beforeList = new ArrayList<>();
        for (PlateCondition.PositionRequirement req : recipe.getResolvedRequirements()) {
            beforeList.add(new BlockLayoutEntry(req.position(), req.block()));
        }
        this.before = List.copyOf(beforeList);

        // 合成后布局：仅包含会发生转化的位置
        Map<PlatePosition, BlockMatch> transformMap = new LinkedHashMap<>();
        for (BlockTransform t : recipe.getTransforms()) {
            transformMap.put(t.position(), t.target());
        }
        if (recipe.getTransformAll() != null) {
            for (PlateCondition.PositionRequirement req : recipe.getResolvedRequirements()) {
                transformMap.putIfAbsent(req.position(), recipe.getTransformAll());
            }
        }
        List<BlockLayoutEntry> afterList = new ArrayList<>();
        transformMap.forEach((pos, block) -> afterList.add(new BlockLayoutEntry(pos, block)));
        this.after = List.copyOf(afterList);
    }

    @Override
    public ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public List<Ingredient> getInputs() {
        return recipe.getInputs();
    }

    @Override
    public ItemStack getOutput() {
        return recipe.getOutput();
    }

    @Override
    public int getMana() {
        return recipe.getMana();
    }

    @Override
    public boolean isCustom() {
        return true;
    }

    @Override
    public List<BlockLayoutEntry> getBeforeLayout() {
        return before;
    }

    @Override
    public List<BlockLayoutEntry> getAfterLayout() {
        return after;
    }
}
