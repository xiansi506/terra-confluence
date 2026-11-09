// ============================================================
// Terra Confluence - 通用配方模板（逐格自定义下方方块）
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下，保存后 /reload 生效
//
// 本模板覆盖"所有情况"：
//   - 输入物品（可多个/可重复）
//   - 魔力消耗（可 0）
//   - 产物（单个物品）
//   - 凝聚板下方 3x3 共 9 个位置，每个位置单独指定方块
//   - 合成后逐格/统一转化（可选）
// 直接复制改数值即可使用。
// ============================================================

ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        // ===== 1) 输入物品（材料）：数组里每一项 = 1 个物品 ==============================
        // 写法：'minecraft:iron_ingot'（简写）或 { item: 'minecraft:iron_ingot' }（标准）
        // 多个相同物品就重复写多次（例如两个铁锭写两次）
        ingredients: [
            'minecraft:diamond',
            'minecraft:netherite_ingot'
        ],

        // ===== 2) 需要的魔力 mana：0 = 无需供能；>0 = 需要火花网络供能 ==================
        mana: 60000,

        // ===== 3) 产物 output（合成后从板上掉落的物品） ================================
        output: { item: 'minecraft:nether_star' },

        // ===== 4) 下方方块条件 below ===================================================
        // 凝聚板正下方 3x3 区域（x/z 在 -1..1，y 固定 -1 = 凝聚板正下方一层）。
        // 使用 positions 可逐格指定 9 个位置的方块（最灵活，通用所有情况）。
        // 坐标：x 向右为正，z 向南为正。
        //   （-1,-1）左后  （0,-1）正后  （1,-1）右后
        //   （-1, 0）左    （0, 0）中心  （1, 0）右
        //   （-1, 1）左前  （0, 1）正前  （1, 1）右前
        // 方块写法：'minecraft:stone'（字符串），或 { id: '...', state: { 属性: 值 } }（带方块状态）
        //   state 示例：{ id: 'minecraft:redstone_lamp', state: { lit: 'false' } }（未点亮的红石灯）
        below: {
            positions: [
                // —— 后一排 (z = -1) ——
                { x: -1, z: -1, block: 'minecraft:stone' },      // 左后
                { x: 0,  z: -1, block: 'minecraft:obsidian' },   // 正后
                { x: 1,  z: -1, block: 'minecraft:stone' },      // 右后
                // —— 中间一排 (z = 0) ——
                { x: -1, z: 0,  block: 'minecraft:obsidian' },   // 左
                { x: 0,  z: 0,  block: 'minecraft:gold_block' }, // 中心（凝聚板正下方）
                { x: 1,  z: 0,  block: 'minecraft:obsidian' },   // 右
                // —— 前一排 (z = 1) ——
                { x: -1, z: 1,  block: 'minecraft:stone' },      // 左前
                { x: 0,  z: 1,  block: 'minecraft:obsidian' },   // 正前
                { x: 1,  z: 1,  block: 'minecraft:stone' }       // 右前
            ]
        },

        // ===== 5) 合成后方块转化（可选） ===============================================
        // 不写 result_blocks 和 transform_all => 合成后下方方块保持不变（原版行为）。
        //
        // 方式一：只转化指定位置（result_blocks），例如中心变成钻石块：
        result_blocks: [
            { x: 0, z: 0, block: 'minecraft:diamond_block' }
        ],
        //
        // 方式二：所有条件位置统一转化（transform_all，与 result_blocks 二选一）：
        // transform_all: { block: 'minecraft:cobblestone' }
        //
        // 注意：如果 result_blocks 与 transform_all 同时写，result_blocks 指定的位置优先。
    });
});

// ============================================================
// 补充说明
// ============================================================
// 1. 想用快捷布局（如 3x3 / cross / diamond / ring）而不是逐格指定？
//    用 below.layout + below.block（+ below.exclude 剔除位置），见 terra_plate_extension.js。
// 2. 想"中心一种、四角一种、四边一种"三组分别指定？
//    用 below.center / below.corners / below.edges，见 terra_plate_three_zone.js。
// 3. 修改配方 = 先移除再添加（或直接改参数后 /reload）：
//    event.remove({ type: 'terra_plate_extension:terra_plate', output: 'minecraft:nether_star' });
// 4. JEI 中按住 Ctrl 可看到配方的原始 JSON，方便核对。
// ============================================================
