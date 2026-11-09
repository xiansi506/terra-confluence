package com.example.terraplateextension.kubejs;

import com.example.terraplateextension.TerraPlateExtension;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.JsonRecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import net.minecraft.resources.ResourceLocation;

/**
 * KubeJS 插件（可选联动）。
 *
 * <p>通过 KubeJS 6 的插件系统（META-INF/kubejs-plugin.json）自动加载，
 * 为扩展泰拉凝聚板配方注册 RecipeSchema，使创作者可以用 KubeJS 脚本
 * 添加 / 移除 / 修改配方。
 *
 * <p>脚本示例（kubejs/server_scripts/ 下）：
 * <pre>
 * ServerEvents.recipes(event =&gt; {
 *     // 添加配方：直接把整个 JSON 对象传给脚本 API
 *     event.recipes.terra_plate_extension.terra_plate({
 *         ingredients: ['3x minecraft:iron_ingot'],
 *         mana: 50000,
 *         output: 'minecraft:diamond',
 *         below: {
 *             layout: 'ring',
 *             block: 'minecraft:stone'
 *         },
 *         result_blocks: [
 *             { x: 0, z: 0, block: 'minecraft:cobblestone' }
 *         ]
 *     });
 *
 *     // 移除/修改配方
 *     event.remove({ type: 'terra_plate_extension:terra_plate', output: 'minecraft:diamond' });
 * });
 * </pre>
 *
 * <p>注意：本类引用了 KubeJS 的类，由 KubeJS 插件加载器在 KubeJS 存在时才会加载；
 * 未安装 KubeJS 时本模组完全正常。
 */
public class TerraPlateKubeJSPlugin extends KubeJSPlugin {

    /** 配方类型（JSON 中的 "type" 字段）与 schema ID */
    public static final ResourceLocation RECIPE_ID =
            new ResourceLocation(TerraPlateExtension.MOD_ID, "terra_plate");

    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        // 注册脚本 API：event.recipes.terra_plate_extension.terra_plate({...})
        // 使用 KubeJS 自带的通用 JSON schema，接受完整配方 JSON 对象
        // （ingredients / mana / output / below / result_blocks / transform_all 均可用）
        event.namespace(TerraPlateExtension.MOD_ID)
                .register("terra_plate", JsonRecipeSchema.SCHEMA);

        // 让 KubeJS 认识已有的 terra_plate_extension:terra_plate 配方，
        // 从而支持 event.remove / 修改
        event.mapRecipe("terra_plate_extension:terra_plate", RECIPE_ID);

        TerraPlateExtension.LOGGER.info("[{}] KubeJS 配方 schema 已注册。", TerraPlateExtension.MOD_ID);
    }
}
