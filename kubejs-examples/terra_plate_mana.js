// ============================================================
// Terra Confluence - 需要魔力的配方示例（修正版）
// 放置位置：<游戏目录>/kubejs/server_scripts/ 下（如 terra_plate_mana.js）
// 保存后执行 /reload 生效
//
// 重要：本模组的脚本 API 直接接收"完整配方 JSON"，所有字段必须用
// 原版配方 JSON 的合法写法（物品用 { item: '...' } 对象，不能用简写字符串）
// ============================================================

// 配方内容：
//   输入：泰拉钢锭 x2 + 魔力珍珠 x1
//   输出：盖亚锭 x1（botania:gaia_ingot）
//   mana：50000（必须给凝聚板供能，见下方"如何给凝聚板供魔"）
//   下方条件：凝聚板正下方 3x3 全部必须是"活石"（botania:livingrock）
//   合成后：9 块活石全部变成"闪光石"（botania:shimmerrock）
ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [
            { item: 'botania:terrasteel_ingot' },
            { item: 'botania:terrasteel_ingot' },
            { item: 'botania:mana_pearl' }
        ],
        mana: 50000,
        output: { item: 'botania:gaia_ingot' },
        below: {
            layout: '3x3',
            block: 'botania:livingrock'
        },
        transform_all: { block: 'botania:shimmerrock' }
    });
});

// ============================================================
// 如何给凝聚板供魔（Botania 1.20.1 机制：火花网络）
// 1. 摆一个魔力池（botania:mana_pool），给它充能：
//    - 最简单：种一朵"红莲"（botania:endoflame，Endoflame），
//      往它旁边丢煤炭/木炭（它会吃掉并产魔）；
//    - 旁边放"魔力发射器"（botania:mana_spreader）对准魔力池；
//    - 丢一组煤炭，池子就有魔力了。
// 2. 魔力池上放一个"火花"（botania:spark）。
// 3. 凝聚板上也放一个火花。
// 4. 两个火花距离别太远（火花扫描半径内），魔力就会自动流到凝聚板。
// ============================================================

// 测试步骤：
//   1) 摆 3x3 活石（9 块），凝聚板放在正中间那块上面
//   2) 按上面的方法让凝聚板有 50000 以上 mana
//   3) 往板上扔：泰拉钢锭 x2 + 魔力珍珠 x1
//   4) 预期：掉出盖亚锭，9 块活石变成闪光石
//   5) 反例：换掉一块活石（比如放石头），再扔材料 -> 红字提示条件不满足
