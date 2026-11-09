package com.example.terraplateextension.recipe;

import java.util.List;

/**
 * 下方方块布局的快捷模式（可理解为"形状"）。
 *
 * <p>扩展点：需要新的快捷形状时，在枚举中新增一项即可
 * （并在 {@link ExtendedTerraPlateRecipeSerializer} 的序列化中自动生效）。
 */
public enum LayoutType {

    /** 不使用快捷模式，仅采用 positions 中显式指定的位置 */
    NONE("none", List.of()),

    /** 3x3 全部九个位置 */
    GRID_3X3("3x3", List.of(
            PlatePosition.of(-1, -1), PlatePosition.of(0, -1), PlatePosition.of(1, -1),
            PlatePosition.of(-1, 0), PlatePosition.of(0, 0), PlatePosition.of(1, 0),
            PlatePosition.of(-1, 1), PlatePosition.of(0, 1), PlatePosition.of(1, 1))),

    /** 十字形：中心 + 上下左右 */
    CROSS("cross", List.of(
            PlatePosition.of(0, -1), PlatePosition.of(-1, 0),
            PlatePosition.of(0, 0),
            PlatePosition.of(1, 0), PlatePosition.of(0, 1))),

    /** 菱形：3x3 去掉四角（中心 + 上下左右 + 四条对角线） */
    DIAMOND("diamond", List.of(
            PlatePosition.of(-1, -1), PlatePosition.of(0, -1), PlatePosition.of(1, -1),
            PlatePosition.of(-1, 0), PlatePosition.of(0, 0), PlatePosition.of(1, 0),
            PlatePosition.of(-1, 1), PlatePosition.of(0, 1), PlatePosition.of(1, 1))),

    /** 环形：3x3 去掉中心（中心位置留空，可放魔力池） */
    RING("ring", List.of(
            PlatePosition.of(-1, -1), PlatePosition.of(0, -1), PlatePosition.of(1, -1),
            PlatePosition.of(-1, 0), PlatePosition.of(1, 0),
            PlatePosition.of(-1, 1), PlatePosition.of(0, 1), PlatePosition.of(1, 1)));

    private final String jsonName;
    private final List<PlatePosition> offsets;

    LayoutType(String jsonName, List<PlatePosition> offsets) {
        this.jsonName = jsonName;
        this.offsets = List.copyOf(offsets);
    }

    /** JSON 中使用的名称 */
    public String getJsonName() {
        return jsonName;
    }

    /** 该布局包含的偏移位置 */
    public List<PlatePosition> getOffsets() {
        return offsets;
    }

    public static LayoutType fromJsonName(String name) {
        for (LayoutType t : values()) {
            if (t.jsonName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的 layout: " + name + "（可用: none / 3x3 / cross / diamond / ring）");
    }
}
