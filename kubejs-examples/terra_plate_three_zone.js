// ============================================================
// Terra Confluence - 三组模式示例（中心一种、四角一种、四边一种）
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下（如 terra_plate_three_zone.js）
// 保存后执行 /reload 生效
//
// 三组模式字段（都在 below 里，每组可选，只检查指定了的组）：
//   center  : 中心一格 (0,0)
//   corners : 四个角 (-1,-1) (1,-1) (-1,1) (1,1)
//   edges   : 四条边中点 (-1,0) (1,0) (0,-1) (0,1)
// 值都是方块（字符串 ID 或 { id, state } 对象），与 layout.block 写法一致。
// ============================================================

// 配方内容：
//   输入：铁锭 x4
//   输出：金锭 x1（mana 0，无需供能即可测试）
//   下方：中心 = 绿宝石块，四角 = 黑曜石，四边 = 金块
//   合成后：三组所有位置（9 格）全部变成石砖
ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            'minecraft:iron_ingot',
            'minecraft:iron_ingot',
            'minecraft:iron_ingot',
            'minecraft:iron_ingot'
        ],
        mana: 0,
        output: { item: 'minecraft:gold_ingot' },
        below: {
            center: 'minecraft:emerald_block',
            corners: 'minecraft:obsidian',
            edges: 'minecraft:gold_block'
        },
        transform_all: { block: 'minecraft:stone_bricks' }
    });
});

// 测试步骤：
//   1) 摆 3x3：中心绿宝石块、四角黑曜石、四边金块，凝聚板放在中心绿宝石块正上方
//   2) 往板上扔 4 个铁锭
//   3) 预期：掉出金锭，9 格全部变成石砖
//   4) 反例：把任意一个角的黑曜石换成石头，再扔材料 -> 红字提示条件不满足
