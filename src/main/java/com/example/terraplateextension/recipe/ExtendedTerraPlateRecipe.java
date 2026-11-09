package com.example.terraplateextension.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩展版泰拉凝聚板配方。
 *
 * <p>在 Botania 原版配方（ingredients + mana + output）基础上，可额外指定：
 * <ul>
 *   <li>{@code below} —— 凝聚板下方的方块条件（未指定时完全按原版 Botania 行为）；</li>
 *   <li>{@code result_blocks} / {@code transform_all} —— 合成成功后下方方块的转化。</li>
 * </ul>
 *
 * <p>注意：本配方类型与 Botania 原版配方类型（botania:terra_plate）相互独立，
 * Botania 原版逻辑不会处理本类型；本模组通过 Mixin 在凝聚板逻辑中额外处理。
 */
public class ExtendedTerraPlateRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final List<Ingredient> ingredients;
    private final int mana;
    private final ItemStack output;
    /** 下方方块条件；null 表示没有额外条件（完全原版行为） */
    @Nullable private final PlateCondition condition;
    /** 指定位置的方块转化 */
    private final List<BlockTransform> transforms;
    /** 若不为 null，所有条件位置统一转化为该方块 */
    @Nullable private final BlockMatch transformAll;
    /** 解析后的条件位置列表（构造时一次性计算，供校验与 JEI 展示） */
    private final List<PlateCondition.PositionRequirement> resolvedRequirements;

    public ExtendedTerraPlateRecipe(ResourceLocation id, List<Ingredient> ingredients, int mana, ItemStack output,
                                    @Nullable PlateCondition condition, List<BlockTransform> transforms,
                                    @Nullable BlockMatch transformAll) {
        this.id = id;
        this.ingredients = List.copyOf(ingredients);
        this.mana = mana;
        this.output = output.copy();
        this.condition = condition;
        this.transforms = List.copyOf(transforms);
        this.transformAll = transformAll;
        this.resolvedRequirements = condition == null ? List.of() : condition.getResolved();
    }

    // ==================== 配方数据访问 ====================

    public ResourceLocation getId() {
        return id;
    }

    /** 消耗的 mana */
    public int getMana() {
        return mana;
    }

    /** 输入物品列表 */
    public List<Ingredient> getInputs() {
        return ingredients;
    }

    /** 产物 */
    public ItemStack getOutput() {
        return output;
    }

    /** 下方方块条件（可能为 null） */
    @Nullable
    public PlateCondition getCondition() {
        return condition;
    }

    /** 指定位置的方块转化列表 */
    public List<BlockTransform> getTransforms() {
        return transforms;
    }

    /** 所有条件位置的统一转化目标（可能为 null） */
    @Nullable
    public BlockMatch getTransformAll() {
        return transformAll;
    }

    /** 解析后的条件位置列表 */
    public List<PlateCondition.PositionRequirement> getResolvedRequirements() {
        return resolvedRequirements;
    }

    /** 是否存在有效（非空）的下方方块条件 */
    public boolean hasCondition() {
        return condition != null && !resolvedRequirements.isEmpty();
    }

    // ==================== 合成逻辑 ====================

    /** 判断凝聚板下方方块条件是否满足（不涉及物品与 mana 检查） */
    public boolean checkCondition(Level level, BlockPos platePos) {
        return findMatchingRotation(level, platePos) >= 0;
    }

    /**
     * 查找下方方块条件匹配的旋转角度（朝向无关匹配）。
     *
     * <p>凝聚板无朝向，配方位置是相对坐标；为保证非对称图案任意朝向可用，
     * 自动尝试 0°/90°/180°/270° 四种旋转。返回匹配的旋转角度（0-3），
     * 无下方条件时返回 0，不匹配返回 -1。
     * 合成后的方块转化应使用同一旋转角度（见 {@link #applyTransforms(Level, BlockPos, int)}）。
     */
    public int findMatchingRotation(Level level, BlockPos platePos) {
        if (!hasCondition()) {
            return 0;
        }
        return condition.findMatchingRotation(level, platePos);
    }

    /**
     * 合成成功后执行下方方块转化（不旋转，等价于旋转角度 0）：
     * 1) 按 result_blocks 逐位置转化；
     * 2) 若配置了 transform_all，把"未被 result_blocks 覆盖"的条件位置统一转化
     *    （指定位置的转化优先于全量转化）。
     */
    public void applyTransforms(Level level, BlockPos platePos) {
        applyTransforms(level, platePos, 0);
    }

    /**
     * 合成成功后执行下方方块转化，位置按 {@code rotation} 旋转
     * （与 {@link #findMatchingRotation} 返回的角度一致，保证转化落在匹配到的实际方块上）。
     */
    public void applyTransforms(Level level, BlockPos platePos, int rotation) {
        if (transforms.isEmpty() && transformAll == null) {
            return;
        }
        java.util.Set<PlatePosition> explicitPositions = new java.util.HashSet<>();
        for (BlockTransform t : transforms) {
            BlockPos p = PlatePosition.rotate(t.position(), rotation).resolve(platePos);
            level.setBlock(p, t.target().placementState(), 3);
            explicitPositions.add(t.position());
        }
        if (transformAll != null) {
            for (PlateCondition.PositionRequirement req : resolvedRequirements) {
                if (explicitPositions.contains(req.position())) {
                    continue; // result_blocks 指定过的位置保持不变
                }
                BlockPos p = PlatePosition.rotate(req.position(), rotation).resolve(platePos);
                level.setBlock(p, transformAll.placementState(), 3);
            }
        }
        // 通知邻居方块更新（红石等），setBlock 的 flag 3 已包含客户端同步
        level.updateNeighborsAt(platePos, level.getBlockState(platePos).getBlock());
    }

    /**
     * 判断给定的物品堆叠集合是否满足本配方的输入（多重集合匹配，
     * 与 Botania 原版 validateRecipe 逻辑一致）。
     *
     * <p>按<b>总数量</b>匹配：板上物品的 count 总和必须与配料条目数完全一致
     * （防止多余物品被吞或重复合成），且每个配料条目从堆叠中消耗 1 个。
     * 这样两个 terrasteel_ingot x1 实体合并成一个 terrasteel_ingot x2 栈后
     * 依然能匹配配方中的两个 terrasteel_ingot 条目。
     */
    public boolean matchesStacks(List<ItemStack> stacks) {
        if (ingredients.isEmpty()) {
            return false;
        }
        int totalInput = 0;
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                totalInput += s.getCount();
            }
        }
        if (totalInput != ingredients.size()) {
            return false;
        }
        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                pool.add(s.copy());
            }
        }
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;
            for (int i = 0; i < pool.size(); i++) {
                ItemStack stack = pool.get(i);
                if (ingredient.test(stack)) {
                    stack.shrink(1); // count>1 的合并栈可被多个条目消耗
                    if (stack.isEmpty()) {
                        pool.remove(i);
                    }
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    // ==================== Recipe 接口实现 ====================

    /** 不使用容器匹配（由 Mixin 在凝聚板处直接调用 matchesStacks） */
    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return output.copy();
    }

    /** 不能放入工作台合成（1.20.1 中该抽象方法名为 canCraftInDimensions） */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return output.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TERRA_PLATE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.TERRA_PLATE.get();
    }

    @Override
    public String toString() {
        return "ExtendedTerraPlateRecipe[" + id + ", mana=" + mana + ", output=" + output + "]";
    }
}
