package com.example.terraplateextension.client.jei;

import com.example.terraplateextension.TerraPlateExtension;
import com.example.terraplateextension.recipe.BlockLayoutEntry;
import com.example.terraplateextension.recipe.BlockMatch;
import com.example.terraplateextension.recipe.PlatePosition;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.client.gui.HUDHandler;
import vazkii.botania.common.block.BotaniaBlocks;

import java.util.List;
import java.util.Map;

/**
 * JEI 配方类别：泰拉凝聚板。
 *
 * <p>布局仿照 Botania 原版凝聚板 JEI 类别（1.12.2 Botania Tweaks 沿用的样式）：
 * <ul>
 *   <li>顶部：输入材料最多两行，第二行分布在下箭头两侧；</li>
 *   <li>中央：下箭头与产物槽；</li>
 *   <li>底部：左侧"合成前"3x3 平台方块，右侧"合成后"3x3 转化方块，中间箭头连接；
 *       两个网格均注册为 JEI 槽位，可与 JEI 联动（点击/按 U/R 查看方块参与的配方）；</li>
 *   <li>底部：原版风格 mana 条（水蓝色）。</li>
 * </ul>
 *
 * <p>无条件的原版配方左侧绘制 Botania 默认平台（四角+中心活石、四边石头）作为示意。
 */
public class ExtendedTerraPlateRecipeCategory implements IRecipeCategory<ITerraPlateRecipeView> {

    private static final String LANG = "jei." + TerraPlateExtension.MOD_ID + ".";

    // 背景左右各加 1px，给两个完整平台及其间的箭头留出空间。
    private static final int TEXTURE_WIDTH = 170;
    public static final int WIDTH = TEXTURE_WIDTH + 2;
    public static final int HEIGHT = 130;

    private static final int CENTER_X = WIDTH / 2;
    private static final int INPUT_Y = 0;
    private static final int SECOND_INPUT_Y = 18;
    private static final int INPUT_SIZE = 16;
    private static final int INPUT_SPACING = 20;
    private static final int INPUTS_PER_ROW = 8;
    private static final int INPUTS_PER_PAGE = INPUTS_PER_ROW * 2;
    private static final int GRID_BASE_X_BEFORE = 40;
    private static final int GRID_BASE_X_AFTER = WIDTH - 40;

    /** mana 条（原版 HUD 样式；右移 4px 视觉居中） */
    private static final int MANA_X = CENTER_X - 50;
    private static final int MANA_Y = 60;
    private static final int MANA_WIDTH = 100;
    private static final int PLATE_MAX_MANA = 1_000_000;

    private final IDrawable background;
    private final IDrawable icon;

