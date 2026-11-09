package com.example.terraplateextension.client.jei;

import com.example.terraplateextension.recipe.BlockLayoutEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * JEI 展示用的统一配方视图。
 *
 * <p>同时包装"扩展配方"（{@code ExtendedTerraPlateRecipe}）与
 * "Botania 原版配方"（{@code vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe}，
 * 即 1.20.1 的泰拉凝聚板配方接口），使 JEI 类别可以统一处理两类配方。
 */
public interface ITerraPlateRecipeView {

    ResourceLocation getId();

    /** 输入物品 */
    List<Ingredient> getInputs();

    /** 产物 */
    ItemStack getOutput();

    /** 消耗的 mana */
    int getMana();

    /** 是否为扩展配方（false = Botania 原版配方） */
    boolean isCustom();

    /** 合成前下方方块布局（空 = 无额外条件） */
    List<BlockLayoutEntry> getBeforeLayout();

    /** 合成后下方方块布局（仅包含会发生转化的位置） */
    List<BlockLayoutEntry> getAfterLayout();
}
