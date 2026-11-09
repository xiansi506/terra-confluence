// ============================================================
// Terra Confluence - 九宫格配方示例
// 合成前：凝聚板下方 9 格是 9 种不同的方块
// 合成后：9 格变成另外 9 种不同的方块（逐格转化）
// 需要魔力：50000（通过火花网络供能）
//
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下，保存后 /reload 生效
// ============================================================

ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        // ===== 输入物品：钻石 x2 + 绿宝石 x1 =====
        ingredients: [
            'minecraft:diamond',
            'minecraft:diamond',
            'minecraft:emerald'
        ],

        // ===== 需要的魔力：50000（凝聚板配火花 + 魔力池供能） =====
        mana: 50000,

        // ===== 产物：下界合金锭 =====
        output: { item: 'minecraft:netherite_ingot' },

        // ===== 合成前：下方 3x3 共 9 格，每格一种方块 =====
        // 坐标：x 向右为正，z 向南为正（图案任意朝向均可，自动旋转匹配）
        below: {
            positions: [
                // 后一排 (z = -1)
                { x: -1, z: -1, block: 'minecraft:stone' },           // 左后：石头
                { x: 0,  z: -1, block: 'minecraft:cobblestone' },     // 正后：圆石
                { x: 1,  z: -1, block: 'minecraft:stone_bricks' },    // 右后：石砖
                // 中间一排 (z = 0)
                { x: -1, z: 0,  block: 'minecraft:obsidian' },        // 左：黑曜石
                { x: 0,  z: 0,  block: 'minecraft:gold_block' },      // 中心：金块
                { x: 1,  z: 0,  block: 'minecraft:iron_block' },      // 右：铁块
                // 前一排 (z = 1)
                { x: -1, z: 1,  block: 'minecraft:diamond_block' },   // 左前：钻石块
                { x: 0,  z: 1,  block: 'minecraft:emerald_block' },   // 正前：绿宝石块
                { x: 1,  z: 1,  block: 'minecraft:lapis_block' }      // 右前：青金石块
            ]
        },

        // ===== 合成后：9 格分别变成另外 9 种方块（逐格转化） =====
        result_blocks: [
            // 后一排
            { x: -1, z: -1, block: 'minecraft:mossy_stone_bricks' },     // 苔石砖
            { x: 0,  z: -1, block: 'minecraft:cracked_stone_bricks' },   // 裂石砖
            { x: 1,  z: -1, block: 'minecraft:chiseled_stone_bricks' },  // 錾制石砖
            // 中间一排
            { x: -1, z: 0,  block: 'minecraft:crying_obsidian' },        // 哭泣黑曜石
            { x: 0,  z: 0,  block: 'minecraft:netherite_block' },        // 下界合金块
            { x: 1,  z: 0,  block: 'minecraft:copper_block' },           // 铜块
            // 前一排
            { x: -1, z: 1,  block: 'minecraft:redstone_block' },         // 红石块
            { x: 0,  z: 1,  block: 'minecraft:coal_block' },             // 煤炭块
            { x: 1,  z: 1,  block: 'minecraft:quartz_block' }            // 石英块
        ]
    });
});

// ============================================================
// 测试步骤：
// 1) 按"合成前"摆 9 种方块，凝聚板放在中心金块正上方
// 2) 凝聚板放火花，附近摆魔力池 + 火花（或创造魔力池）充能
// 3) 往板上扔 钻石 x2 + 绿宝石 x1
// 4) 预期：充能到 50000 mana 后合成，掉出下界合金锭，
//    下方 9 格变成"合成后"对应的 9 种方块
// 5) 图案转 90°/180°/270° 摆放同样可用（自动旋转匹配）
// ============================================================
