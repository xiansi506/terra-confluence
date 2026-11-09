package com.example.terraplateextension.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;

import java.util.List;

/**
 * 访问 Botania 泰拉凝聚板方块实体的私有方法。
 *
 * <p>注意：新版 Botania（含 supportedColors 的构建）中凝聚板的
 * getItems() 签名从无参改为 {@code getItems(List<ItemEntity>)}，
 * 并新增 {@code getItemEntities()}——两者都要通过 Invoker 访问。
 * （模组方法永不混淆，remap=false）
 */
@Mixin(TerrestrialAgglomerationPlateBlockEntity.class)
public interface TerrestrialAgglomerationPlateBlockEntityAccessor {

    /** 调用原版私有方法 getItemEntities()：获取凝聚板上的物品实体列表 */
    @Invoker(value = "getItemEntities", remap = false)
    List<ItemEntity> terraPlateExtension$getItemEntities();

    /** 调用原版私有方法 getItems(List)：把实体列表转为物品堆叠列表 */
    @Invoker(value = "getItems", remap = false)
    List<ItemStack> terraPlateExtension$getItems(List<ItemEntity> entities);
}
