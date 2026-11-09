// ============================================================
// Terra Confluence - 流动水条件示例（1.0.12 起支持）
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下
//
// 本文件讲一件事：怎么要求"凝聚板下方某格必须是【流动】的液体"。
//
// ---------- 判定规则（1.0.12）----------
//   fluid: 'minecraft:water'                    水源和流动都算（旧行为）
//   fluid: 'minecraft:water', flowing: true     只认流动的（水源方块不算）
//   fluid: 'minecraft:water', source: true      只认源头（水源方块）
//
//   source 与 flowing 不能同时写（会报错）。
//   两者都只对 fluid 条件有效，对 block 条件写会报错。
//
// ---------- 怎么摆出"流动的水" ----------
//   检测位置是凝聚板正下方那一层（y = 板子y - 1）的 3x3。
//   要某一格"是流动的"，就得让水【从旁边流进这一格】：
//
//      水槽 = 水源方块，★ = 被检测的格子
//
//          [水槽] ★ [水槽]
//            ↓    ↓
//          ★   [水槽]  ★
//
//   做法：在被检测格子的【相邻格】放水源方块，水会自然流进被检测格子，
//         那一刻它就是"流动的水"（FluidState.isSource() 为 false）。
//
//   ⚠️ 千万别直接把水桶倒在被检测的格子上 —— 那是水源方块，判定会失败。
//   ⚠️ 水需要有地方流（不能在封闭空间里），否则它会消失或停在原地变成不流动。
//
// ---------- JEI 里怎么显示 ----------
//   该格显示【水桶】图标，鼠标悬停时 tooltip 会多一行：
//     "需要【流动】的流体，水源方块不算"
//   因为桶图标本身表达不了"必须流动"，所以必须靠这行文字说清楚。
//
// ============================================================

ServerEvents.recipes(event => {

    // ---------- 例 1：三格都要求流动水 ----------
    // 输入：海洋之心 + 海晶碎片 + 海晶砂粒 + 水之符文
    // 下方：北/西/东三格必须是【流动】的水
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'minecraft:heart_of_the_sea' },
            { item: 'minecraft:prismarine_shard' },
            { item: 'minecraft:prismarine_crystals' },
            { item: 'botania:rune_water' }
        ],
        mana: 80000,
        output: { item: 'minecraft:conduit' },
        below: {
            positions: [
                { x: 0,  z: -1, fluid: 'minecraft:water', flowing: true },   // 北
                { x: -1, z: 0,  fluid: 'minecraft:water', flowing: true },   // 西
                { x: 1,  z: 0,  fluid: 'minecraft:water', flowing: true }    // 东
            ]
        }
    });

    // ---------- 例 2：流动水 + 水源 混用 ----------
    // 演示同一个配方里两种限定符混着写
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'minecraft:nautilus_shell' },
            { item: 'botania:rune_water' }
        ],
        mana: 50000,
        output: { item: 'minecraft:heart_of_the_sea' },
        below: {
            positions: [
                { x: 0,  z: -1, fluid: 'minecraft:water', flowing: true },   // 必须流动
                { x: 0,  z: 1,  fluid: 'minecraft:water', flowing: true },   // 必须流动
                { x: -1, z: -1, fluid: 'minecraft:water', source: true }     // 必须是水源方块
            ]
        }
    });

    // ---------- 例 3：流动的岩浆 ----------
    // 流体 ID 换成 minecraft:lava 即可，写法完全一样
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'minecraft:magma_block' },
            { item: 'botania:rune_fire' }
        ],
        mana: 60000,
        output: { item: 'minecraft:magma_cream' },
        below: {
            positions: [
                { x: 0,  z: -1, fluid: 'minecraft:lava', flowing: true }
            ]
        }
    });

    // ---------- 例 4：流动水 + 方块条件混合 ----------
    // 四角是方块、中间三格是流动水 —— 展示 fluid 和 block 能混用
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'minecraft:prismarine_bricks' },
            { item: 'botania:rune_water' },
            { item: 'botania:rune_earth' }
        ],
        mana: 100000,
        output: { item: 'minecraft:dark_prismarine' },
        below: {
            corners: 'minecraft:prismarine_bricks',
            positions: [
                { x: 0,  z: -1, fluid: 'minecraft:water', flowing: true },
                { x: -1, z: 0,  fluid: 'minecraft:water', flowing: true },
                { x: 1,  z: 0,  fluid: 'minecraft:water', flowing: true }
            ]
        },
        result_blocks: [
            { x: -1, z: -1, block: 'minecraft:prismarine' },
            { x: -1, z: 1,  block: 'minecraft:prismarine' },
            { x: 1,  z: -1, block: 'minecraft:prismarine' },
            { x: 1,  z: 1,  block: 'minecraft:prismarine' }
        ]
    });

});

// ============================================================
// 排查用：日志里会打印不满足的位置与实际方块
// ============================================================
// 条件不满足时，latest.log 里会有这类行：
//
//   [TPE][调试] 位置(0,-1,-1) 需要 fluid:minecraft:water(flowing)，实际方块 minecraft:water
//
// 注意末尾的 "(flowing)" —— 它告诉你这个条件要求流动。
// 如果实际方块是 minecraft:water 却仍判定失败，说明那一格是【水源】而不是流动水，
// 需要按上面的摆法让水从旁边流进来。
//
// ============================================================
// 常见流体 ID
// ============================================================
//   minecraft:water      水
//   minecraft:lava       岩浆
//   thermal:ender        热力膨胀的末影流体（该整合包装了热力才可用）
//
// 写不存在的 ID 会在配方加载时报 "未知的流体: xxx"。
