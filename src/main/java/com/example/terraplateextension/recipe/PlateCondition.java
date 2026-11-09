package com.example.terraplateextension.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 泰拉凝聚板下方的方块条件。
 *
 * <p>由"布局快捷模式（可为 none）+ 三组快捷模式（center/corners/edges）+ 显式 positions"组成，
 * exclude 用于剔除布局/三组生成的位置（例如 3x3 布局中留出中心给魔力池）。
 *
 * <p><b>三组快捷模式</b>：把凝聚板正下方 3x3 区域分成三组，每组可指定一种方块：
 * <ul>
 *   <li>{@code center}：中心一格 (0,0)；</li>
 *   <li>{@code corners}：四个角 (-1,-1) (1,-1) (-1,1) (1,1)；</li>
 *   <li>{@code edges}：四条边中点 (-1,0) (1,0) (0,-1) (0,1)。</li>
 * </ul>
 * 三种字段都可选，只检查指定了的组。
 */
public class PlateCondition {

    /** 一个位置 + 该位置要求的方块 */
    public record PositionRequirement(PlatePosition position, BlockMatch block) {
    }

    // ===== 三组快捷模式的位置常量（y 固定 -1，即凝聚板正下方一层） =====
    private static final List<PlatePosition> ZONE_CENTER = List.of(
            new PlatePosition(0, PlatePosition.DEFAULT_Y, 0));
    private static final List<PlatePosition> ZONE_CORNERS = List.of(
            new PlatePosition(-1, PlatePosition.DEFAULT_Y, -1),
            new PlatePosition(1, PlatePosition.DEFAULT_Y, -1),
            new PlatePosition(-1, PlatePosition.DEFAULT_Y, 1),
            new PlatePosition(1, PlatePosition.DEFAULT_Y, 1));
    private static final List<PlatePosition> ZONE_EDGES = List.of(
            new PlatePosition(-1, PlatePosition.DEFAULT_Y, 0),
            new PlatePosition(1, PlatePosition.DEFAULT_Y, 0),
            new PlatePosition(0, PlatePosition.DEFAULT_Y, -1),
            new PlatePosition(0, PlatePosition.DEFAULT_Y, 1));

    private final LayoutType layout;
    /** layout 快捷模式下所有位置要求的方块 */
    @Nullable private final BlockMatch layoutBlock;
    /** 三组快捷模式：中心 (0,0) 要求的方块 */
    @Nullable private final BlockMatch centerBlock;
    /** 三组快捷模式：四个角要求的方块 */
    @Nullable private final BlockMatch cornerBlocks;
    /** 三组快捷模式：四条边中点要求的方块 */
    @Nullable private final BlockMatch edgeBlocks;
    private final List<PlatePosition> exclude;
    private final List<PositionRequirement> explicit;
    /** 解析后的最终需求列表（构造时一次性计算，避免每次合成检查重复分配） */
    private final List<PositionRequirement> resolved;

    public PlateCondition(LayoutType layout, @Nullable BlockMatch layoutBlock,
                          @Nullable BlockMatch centerBlock, @Nullable BlockMatch cornerBlocks,
                          @Nullable BlockMatch edgeBlocks,
                          List<PlatePosition> exclude, List<PositionRequirement> explicit) {
        if (layout != LayoutType.NONE && layoutBlock == null) {
            throw new IllegalArgumentException("使用 layout 快捷模式时必须提供 block");
        }
        this.layout = layout;
        this.layoutBlock = layoutBlock;
        this.centerBlock = centerBlock;
        this.cornerBlocks = cornerBlocks;
        this.edgeBlocks = edgeBlocks;
        this.exclude = List.copyOf(exclude);
        this.explicit = List.copyOf(explicit);
        this.resolved = resolve();
    }

    public LayoutType getLayout() {
        return layout;
    }

    @Nullable
    public BlockMatch getLayoutBlock() {
        return layoutBlock;
    }

