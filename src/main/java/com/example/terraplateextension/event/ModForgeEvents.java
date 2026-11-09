package com.example.terraplateextension.event;

import com.example.terraplateextension.TerraPlateExtension;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 事件监听器（FORGE 总线）。
 *
 * <p>示例：监听本模组抛出的 {@link PlateCraftEvent}。
 * 这是留给后续开发的扩展点——需要挂钩其他 Forge 事件时，在下面添加
 * {@code @SubscribeEvent} 方法即可。
 */
@Mod.EventBusSubscriber(modid = TerraPlateExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModForgeEvents {

    private ModForgeEvents() {
    }

    /** 示例监听：扩展泰拉凝聚板合成完成时输出调试日志 */
    @SubscribeEvent
    public static void onPlateCraftPost(PlateCraftEvent.Post event) {
        TerraPlateExtension.LOGGER.debug("收到扩展合成完成事件: {} @ {}", event.getRecipe().getOutput(), event.getPos());
    }
}
