package com.example.terraplateextension.recipe;

/**
 * 指定位置的方块转化：合成完成后，把 position 处的方块替换为 target。
 */
public record BlockTransform(PlatePosition position, BlockMatch target) {
}
