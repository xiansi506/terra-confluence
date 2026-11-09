// ============================================================
// Terra Confluence - KubeJS 示例脚本（修正版）
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下（如 terra_plate_extension.js）
// 需要游戏内安装 KubeJS（Forge 1.20.1）与 Rhino
// 保存后执行 /reload 即可生效
//
// 重要：本模组的脚本 API 直接接收"完整配方 JSON"，所有字段必须用
// 原版配方 JSON 的合法写法：
//   - 物品一律用 { item: '...' } 对象（不能用 'minecraft:xxx' 简写）
//   - 方块可以用字符串（'minecraft:stone'）或 { id: '...', state: {...} }
// ============================================================

// ---------- 复杂测试配方：下界之星 ----------
// 输入：钻石 + 绿宝石 + 金锭 x2；mana 0（无需魔力即可测试）
// 下方条件：3x3 石砖（剔除中心 (0,0)）+ 位置 (1,1) 未点亮的红石灯
// 转化：(-1,-1)->钻石块、(1,1)->红石块（指定位置优先）、其余石砖->錾制石砖
// 注意：位置限制在凝聚板正下方 3x3 范围内（x、z 在 -1..1，y 固定 -1）
ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'minecraft:diamond' },
            { item: 'minecraft:emerald' },
            { item: 'minecraft:gold_ingot' },
            { item: 'minecraft:gold_ingot' }
       ],
        mana: 10000,
        output: { item: 'minecraft:nether_star' },
        below: {
            layout: '3x3',
            block: 'minecraft:stone_bricks',

            positions: [
                { x: 1, z: 1, block: { id: 'minecraft:redstone_lamp', state: { lit: 'false' } } }
            ]
        },
        result_blocks: [
            { x: 1, z: 1, block: 'minecraft:redstone_block' },
            { x: -1, z: -1, block: 'minecraft:diamond_block' }
        ],
        transform_all: { block: 'minecraft:chiseled_stone_bricks' }
    });
});

// ---------- 移除 / 修改配方 ----------
ServerEvents.recipes(event => {
    // 按类型 + 产物移除
    // event.remove({ type: 'terra_plate_extension:terra_plate', output: 'minecraft:nether_star' });

    // 修改配方 = 先移除，再以新参数添加
    // event.remove({ type: 'terra_plate_extension:terra_plate', output: 'minecraft:gold_ingot' });
    // event.recipes.terra_plate_extension.terra_plate({ ...新配置... });
});