    @Nullable
    public BlockMatch getCenterBlock() {
        return centerBlock;
    }

    @Nullable
    public BlockMatch getCornerBlocks() {
        return cornerBlocks;
    }

    @Nullable
    public BlockMatch getEdgeBlocks() {
        return edgeBlocks;
    }

    public List<PlatePosition> getExclude() {
        return exclude;
    }

    public List<PositionRequirement> getExplicit() {
        return explicit;
    }

    /** 展开为最终的需求列表（layout 位置 + 三组位置 + 显式位置，显式位置可覆盖同位置的其他方块） */
    private List<PositionRequirement> resolve() {
        Set<PlatePosition> excluded = new HashSet<>(exclude);
        LinkedHashMap<PlatePosition, BlockMatch> map = new LinkedHashMap<>();

        if (layout != LayoutType.NONE) {
            for (PlatePosition p : layout.getOffsets()) {
                if (!excluded.contains(p)) {
                    map.put(p, layoutBlock);
                }
            }
        }
        if (centerBlock != null) {
            for (PlatePosition p : ZONE_CENTER) {
                if (!excluded.contains(p)) {
                    map.put(p, centerBlock);
                }
            }
        }
        if (cornerBlocks != null) {
            for (PlatePosition p : ZONE_CORNERS) {
                if (!excluded.contains(p)) {
                    map.put(p, cornerBlocks);
                }
            }
        }
        if (edgeBlocks != null) {
            for (PlatePosition p : ZONE_EDGES) {
                if (!excluded.contains(p)) {
                    map.put(p, edgeBlocks);
                }
            }
        }
        for (PositionRequirement req : explicit) {
            map.put(req.position(), req.block());
        }

        List<PositionRequirement> result = new ArrayList<>();
        map.forEach((pos, block) -> result.add(new PositionRequirement(pos, block)));
        // 按 (z, x) 排序，保证 JEI 展示与日志输出稳定
        result.sort(Comparator.comparingInt((PositionRequirement r) -> r.position().z())
                .thenComparingInt(r -> r.position().x()));
        return List.copyOf(result);
    }

    /** 解析后的需求列表（只读） */
    public List<PositionRequirement> getResolved() {
        return resolved;
    }

    public boolean isEmpty() {
        return resolved.isEmpty();
    }

    /** 检查凝聚板下方所有需求位置是否满足 */
    public boolean matches(Level level, BlockPos platePos) {
        return findMatchingRotation(level, platePos) >= 0;
    }

    /**
     * 查找匹配的旋转角度（朝向无关匹配）。
     *
     * <p>凝聚板无朝向属性，配方位置使用相对坐标。为让非对称图案在任意朝向下都能使用，
     * 依次尝试把配方图案顺时针旋转 0°/90°/180°/270°，只要其中一种旋转下所有位置都满足
     * 即返回该角度（0-3）；全部不满足返回 -1。
     *
     * <p>对称图案（3x3 同块、三组模式等）在任意旋转下等价，行为与原来一致。
     */
    public int findMatchingRotation(Level level, BlockPos platePos) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesRotated(level, platePos, rotation)) {
                return rotation;
            }
        }
        return -1;
    }

    /** 检查在指定旋转角度下所有需求位置是否满足 */
    private boolean matchesRotated(Level level, BlockPos platePos, int rotation) {
        for (PositionRequirement req : resolved) {
            BlockPos p = PlatePosition.rotate(req.position(), rotation).resolve(platePos);
            if (req.block().isFluidMatch()) {
                // 流体匹配：检查该位置的流体状态
                if (!req.block().matchesFluid(level.getFluidState(p))) {
                    return false;
                }
            } else {
                // 方块匹配：检查该位置的方块状态
                BlockState state = level.getBlockState(p);
                if (!req.block().matches(state)) {
                    return false;
                }
            }
        }
        return true;
    }
}
