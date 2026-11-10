package com.example.terraplateextension.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/**
 * 相对于泰拉凝聚板方块的位置偏移。
 *
 * <p>约定：y 默认取 -1，即凝聚板正下方那一层；
 * x 为东西方向（东为正），z 为南北方向（南为正）。
 */
public record PlatePosition(int x, int y, int z) {

    /** 默认的 y 偏移：凝聚板正下方一层 */
    public static final int DEFAULT_Y = -1;

    /** 便捷构造：x/z 偏移，y 取默认 -1 */
    public PlatePosition(int x, int z) {
        this(x, DEFAULT_Y, z);
    }

    public static PlatePosition of(int x, int z) {
        return new PlatePosition(x, DEFAULT_Y, z);
    }

    public static PlatePosition of(int x, int y, int z) {
        return new PlatePosition(x, y, z);
    }

    /** 根据凝聚板所在方块位置，解析出本偏移对应的世界坐标 */
    public BlockPos resolve(BlockPos platePos) {
        return platePos.offset(x, y, z);
    }

    /**
     * 将相对偏移顺时针旋转 rotation 次（每次 90°）。
     *
     * <p>用于"朝向无关"匹配：配方图案允许任意朝向摆放时，
     * 检查阶段尝试 4 种旋转，只要其中一种匹配即通过（见 PlateCondition#findMatchingRotation）。
     * 例如 (0,-1)（正北）旋转 90° 得到 (1,0)（正东）。
     */
    public static PlatePosition rotate(PlatePosition pos, int rotation) {
        int x = pos.x();
        int z = pos.z();
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new PlatePosition(-z, pos.y(), x);   // 90°
            case 2 -> new PlatePosition(-x, pos.y(), -z);  // 180°
            case 3 -> new PlatePosition(z, pos.y(), -x);   // 270°
            default -> pos;
        };
    }

    static Rotation blockRotation(int quarters) {
        return switch (Math.floorMod(quarters, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
