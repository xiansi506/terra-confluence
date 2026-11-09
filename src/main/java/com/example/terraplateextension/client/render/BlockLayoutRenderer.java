package com.example.terraplateextension.client.render;

import com.example.terraplateextension.recipe.BlockLayoutEntry;
import com.example.terraplateextension.recipe.BlockMatch;
import com.example.terraplateextension.recipe.PlatePosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端渲染辅助类：绘制泰拉凝聚板下方的方块布局网格（JEI 类别中使用）。
 *
 * <p>分工：有物品的方块由 JEI 槽位渲染；
 * 本类负责网格外框、包围盒内的空白单元、无物品方块的占位单元；
 * 占位单元的悬停提示由 JEI 类别的 {@code getTooltipStrings} 提供。
 */
public final class BlockLayoutRenderer {

    private BlockLayoutRenderer() {
    }

    /** 计算条目的包围盒：{minX, maxX, minZ, maxZ}；空列表返回 null */
    @Nullable
    public static int[] bounds(List<BlockLayoutEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockLayoutEntry e : entries) {
            minX = Math.min(minX, e.position().x());
            maxX = Math.max(maxX, e.position().x());
            minZ = Math.min(minZ, e.position().z());
            maxZ = Math.max(maxZ, e.position().z());
        }
        return new int[]{minX, maxX, minZ, maxZ};
    }

    /** 单元左上角 X：originX + (posX - minX) * pitch */
    public static int cellX(int originX, int minX, int posX, int pitch) {
        return originX + (posX - minX) * pitch;
    }

    /** 单元左上角 Y：originY + (posZ - minZ) * pitch */
    public static int cellY(int originY, int minZ, int posZ, int pitch) {
        return originY + (posZ - minZ) * pitch;
    }

    /**
     * 绘制网格外框、空白单元与无物品方块的占位单元。
     *
     * <p>占位单元的悬停提示由 JEI 类别的 {@code getTooltipStrings} 提供
     * （见 ExtendedTerraPlateRecipeCategory）。
     */
    public static void renderGridBackground(GuiGraphics gui, List<BlockLayoutEntry> entries,
                                            int originX, int originY, int cell, int gap) {
        int[] b = bounds(entries);
        if (b == null) {
            return;
        }
        int pitch = cell + gap;
        int width = (b[1] - b[0] + 1) * pitch - gap;
        int height = (b[3] - b[2] + 1) * pitch - gap;

        // 网格外框（半透明细框）
        gui.fill(originX - 1, originY - 1, originX + width + 1, originY + height + 1, 0x40FFFFFF);

        // 包围盒内但没有条目的空白单元（例如环形布局的中心）
        Set<Long> occupied = new HashSet<>();
        for (BlockLayoutEntry e : entries) {
            occupied.add(key(e.position()));
        }
        for (int z = b[2]; z <= b[3]; z++) {
            for (int x = b[0]; x <= b[1]; x++) {
                if (!occupied.contains(key(PlatePosition.of(x, z)))) {
                    int px = cellX(originX, b[0], x, pitch);
                    int py = cellY(originY, b[2], z, pitch);
                    gui.fill(px, py, px + cell, py + cell, 0x33000000);
                }
            }
        }

        for (BlockLayoutEntry entry : entries) {
            BlockMatch match = entry.block();
            if (match == null) {
                continue;
            }
            Block block = match.getBlock();
            ItemStack stack = new ItemStack(block);
            if (!stack.isEmpty()) {
                continue; // 有物品的方块由 JEI 槽位渲染
            }
            // 无物品方块（如水）：绘制占位单元（悬停提示见类别的 getTooltipStrings）
            int px = cellX(originX, b[0], entry.position().x(), pitch);
            int py = cellY(originY, b[2], entry.position().z(), pitch);
            gui.fill(px, py, px + cell, py + cell, 0xFF3A3A3A);
            gui.fill(px + 1, py + 1, px + cell - 1, py + cell - 1, 0xFF222222);
        }
    }

    private static long key(PlatePosition pos) {
        return ((long) pos.x() << 32) ^ (pos.z() & 0xFFFFFFFFL);
    }
}
