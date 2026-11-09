package com.example.terraplateextension.recipe;

import org.jetbrains.annotations.Nullable;

/**
 * JEI 展示用的一条方块布局记录：位置 + 方块规则。
 *
 * <p>用于"合成前需求"与"合成后转化"的网格展示。
 * block 为 null 时表示该位置在合成后保持不变（仅用于"合成后"布局）。
 */
public record BlockLayoutEntry(PlatePosition position, @Nullable BlockMatch block) {
}
