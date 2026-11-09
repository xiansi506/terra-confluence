package com.example.terraplateextension.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩展泰拉凝聚板配方的序列化器（JSON 读写 + 网络包读写）。
 *
 * <p>JSON 格式：
 * <pre>
 * {
 *   "type": "terra_plate_extension:terra_plate",
 *   "ingredients": [ { "item": "minecraft:iron_ingot" }, ... ],   // 必填
 *   "mana": 20000,                                                // 必填
 *   "output": { "item": "minecraft:gold_ingot" },                 // 必填
 *   "below": {                                                    // 可选：下方方块条件
 *     "layout": "3x3",                                            //   可选：none/3x3/cross/diamond/ring
 *     "block": "minecraft:stone",                                 //   layout 模式下所有位置要求的方块
 *     "center": "minecraft:stone",                                //   可选：中心 (0,0) 一种方块
 *     "corners": "minecraft:obsidian",                            //   可选：四个角 (-1,-1)(1,-1)(-1,1)(1,1) 一种方块
 *     "edges": "minecraft:gold_block",                            //   可选：四条边中点 (-1,0)(1,0)(0,-1)(0,1) 一种方块
 *     "exclude": [ { "x": 0, "z": 0 } ],                          //   可选：从 layout/三组中剔除的位置
 *     "positions": [                                              //   可选：额外的自定义位置
 *       { "x": 2, "z": 0, "block": { "id": "minecraft:redstone_lamp", "state": { "lit": "false" } } }
 *     ]
 *   },
 *   "result_blocks": [                                            // 可选：指定位置的方块转化
 *     { "x": 0, "z": -1, "block": "minecraft:cobblestone" }
 *   ],
 *   "transform_all": { "block": "minecraft:cobblestone" }         // 可选：所有条件位置统一转化
 * }
 * </pre>
 */
public class ExtendedTerraPlateRecipeSerializer implements RecipeSerializer<ExtendedTerraPlateRecipe> {

    // ==================== JSON 读取 ====================

    @Override
    public ExtendedTerraPlateRecipe fromJson(ResourceLocation id, JsonObject json) {
        // 1) 输入物品（必填）
        JsonArray ingArr = GsonHelper.getAsJsonArray(json, "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonElement el : ingArr) {
            ingredients.add(parseIngredient(el));
        }

        // 2) mana（必填）
        int mana = GsonHelper.getAsInt(json, "mana");

        // 3) 产物（必填）
        JsonObject outObj = GsonHelper.getAsJsonObject(json, "output");
        ItemStack output = ShapedRecipe.itemStackFromJson(outObj);

        // 4) 下方方块条件（可选）
        PlateCondition condition = null;
        if (json.has("below")) {
            condition = readCondition(GsonHelper.getAsJsonObject(json, "below"));
        }

        // 5) 指定位置转化（可选）
        List<BlockTransform> transforms = new ArrayList<>();
        if (json.has("result_blocks")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(json, "result_blocks")) {
                JsonObject obj = el.getAsJsonObject();
                // 转化目标通常是方块；若给了 fluid 也能解析（尽管转化到流体场景少见）
                transforms.add(new BlockTransform(readPosition(obj), readPositionBlockMatch(obj)));
            }
        }

        // 6) 全条件位置统一转化（可选）
        BlockMatch transformAll = null;
        if (json.has("transform_all")) {
            JsonObject taObj = GsonHelper.getAsJsonObject(json, "transform_all");
            transformAll = readPositionBlockMatch(taObj);
        }

