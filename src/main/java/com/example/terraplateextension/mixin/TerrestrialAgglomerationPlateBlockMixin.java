package com.example.terraplateextension.mixin;

import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import com.example.terraplateextension.recipe.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.common.block.mana.TerrestrialAgglomerationPlateBlock;

/**
 * 修复"手持原料右键凝聚板直接放上"。
 *
 * <p>原版 {@code TerrestrialAgglomerationPlateBlock.usesItem} 只检查物品是否为
 * <b>Botania 原版凝聚板配方</b>（TERRA_PLATE_TYPE）的原料——扩展配方不属于该类型，
 * 导致自定义配方原料右键放不上板。本混入在检查前补充判断：物品若是任一<b>扩展配方</b>
 * 的原料也允许放上板（不匹配时继续走原版逻辑，原版行为不受影响）。
 */
@Mixin(TerrestrialAgglomerationPlateBlock.class)
public abstract class TerrestrialAgglomerationPlateBlockMixin {

    @Inject(method = "usesItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void terraPlateExtension$usesItem(ItemStack stack, Level level,
                                                     CallbackInfoReturnable<Boolean> cir) {
        // 扩展配方的原料也允许右键放上凝聚板
        for (ExtendedTerraPlateRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TERRA_PLATE.get())) {
            for (Ingredient ingredient : recipe.getInputs()) {
                if (ingredient.test(stack)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        // 未命中扩展配方原料：不设置返回值，原版逻辑继续（检查原版配方原料）
    }
}
