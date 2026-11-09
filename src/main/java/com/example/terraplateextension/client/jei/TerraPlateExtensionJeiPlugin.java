package com.example.terraplateextension.client.jei;

import com.example.terraplateextension.TerraPlateExtension;
import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import com.example.terraplateextension.recipe.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.client.integration.jei.TerrestrialAgglomerationRecipeCategory;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 插件。
 *
 * <p>注册"泰拉凝炼"配方类别，并同时收集：
 * <ul>
 *   <li>本模组的扩展配方（terra_plate_extension:terra_plate）；</li>
 *   <li>Botania 原版泰拉凝聚板配方（botania:terra_plate），原版配方显示"无额外条件"。</li>
 * </ul>
 *
 * <p>运行时隐藏 Botania 自带的原版凝聚板配方类别——加入本模组后，
 * JEI 中不再显示原版格式的凝聚板配方，所有凝聚板配方统一在本模组的新格式类别中显示。
 */
@JeiPlugin
public class TerraPlateExtensionJeiPlugin implements IModPlugin {

    /**
     * 本类别使用的 JEI RecipeType（注意与 Minecraft 的 RecipeType 区分）。
     *
     * <p>命名空间使用 {@code botania}：JEI 的"配方来源"显示基于命名空间对应的模组，
     * 这样凝聚板配方在 JEI 中统一显示来源为"植物魔法"。
     * path 使用 terra_plate_extension，避免与 Botania 原版类别
     * （botania:terra_plate）的 RecipeType 冲突。
     */
    public static final RecipeType<ITerraPlateRecipeView> RECIPE_TYPE =
            RecipeType.create("botania", "terra_plate_extension", ITerraPlateRecipeView.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TerraPlateExtension.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ExtendedTerraPlateRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // JEI 收集配方时处于客户端、已进入存档/单人世界，level 应可用
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        List<ITerraPlateRecipeView> views = new ArrayList<>();

        // 1) 扩展配方
        for (ExtendedTerraPlateRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TERRA_PLATE.get())) {
            views.add(new ExtendedRecipeView(recipe));
        }

        // 2) Botania 原版泰拉凝聚板配方（1.20.1 接口名：TerrestrialAgglomerationRecipe）
        for (TerrestrialAgglomerationRecipe recipe : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.TERRA_PLATE_TYPE)) {
            views.add(new VanillaRecipeView(recipe, level.registryAccess()));
        }

        registration.addRecipes(RECIPE_TYPE, views);
    }

    /**
     * 注册配方催化剂：泰拉凝聚板方块。
     *
     * <p>效果：
     * <ul>
     *   <li>JEI 物品列表中对凝聚板按 U 键可查看使用凝聚板的全部配方（原版联动行为）；</li>
     *   <li>配方页面左上角显示催化剂物品（"该配方使用的工具"），即凝聚板。</li>
     * </ul>
     * 原版 Botania 的催化剂指向其原版类别，而该类别已被本模组隐藏，
     * 因此必须在本模组类别上重新注册催化剂。
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(BotaniaBlocks.terraPlate), RECIPE_TYPE);
    }

    /**
     * JEI 运行时可用时：隐藏 Botania 原版凝聚板配方类别。
     * 原版配方已收集到本模组类别中（registerRecipes），因此隐藏后所有凝聚板配方
     * 统一以本模组的新格式显示，JEI 中不再出现原版格式的凝聚板配方页面。
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        jeiRuntime.getRecipeManager().hideRecipeCategory(TerrestrialAgglomerationRecipeCategory.TYPE);
    }
}
