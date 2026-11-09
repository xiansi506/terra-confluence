package com.example.terraplateextension.event;

import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;

import java.util.List;

/**
 * 泰拉凝聚板"扩展配方"合成事件（Forge 事件总线）。
 *
 * <p>这是本模组对外开放的扩展点：其他附属模组可以监听
 * {@link Pre}（可取消合成）或 {@link Post}（合成完成通知）。
 *
 * <p>注：Botania 1.20.1 中泰拉凝聚板的方块实体类名为
 * TerrestrialAgglomerationPlateBlockEntity（旧版叫 TerraPlateBlockEntity）。
 */
public abstract class PlateCraftEvent extends Event {

    private final Level level;
    private final BlockPos pos;
    private final TerrestrialAgglomerationPlateBlockEntity plate;
    private final ExtendedTerraPlateRecipe recipe;

    protected PlateCraftEvent(Level level, BlockPos pos, TerrestrialAgglomerationPlateBlockEntity plate,
                              ExtendedTerraPlateRecipe recipe) {
        this.level = level;
        this.pos = pos;
        this.plate = plate;
        this.recipe = recipe;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public TerrestrialAgglomerationPlateBlockEntity getPlate() {
        return plate;
    }

    public ExtendedTerraPlateRecipe getRecipe() {
        return recipe;
    }

    /** 合成前事件：取消（EVENT_BUS.post 返回 true）则本次合成不进行 */
    @Cancelable
    public static class Pre extends PlateCraftEvent {
        private final List<ItemStack> stacksOnPlate;

        public Pre(Level level, BlockPos pos, TerrestrialAgglomerationPlateBlockEntity plate,
                   ExtendedTerraPlateRecipe recipe, List<ItemStack> stacksOnPlate) {
            super(level, pos, plate, recipe);
            this.stacksOnPlate = List.copyOf(stacksOnPlate);
        }

        /** 合成时凝聚板上的所有物品 */
        public List<ItemStack> getStacksOnPlate() {
            return stacksOnPlate;
        }
    }

    /** 合成后事件 */
    public static class Post extends PlateCraftEvent {
        public Post(Level level, BlockPos pos, TerrestrialAgglomerationPlateBlockEntity plate,
                    ExtendedTerraPlateRecipe recipe) {
            super(level, pos, plate, recipe);
        }
    }
}