        return new ExtendedTerraPlateRecipe(id, ingredients, mana, output, condition, transforms, transformAll);
    }

    /**
     * 解析单个输入物品。
     *
     * <p>兼容两种写法：
     * <ul>
     *   <li>标准对象：{ "item": "minecraft:iron_ingot" } 或 { "tag": "..." }</li>
     *   <li>简写字符串："minecraft:iron_ingot"（自动转为 {item: ...}，
     *       方便 KubeJS 脚本创作者）</li>
     * </ul>
     */
    private static Ingredient parseIngredient(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("item", el.getAsString());
            return Ingredient.fromJson(obj);
        }
        return Ingredient.fromJson(el);
    }

    private PlateCondition readCondition(JsonObject obj) {
        LayoutType layout = LayoutType.NONE;
        if (obj.has("layout")) {
            layout = LayoutType.fromJsonName(GsonHelper.getAsString(obj, "layout"));
        }

        BlockMatch layoutBlock = null;
        if (obj.has("block")) {
            layoutBlock = readBlockMatch(obj.get("block"));
        }

        // 三组快捷模式：中心一种、四角一种、四边一种（每组可选，只检查指定了的组）
        BlockMatch center = obj.has("center") ? readBlockMatch(obj.get("center")) : null;
        BlockMatch corners = obj.has("corners") ? readBlockMatch(obj.get("corners")) : null;
        BlockMatch edges = obj.has("edges") ? readBlockMatch(obj.get("edges")) : null;

        List<PlatePosition> exclude = new ArrayList<>();
        if (obj.has("exclude")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(obj, "exclude")) {
                exclude.add(readPosition(el.getAsJsonObject()));
            }
        }

        List<PlateCondition.PositionRequirement> explicit = new ArrayList<>();
        if (obj.has("positions")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(obj, "positions")) {
                JsonObject p = el.getAsJsonObject();
                // 方块条件用 block 字段（字符串或对象）；流体条件用 fluid 字段
                explicit.add(new PlateCondition.PositionRequirement(readPosition(p), readPositionBlockMatch(p)));
            }
        }

        if (layout == LayoutType.NONE && explicit.isEmpty()
                && center == null && corners == null && edges == null) {
            throw new IllegalArgumentException("below 必须包含 layout、positions、center、corners、edges 之一");
        }
        if (layout != LayoutType.NONE && layoutBlock == null) {
            throw new IllegalArgumentException("使用 layout 快捷模式时必须提供 block");
        }
        return new PlateCondition(layout, layoutBlock, center, corners, edges, exclude, explicit);
    }

    /**
     * 读取位置（x/z 默认 0，y 默认 -1）。
     *
     * <p>限制：位置必须位于凝聚板正下方的 3x3 范围内（x、z 在 -1..1，y 固定 -1）。
     * 如需放开该限制（支持任意偏移），删除 {@link #validatePosition} 的调用即可。
     */
    private PlatePosition readPosition(JsonObject obj) {
        int x = GsonHelper.getAsInt(obj, "x", 0);
        int z = GsonHelper.getAsInt(obj, "z", 0);
        int y = GsonHelper.getAsInt(obj, "y", PlatePosition.DEFAULT_Y);
        validatePosition(x, y, z);
        return new PlatePosition(x, y, z);
    }

    /** 校验位置是否在凝聚板下方 3x3 范围内 */
    private static void validatePosition(int x, int y, int z) {
        if (y != PlatePosition.DEFAULT_Y) {
            throw new IllegalArgumentException("位置 y 必须为 -1（凝聚板正下方一层），实际为 " + y);
        }
        if (Math.abs(x) > 1 || Math.abs(z) > 1) {
            throw new IllegalArgumentException("位置 (x=" + x + ", z=" + z + ") 超出凝聚板下方 3x3 范围（x、z 必须在 -1..1 之间）");
        }
    }

    /**
     * 读取方块规则：支持字符串 ID、{id, state} 对象、或流体 {fluid, source?, flowing?}。
     *
     * <p>流体的两个可选限定符（1.0.12）：
     * <ul>
     *   <li>{@code source: true} —— 只认源头（水源方块）；</li>
     *   <li>{@code flowing: true} —— 只认流动的（非源头）。</li>
     * </ul>
     * 都不写时行为同旧版：只看流体种类，水源与流动都能满足。
     */
    private BlockMatch readBlockMatch(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            throw new IllegalArgumentException("position 缺少 block/fluid 字段");
        }
        if (el.isJsonPrimitive()) {
            return new BlockMatch(BlockMatch.parseBlock(el.getAsString()), Map.of());
        }
        JsonObject obj = el.getAsJsonObject();
        // 流体匹配：{ "fluid": "minecraft:water", "flowing": true }
        if (obj.has("fluid")) {
            boolean src = GsonHelper.getAsBoolean(obj, "source", false);
            boolean flow = GsonHelper.getAsBoolean(obj, "flowing", false);
            return new BlockMatch(null, null,
                    BlockMatch.parseFluid(GsonHelper.getAsString(obj, "fluid")), src, flow);
        }
        if (!obj.has("id")) {
            throw new IllegalArgumentException("position 的 block 对象缺少 id 字段（或应使用 fluid）: " + obj);
        }
        String id = GsonHelper.getAsString(obj, "id");
        Map<String, String> props = new HashMap<>();
        if (obj.has("state")) {
            JsonObject state = GsonHelper.getAsJsonObject(obj, "state");
            for (String key : state.keySet()) {
                props.put(key, state.get(key).getAsString());
            }
        }
        return new BlockMatch(BlockMatch.parseBlock(id), props);
    }

    /**
     * 读取某个位置对象（{x,z,block?/fluid?}）的匹配规则。
     * <p>优先读 {@code fluid}（流体条件）；没有 fluid 再读 {@code block}（方块条件）。
     * fluid 的值是流体 ID 字符串（走流体注册表），可用 {@code source}/{@code flowing} 限定；
     * block 的值可以是字符串或 {id,state} 对象。
     */
    private BlockMatch readPositionBlockMatch(JsonObject posObj) {
        if (posObj.has("fluid")) {
            boolean src = GsonHelper.getAsBoolean(posObj, "source", false);
            boolean flow = GsonHelper.getAsBoolean(posObj, "flowing", false);
            return new BlockMatch(null, null,
                    BlockMatch.parseFluid(GsonHelper.getAsString(posObj, "fluid")), src, flow);
        }
        if (posObj.has("block")) {
            return readBlockMatch(posObj.get("block"));
        }
        throw new IllegalArgumentException("position 缺少 block/fluid 字段: " + posObj);
    }

    // ==================== JSON 写出（调试 / 数据生成用） ====================
    // 注意：1.20.1 的 RecipeSerializer 接口没有 toJson 方法（1.20.2+ 才加入），
    // 这里只是本模组自用的辅助方法，不加 @Override。

    public void toJson(JsonObject json, ExtendedTerraPlateRecipe recipe) {
        JsonArray ing = new JsonArray();
        for (Ingredient i : recipe.getInputs()) {
            ing.add(i.toJson());
        }
        json.add("ingredients", ing);
        json.addProperty("mana", recipe.getMana());

        JsonObject out = new JsonObject();
        out.addProperty("item", BuiltInRegistries.ITEM.getKey(recipe.getOutput().getItem()).toString());
        if (recipe.getOutput().getCount() > 1) {
            out.addProperty("count", recipe.getOutput().getCount());
        }
        json.add("output", out);

        if (recipe.getCondition() != null) {
            PlateCondition cond = recipe.getCondition();
            JsonObject below = new JsonObject();
            below.addProperty("layout", cond.getLayout().getJsonName());
            if (cond.getLayoutBlock() != null) {
                below.add("block", writeBlockMatch(cond.getLayoutBlock()));
            }
            if (cond.getCenterBlock() != null) {
                below.add("center", writeBlockMatch(cond.getCenterBlock()));
            }
            if (cond.getCornerBlocks() != null) {
                below.add("corners", writeBlockMatch(cond.getCornerBlocks()));
            }
            if (cond.getEdgeBlocks() != null) {
                below.add("edges", writeBlockMatch(cond.getEdgeBlocks()));
            }
            if (!cond.getExclude().isEmpty()) {
                JsonArray ex = new JsonArray();
                for (PlatePosition p : cond.getExclude()) {
                    ex.add(writePosition(p));
                }
                below.add("exclude", ex);
            }
            if (!cond.getExplicit().isEmpty()) {
                JsonArray ps = new JsonArray();
                for (PlateCondition.PositionRequirement req : cond.getExplicit()) {
                    JsonObject p = writePosition(req.position());
                    p.add("block", writeBlockMatch(req.block()));
                    ps.add(p);
                }
                below.add("positions", ps);
            }
            json.add("below", below);
        }

        if (!recipe.getTransforms().isEmpty()) {
            JsonArray rb = new JsonArray();
            for (BlockTransform t : recipe.getTransforms()) {
                JsonObject p = writePosition(t.position());
                p.add("block", writeBlockMatch(t.target()));
                rb.add(p);
            }
            json.add("result_blocks", rb);
        }

        if (recipe.getTransformAll() != null) {
            JsonObject ta = new JsonObject();
            ta.add("block", writeBlockMatch(recipe.getTransformAll()));
            json.add("transform_all", ta);
        }
    }

    private JsonObject writePosition(PlatePosition pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.x());
        o.addProperty("z", pos.z());
        if (pos.y() != PlatePosition.DEFAULT_Y) {
            o.addProperty("y", pos.y());
        }
        return o;
    }

    private JsonObject writeBlockMatch(BlockMatch match) {
        JsonObject o = new JsonObject();
        if (match.isFluidMatch()) {
            // 流体条件：写回 fluid，并带上 source/flowing 限定符
            o.addProperty("fluid", match.getFluidId().toString());
            if (match.isSourceOnly()) {
                o.addProperty("source", true);
            }
            if (match.isFlowingOnly()) {
                o.addProperty("flowing", true);
            }
            return o;
        }
        o.addProperty("id", match.getBlockId().toString());
        if (!match.getStateProperties().isEmpty()) {
            JsonObject state = new JsonObject();
            for (Map.Entry<String, String> e : match.getStateProperties().entrySet()) {
                state.addProperty(e.getKey(), e.getValue());
            }
            o.add("state", state);
        }
        return o;
    }

    // ==================== 网络读写（服务端 -> 客户端同步配方用） ====================

    @Override
    public ExtendedTerraPlateRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        int ingCount = buf.readVarInt();
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < ingCount; i++) {
            ingredients.add(Ingredient.fromNetwork(buf));
        }

        int mana = buf.readVarInt();
        ItemStack output = buf.readItem();

        PlateCondition condition = null;
        if (buf.readBoolean()) {
            condition = readConditionFromNetwork(buf);
        }

        int transformCount = buf.readVarInt();
        List<BlockTransform> transforms = new ArrayList<>();
        for (int i = 0; i < transformCount; i++) {
            PlatePosition pos = readPositionFromNetwork(buf);
            BlockMatch target = readBlockMatchFromNetwork(buf);
            transforms.add(new BlockTransform(pos, target));
        }

        BlockMatch transformAll = buf.readBoolean() ? readBlockMatchFromNetwork(buf) : null;

        return new ExtendedTerraPlateRecipe(id, ingredients, mana, output, condition, transforms, transformAll);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, ExtendedTerraPlateRecipe recipe) {
        buf.writeVarInt(recipe.getInputs().size());
        for (Ingredient ingredient : recipe.getInputs()) {
            ingredient.toNetwork(buf);
        }

        buf.writeVarInt(recipe.getMana());
        buf.writeItem(recipe.getOutput());

        PlateCondition condition = recipe.getCondition();
        buf.writeBoolean(condition != null);
        if (condition != null) {
            writeConditionToNetwork(buf, condition);
        }

        buf.writeVarInt(recipe.getTransforms().size());
        for (BlockTransform t : recipe.getTransforms()) {
            writePositionToNetwork(buf, t.position());
            writeBlockMatchToNetwork(buf, t.target());
        }

        buf.writeBoolean(recipe.getTransformAll() != null);
        if (recipe.getTransformAll() != null) {
            writeBlockMatchToNetwork(buf, recipe.getTransformAll());
        }
    }

    private PlateCondition readConditionFromNetwork(FriendlyByteBuf buf) {
        LayoutType layout = LayoutType.fromJsonName(buf.readUtf());
        boolean hasLayoutBlock = buf.readBoolean();
        BlockMatch layoutBlock = hasLayoutBlock ? readBlockMatchFromNetwork(buf) : null;

        BlockMatch center = buf.readBoolean() ? readBlockMatchFromNetwork(buf) : null;
        BlockMatch corners = buf.readBoolean() ? readBlockMatchFromNetwork(buf) : null;
        BlockMatch edges = buf.readBoolean() ? readBlockMatchFromNetwork(buf) : null;

        int excludeCount = buf.readVarInt();
        List<PlatePosition> exclude = new ArrayList<>();
        for (int i = 0; i < excludeCount; i++) {
            exclude.add(readPositionFromNetwork(buf));
        }

        int explicitCount = buf.readVarInt();
        List<PlateCondition.PositionRequirement> explicit = new ArrayList<>();
        for (int i = 0; i < explicitCount; i++) {
            PlatePosition pos = readPositionFromNetwork(buf);
            BlockMatch block = readBlockMatchFromNetwork(buf);
            explicit.add(new PlateCondition.PositionRequirement(pos, block));
        }

        return new PlateCondition(layout, layoutBlock, center, corners, edges, exclude, explicit);
    }

    private void writeConditionToNetwork(FriendlyByteBuf buf, PlateCondition condition) {
        buf.writeUtf(condition.getLayout().getJsonName());
        buf.writeBoolean(condition.getLayoutBlock() != null);
        if (condition.getLayoutBlock() != null) {
            writeBlockMatchToNetwork(buf, condition.getLayoutBlock());
        }
        buf.writeBoolean(condition.getCenterBlock() != null);
        if (condition.getCenterBlock() != null) {
            writeBlockMatchToNetwork(buf, condition.getCenterBlock());
        }
        buf.writeBoolean(condition.getCornerBlocks() != null);
        if (condition.getCornerBlocks() != null) {
            writeBlockMatchToNetwork(buf, condition.getCornerBlocks());
        }
        buf.writeBoolean(condition.getEdgeBlocks() != null);
        if (condition.getEdgeBlocks() != null) {
            writeBlockMatchToNetwork(buf, condition.getEdgeBlocks());
        }
        buf.writeVarInt(condition.getExclude().size());
        for (PlatePosition p : condition.getExclude()) {
            writePositionToNetwork(buf, p);
        }
        buf.writeVarInt(condition.getExplicit().size());
        for (PlateCondition.PositionRequirement req : condition.getExplicit()) {
            writePositionToNetwork(buf, req.position());
            writeBlockMatchToNetwork(buf, req.block());
        }
    }

    private PlatePosition readPositionFromNetwork(FriendlyByteBuf buf) {
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int z = buf.readVarInt();
        validatePosition(x, y, z); // 与 JSON 读取一致的 3x3 范围校验
        return new PlatePosition(x, y, z);
    }

    private void writePositionToNetwork(FriendlyByteBuf buf, PlatePosition pos) {
        buf.writeVarInt(pos.x());
        buf.writeVarInt(pos.y());
        buf.writeVarInt(pos.z());
    }

    private BlockMatch readBlockMatchFromNetwork(FriendlyByteBuf buf) {
        // 第一位标记：0=方块匹配，1=流体匹配
        boolean isFluid = buf.readBoolean();
        if (isFluid) {
            String fluidId = buf.readUtf();
            // source/flowing 限定符（1.0.12）
            boolean src = buf.readBoolean();
            boolean flow = buf.readBoolean();
            return new BlockMatch(null, null, BlockMatch.parseFluid(fluidId), src, flow);
        }
        String id = buf.readUtf();
        int propCount = buf.readVarInt();
        Map<String, String> props = new HashMap<>();
        for (int i = 0; i < propCount; i++) {
            props.put(buf.readUtf(), buf.readUtf());
        }
        return new BlockMatch(BlockMatch.parseBlock(id), props);
    }

    private void writeBlockMatchToNetwork(FriendlyByteBuf buf, BlockMatch match) {
        buf.writeBoolean(match.isFluidMatch());
        if (match.isFluidMatch()) {
            buf.writeUtf(match.getFluidId().toString());
            buf.writeBoolean(match.isSourceOnly());
            buf.writeBoolean(match.isFlowingOnly());
            return;
        }
        buf.writeUtf(match.getBlockId().toString());
        buf.writeVarInt(match.getStateProperties().size());
        for (Map.Entry<String, String> e : match.getStateProperties().entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }
}
