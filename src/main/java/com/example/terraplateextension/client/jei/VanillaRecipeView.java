package com.example.terraplateextension.client.jei;

import com.example.terraplateextension.recipe.BlockLayoutEntry;
import com.example.terraplateextension.recipe.BlockMatch;
import com.example.terraplateextension.recipe.PlatePosition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.block.BotaniaBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Botania 原版泰拉凝聚板配方的 JEI 视图。
 *
 * <p>注：Botania 1.20.1 中该配方接口名为 TerrestrialAgglomerationRecipe
 * （旧版叫 TerraPlateRecipe），输入物品通过 {@link net.minecraft.world.item.crafting.Recipe#getIngredients()} 获取。
 *
 * <p>原版凝聚板配方要求凝聚板正下方 3x3 全部为活石（livingrock），
 * 在"合成前"网格中如实显示（与扩展配方相同的格式）；原版配方无方块转化。
 */
public class VanillaRecipeView implements ITerraPlateRecipeView {

    private final TerrestrialAgglomerationRecipe recipe;
    private final ItemStack output;

    public VanillaRecipeView(TerrestrialAgglomerationRecipe recipe, RegistryAccess registryAccess) {
        this.recipe = recipe;
        this.output = recipe.getResultItem(registryAccess);
    }

    @Override
    public ResourceLocation getId() {
        // 原版配方接口未保证暴露 ID，这里给出类别级占位 ID（仅用于展示排序等场景）
        return new ResourceLocation("botania", "terra_plate");
    }

    @Override
    public List<Ingredient> getInputs() {
        return recipe.getIngredients();
    }

    @Override
    public ItemStack getOutput() {
        return output;
    }

    @Override
    public int getMana() {
        return recipe.getMana();
    }

    @Override
    public boolean isCustom() {
        return false;
    }

    @Override
    public List<BlockLayoutEntry> getBeforeLayout() {
        // 原版凝聚板平台（反编译 Botania 的 MULTIBLOCK 多方块定义，1.20.1）：
        //   顶层  ___      底层  RLR      R = botania 的 storage_blocks/lapis（青金石块）
        //         _P_            L0L      L = livingrock（活石）
        //         ___            RLR      0 = 空（板子正下方，不要求方块）
        // 即：中心 + 四角为活石，四条边为青金石块。
        // ⚠️ 曾经写反过（四边活石 / 四角青金石块），这里按字节码里的图案字符串修正。
        List<BlockLayoutEntry> entries = new ArrayList<>();
        BlockMatch livingrock = new BlockMatch(BotaniaBlocks.livingrock, Map.of());
        BlockMatch lapis = new BlockMatch(Blocks.LAPIS_BLOCK, Map.of());
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                boolean isEdge = (x == 0) != (z == 0); // 四条边（非角、非中心）
                BlockMatch block = isEdge ? lapis : livingrock;
                entries.add(new BlockLayoutEntry(new PlatePosition(x, PlatePosition.DEFAULT_Y, z), block));
            }
        }
        return entries;
    }

    @Override
    public List<BlockLayoutEntry> getAfterLayout() {
        // 原版凝聚板配方不转化下方方块：合成后平台保持不变（显示与合成前相同的结构）
        return getBeforeLayout();
    }
}
