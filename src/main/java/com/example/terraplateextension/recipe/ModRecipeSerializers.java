package com.example.terraplateextension.recipe;

import com.example.terraplateextension.TerraPlateExtension;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方序列化器注册。
 *
 * <p>序列化器 ID 即配方 JSON 中的 "type" 字段：
 * {@code "type": "terra_plate_extension:terra_plate"}。
 */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TerraPlateExtension.MOD_ID);

    /** 扩展泰拉凝聚板配方序列化器 */
    public static final RegistryObject<RecipeSerializer<ExtendedTerraPlateRecipe>> TERRA_PLATE =
            RECIPE_SERIALIZERS.register("terra_plate", ExtendedTerraPlateRecipeSerializer::new);

    private ModRecipeSerializers() {
    }
}
