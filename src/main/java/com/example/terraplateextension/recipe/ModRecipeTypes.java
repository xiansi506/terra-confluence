package com.example.terraplateextension.recipe;

import com.example.terraplateextension.TerraPlateExtension;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方类型注册。
 *
 * <p>配方类型用于 RecipeManager 按类型查询配方
 * （{@code level.getRecipeManager().getAllRecipesFor(type)}）。
 * JSON 中的 "type" 字段对应的是序列化器 ID（见 {@link ModRecipeSerializers}）。
 */
public final class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, TerraPlateExtension.MOD_ID);

    /** 扩展泰拉凝聚板配方类型：terra_plate_extension:terra_plate */
    public static final RegistryObject<RecipeType<ExtendedTerraPlateRecipe>> TERRA_PLATE =
            RECIPE_TYPES.register("terra_plate", () -> new RecipeType<ExtendedTerraPlateRecipe>() {
                @Override
                public String toString() {
                    return "terra_plate";
                }
            });

    private ModRecipeTypes() {
    }
}