    public ExtendedTerraPlateRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.drawableBuilder(
                new ResourceLocation(TerraPlateExtension.MOD_ID, "textures/gui/terrasteeloverlay.png"),
                0, 0, TEXTURE_WIDTH, HEIGHT).addPadding(0, 0, 1, 1).build();
        // 类别图标：泰拉凝聚板方块
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(BotaniaBlocks.terraPlate));
    }

    @Override
    public RecipeType<ITerraPlateRecipeView> getRecipeType() {
        return TerraPlateExtensionJeiPlugin.RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(LANG + "category.title");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    static void addRecipePages(List<ITerraPlateRecipeView> views, ITerraPlateRecipeView view) {
        List<Ingredient> inputs = view.getInputs();
        int count = inputs.size();
        if (count <= INPUTS_PER_PAGE) {
            views.add(view);
            return;
        }

        int pageCount = 1 + (count - 1) / INPUTS_PER_PAGE;
        for (int start = 0; start < count; ) {
            int end = start + Math.min(INPUTS_PER_PAGE, count - start);
            views.add(new InputPage(view, inputs.subList(start, end), 1 + start / INPUTS_PER_PAGE, pageCount));
            start = end;
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ITerraPlateRecipeView view, IFocusGroup focuses) {
        List<Ingredient> inputs = view.getInputs();
        int firstRow = Math.min(inputs.size(), INPUTS_PER_ROW);
        addInputRow(builder, inputs, 0, firstRow, CENTER_X, INPUT_Y);

        // 第二行留出中间的箭头位置。
        int secondRow = inputs.size() - firstRow;
        int left = (secondRow + 1) / 2;
        addInputRow(builder, inputs, firstRow, left, CENTER_X - 46, SECOND_INPUT_Y);
        addInputRow(builder, inputs, firstRow + left, secondRow - left, CENTER_X + 46, SECOND_INPUT_Y);

        int contentY = inputs.isEmpty() ? 0 : 36;
        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X - 8, contentY)
                .addItemStack(view.getOutput());
        if (view instanceof InputPage page) {
            output.addTooltipCallback((slotView, tooltip) -> {
                tooltip.add(page.label());
                tooltip.add(Component.translatable(LANG + "tooltip.input_pages"));
            });
        }

        int platformY = contentY + 67;

        // 左侧"合成前"网格：需求方块注册为 INPUT 槽（可查看该方块的获取配方 / 按 U 查看参与的配方）
        addGridSlots(builder, view.getBeforeLayout(), GRID_BASE_X_BEFORE, platformY,
                RecipeIngredientRole.INPUT, LANG + "tooltip.required");
        // 右侧"合成后"网格：转化方块注册为 OUTPUT 槽（点击查看该方块如何获得，即本凝聚板配方）
        addGridSlots(builder, view.getAfterLayout(), GRID_BASE_X_AFTER, platformY,
                RecipeIngredientRole.OUTPUT, LANG + "tooltip.transformed");
    }

    private static void addInputRow(IRecipeLayoutBuilder builder, List<Ingredient> inputs, int start,
                                    int count, int centerX, int y) {
        int firstX = centerX - (INPUT_SIZE + (count - 1) * INPUT_SPACING) / 2;
        for (int i = 0; i < count; i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, firstX + i * INPUT_SPACING, y)
                    .addIngredients(inputs.get(start + i));
        }
    }

    private record InputPage(ITerraPlateRecipeView recipe, List<Ingredient> inputs,
                             int number, int count) implements ITerraPlateRecipeView {
        Component label() {
            return Component.translatable(LANG + "label.input_page", number, count);
        }

        @Override
        public ResourceLocation getId() {
            return recipe.getId();
        }

        @Override
        public List<Ingredient> getInputs() {
            return inputs;
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
            return recipe.isCustom();
        }

        @Override
        public List<BlockLayoutEntry> getBeforeLayout() {
            return recipe.getBeforeLayout();
        }

        @Override
        public List<BlockLayoutEntry> getAfterLayout() {
            return recipe.getAfterLayout();
        }
    }

    /** 把方块布局注册为 JEI 槽位（流体匹配/流体方块注册为流体槽，无水物品方块跳过由 draw 绘制占位） */
    private void addGridSlots(IRecipeLayoutBuilder builder, List<BlockLayoutEntry> entries,
                              int originX, int gridY, RecipeIngredientRole role, String labelLangKey) {
        for (BlockLayoutEntry entry : entries) {
            BlockMatch match = entry.block();
            if (match == null) {
                continue;
            }
            PlatePosition pos = entry.position();
            int[] iso = platformOffset(pos.x(), pos.z());
            int x = originX + iso[0];
            int y = gridY + iso[1];

            // 纯流体匹配（如 thermal:ender）或流体方块（水/岩浆）：
            // 用对应的桶装流体物品显示（如 水桶/岩浆桶），更直观
            boolean fluidMatch = match.isFluidMatch() && match.getFluid() != null;
            boolean liquidBlockMatch = !fluidMatch && match.getBlock() instanceof LiquidBlock;
            if (fluidMatch || liquidBlockMatch) {
                Fluid fluid = fluidMatch ? match.getFluid() : ((LiquidBlock) match.getBlock()).getFluid();
                ItemStack bucket = getBucketStack(fluid);
                // ⚠️ 桶物品只表示"是这种流体"，表达不了"必须是流动的"。
                //    所以 source/flowing 限定符必须靠额外一行 tooltip 说明（1.0.12）。
                boolean flowing = fluidMatch && match.isFlowingOnly();
                boolean source = fluidMatch && match.isSourceOnly();
                if (!bucket.isEmpty()) {
                    builder.addSlot(role, x, y)
                            .addItemStack(bucket)
                            .addTooltipCallback((slotView, tooltip) -> {
                                tooltip.add(Component.translatable(labelLangKey));
                                addFluidStateTooltip(flowing, source, tooltip);
                                tooltip.add(Component.translatable(LANG + "tooltip.position", pos.x(), pos.y(), pos.z()));
                            });
                } else {
                    // 没有桶物品的流体：退化为流体槽
                    builder.addSlot(role, x, y)
                            .addFluidStack(fluid, 1000)
                            .addTooltipCallback((slotView, tooltip) -> {
                                tooltip.add(Component.translatable(labelLangKey));
                                addFluidStateTooltip(flowing, source, tooltip);
                                tooltip.add(Component.translatable(LANG + "tooltip.position", pos.x(), pos.y(), pos.z()));
                            });
                }
                continue;
            }

            ItemStack stack = new ItemStack(match.getBlock());
            if (stack.isEmpty()) {
                continue; // 无物品方块：由 draw() 绘制占位
            }
            builder.addSlot(role, x, y)
                    .addItemStack(stack)
                    // 悬停时在物品信息后追加说明（合成前/合成后 + 坐标 + 状态要求）
                    .addTooltipCallback((slotView, tooltip) -> {
                        tooltip.add(Component.translatable(labelLangKey));
                        tooltip.add(Component.translatable(LANG + "tooltip.position", pos.x(), pos.y(), pos.z()));
                        addStateRequirementTooltip(match, tooltip);
                    });
        }
    }

    /** 获取流体的桶装物品（如水 -> 水桶）；没有桶则返回空 */
    private static ItemStack getBucketStack(Fluid fluid) {
        if (fluid == null) {
            return ItemStack.EMPTY;
        }
        Item bucketItem = fluid.getBucket();
        if (bucketItem == null || bucketItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(bucketItem);
    }

    /**
     * 追加"流体形态"说明（1.0.12）。
     *
     * <p>桶物品图标只能表达"是这种流体"，说不出"必须是流动的" ——
     * 所以用文字补上，否则玩家会以为摆个水源方块就行。
     * 两个限定符都不设时不加任何文字（表示水源/流动都可以）。
     */
    private static void addFluidStateTooltip(boolean flowing, boolean source, List<Component> tooltip) {
        if (flowing) {
            tooltip.add(Component.translatable(LANG + "tooltip.fluid_flowing"));
        } else if (source) {
            tooltip.add(Component.translatable(LANG + "tooltip.fluid_source"));
        }
    }

    /** 把"状态要求"文字追加到 tooltip（如 点亮(lit=true)），无状态要求时不加 */
    private static void addStateRequirementTooltip(BlockMatch match, List<Component> tooltip) {
        if (match == null || match.getStateProperties().isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable(LANG + "tooltip.state_require"));
        for (Map.Entry<String, String> e : match.getStateProperties().entrySet()) {
            tooltip.add(Component.literal("  " + e.getKey() + " = " + e.getValue()));
        }
    }

    /**
     * Botania 原版凝聚板 JEI 的 3D 平台坐标。它不是规整的平面 3×3 网格，
     * 而是把九个方块错开成菱形透视；这正是参考图底部平台的摆法。
     */
    private static int[] platformOffset(int x, int z) {
        if (x == -1 && z == -1) return new int[]{-40, -8};
        if (x ==  0 && z == -1) return new int[]{-24,-16};
        if (x ==  1 && z == -1) return new int[]{ 24, -8};
        if (x == -1 && z ==  0) return new int[]{-24,  0};
        if (x ==  0 && z ==  0) return new int[]{ -8, -8};
        if (x ==  1 && z ==  0) return new int[]{  8,  0};
        if (x == -1 && z ==  1) return new int[]{ -8,-24};
        if (x ==  0 && z ==  1) return new int[]{  8,-16};
        if (x ==  1 && z ==  1) return new int[]{ -8,  8};
        // 自定义位置超出 3×3 时仍给出稳定的等距投影位置。
        return new int[]{-8 + (x - z) * 16, -8 + (x + z) * 8};
    }

    @Override
    public void draw(ITerraPlateRecipeView view, IRecipeSlotsView slotsView, GuiGraphics gui,
                     double mouseX, double mouseY) {
        // 箭头由 Botania Tweaks 的 terrasteeloverlay 纹理绘制。
        HUDHandler.renderManaBar(gui, MANA_X, MANA_Y, MANA_WIDTH, 0.75F, view.getMana(), PLATE_MAX_MANA);
        if (view instanceof InputPage page) {
            Font font = Minecraft.getInstance().font;
            Component label = page.label();
            int textWidth = font.width(label);
            float scale = Math.min(1.0F, 72.0F / textWidth);
            gui.pose().pushPose();
            gui.pose().translate(WIDTH - 2, 40, 0);
            gui.pose().scale(scale, scale, 1.0F);
            gui.drawString(font, label, -textWidth, 0, 0xFF404040, false);
            gui.pose().popPose();
        }
        // 底部：合成前/合成后平台方块（由 setRecipe 注册成 JEI 槽位的格子由 JEI 渲染）
        drawPlatform(gui, view);
    }

    /**
     * 绘制底部平台区：只画中间的箭头。
     *
     * <h2>1.0.11：只画真实存在的槽位，其余一律留空</h2>
     * 这里先后做过三版，最终定成"不画任何装饰性内容"：
     * <ol>
     *   <li>最早：扩展配方的空格画深灰半透明小方块 —— 看起来像"这里要求某种方块"，误导；</li>
     *   <li>1.0.10：扩展配方空格留空，但<b>完全无条件</b>的配方仍画原版平台示意
     *       （中心+四角活石、四边青金石块）—— 用户反馈同样是干扰；</li>
     *   <li>1.0.11（当前）：<b>空格一律留空、不画任何东西</b>。
     *       JEI 里只显示 <b>配方的 {@code below} 真正要求过</b> 的方块，
     *       没有要求的位置就是空白。</li>
     * </ol>
     * 原版平台的实际结构（反编译 Botania 的 MULTIBLOCK 确认）是
     * 中心+四角活石 / 四边青金石块 —— 这个信息如果将来需要，应该写在说明文字里，
     * 而不是画进每个配方的格子里。
     */
    private void drawPlatform(GuiGraphics gui, ITerraPlateRecipeView view) {
        // 箭头已经包含在背景纹理中；没有要求的位置保持透明。
    }

    // ------------------------------------------------------------------
    // 关于"空格子的 tooltip"（1.0.11 移除）
    // ------------------------------------------------------------------
    // 这里曾经覆写 getTooltipStrings(...)，给底部两个 3x3 网格里的**空格子**
    // 加悬停说明（"原版平台：…" / "该位置无方块要求" / "无方块转化"）。
    //
    // 用户反馈这些文字纯属干扰（"这些东西都不要"）：空白格子本来就没有东西可看，
    // 鼠标划过却冒出一段话，反而让人以为那个位置有什么要求。
    //
    // 所以现在不再覆写那个方法 —— 只有**真正有方块的格子**（由 setRecipe 注册成
    // JEI 槽位、并挂了 addTooltipCallback 的那些）才会有 tooltip。
    // 顺带的好处：那个方法在 JEI 15.x 里已被标记 deprecated，去掉正好。
}
