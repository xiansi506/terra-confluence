package com.example.terraplateextension;

import com.example.terraplateextension.config.ModConfig;
import com.example.terraplateextension.recipe.ModRecipeSerializers;
import com.example.terraplateextension.recipe.ModRecipeTypes;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 主模组类。
 *
 * <p>Terra Confluence —— 植物魔法（Botania）的附属模组：
 * 为泰拉凝聚板扩展配方能力（下方方块条件 + 合成后转化），并提供 JEI 查看界面。
 *
 * <p>注意：Forge 1.20.1（47.x）的 @Mod 类必须提供<b>无参构造函数</b>
 * （带 IEventBus 参数的构造函数是 1.20.2+ 才支持的写法）。
 */
@Mod(TerraPlateExtension.MOD_ID)
public class TerraPlateExtension {

    /** 模组 ID */
    public static final String MOD_ID = "terra_plate_extension";

    /** 日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public TerraPlateExtension() {
        // Forge 1.20.1：通过 FMLJavaModLoadingContext 获取模组事件总线
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配方类型（terra_plate_extension:terra_plate）
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        // 注册配方序列化器（对应 JSON 中的 "type" 字段）
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

        // 注册通用配置（条件失败提示等）
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);

        LOGGER.info("[{}] Terra Confluence 已加载，泰拉凝聚板自定义配方已启用。", MOD_ID);
        LOGGER.info("[{}] KubeJS 联动：若检测到 KubeJS，配方 schema 将由 kubejs-plugin.json 自动注册。", MOD_ID);
    }
}
