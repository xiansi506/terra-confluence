package com.example.terraplateextension.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * 对"某个方块/流体"的匹配规则。
 *
 * <p>支持两种匹配目标：
 * <ul>
 *   <li><b>方块</b>：方块 ID + 可选的方块状态属性；</li>
 *   <li><b>流体</b>：流体 ID（如 {@code thermal:resonant_ender}、{@code minecraft:water}），
 *       匹配时检查目标位置的流体状态是否为本流体。</li>
 * </ul>
 *
 * <p>JSON 支持写法：
 * <pre>
 *   "block": "minecraft:stone"                          // 任意状态
 *   "block": { "id": "minecraft:redstone_lamp", "state": { "lit": "false" } }
 *   "fluid": "thermal:resonant_ender"                    // 要求是某流体（不检查方块）
 *   "block": { "id": "minecraft:water" }                 // 水既是方块也是流体，按方块匹配
 * </pre>
 */
public class BlockMatch {

    /** 方块匹配目标（可为 null——当使用流体匹配时） */
    @Nullable private final Block block;
    @Nullable private final ResourceLocation blockId;
    /** 属性名 -> 属性值（字符串形式，如 "lit" -> "false"） */
    private final Map<String, String> stateProperties;
    /** 流体匹配目标（可为 null——当使用方块匹配时） */
    @Nullable private final ResourceLocation fluidId;
    @Nullable private final Fluid fluid;
    /**
     * 流体是否必须是<b>源头</b>（1.0.12）。
     *
     * <p>{@code false} + {@link #flowingOnly} 为 {@code false} 时表示"水源或流动都行"。
     */
    private final boolean sourceOnly;
    /** 流体是否必须是<b>流动</b>的（非源头，1.0.12）。 */
    private final boolean flowingOnly;

    public BlockMatch(@Nullable Block block, @Nullable Map<String, String> stateProperties,
                      @Nullable ResourceLocation fluidId) {
        this(block, stateProperties, fluidId, false, false);
    }

    /**
     * 完整构造（1.0.12 起支持"只认水源 / 只认流动"）。
     *
     * <p>为什么要区分：流体种类使用 {@code Fluid.isSame} 比较源头与流动变体，
     * 因此水源方块和流动的水<b>都能</b>满足条件。想让某个位置"必须正在流动"，
     * 就得额外看 {@link FluidState#isSource()}。
     *
     * @param sourceOnly  只认源头（水源方块）
     * @param flowingOnly 只认流动（非源头）；与 {@code sourceOnly} 互斥
     */
    public BlockMatch(@Nullable Block block, @Nullable Map<String, String> stateProperties,
                      @Nullable ResourceLocation fluidId, boolean sourceOnly, boolean flowingOnly) {
        if (block == null && fluidId == null) {
            throw new IllegalArgumentException("BlockMatch 必须指定 block 或 fluid 之一");
        }
        if (sourceOnly && flowingOnly) {
            throw new IllegalArgumentException("source 与 flowing 不能同时为 true");
        }
        if ((sourceOnly || flowingOnly) && fluidId == null) {
            throw new IllegalArgumentException("source/flowing 只能用于流体条件（fluid），不能用于方块条件");
        }
        this.block = block;
        this.blockId = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
        this.stateProperties = stateProperties == null ? Map.of() : Map.copyOf(stateProperties);
        this.fluidId = fluidId;
        this.fluid = fluidId == null ? null : ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluidId != null && this.fluid == null) {
            throw new IllegalArgumentException("未知的流体: " + fluidId);
        }
        this.sourceOnly = sourceOnly;
        this.flowingOnly = flowingOnly;
    }

    /** 纯方块匹配构造（兼容旧代码） */
    public BlockMatch(Block block, @Nullable Map<String, String> stateProperties) {
        this(block, stateProperties, null);
    }

    @Nullable
    public Block getBlock() {
        return block;
    }

    @Nullable
    public ResourceLocation getBlockId() {
        return blockId;
    }

    public Map<String, String> getStateProperties() {
        return stateProperties;
    }

    @Nullable
    public ResourceLocation getFluidId() {
        return fluidId;
    }

    @Nullable
    public Fluid getFluid() {
        return fluid;
    }

    /** 是否为流体匹配目标 */
    public boolean isFluidMatch() {
        return fluidId != null;
    }

    /** 是否要求流体必须是源头（1.0.12） */
    public boolean isSourceOnly() {
        return sourceOnly;
    }

    /** 是否要求流体必须是流动的（非源头，1.0.12） */
    public boolean isFlowingOnly() {
        return flowingOnly;
    }

    /**
     * 判断给定的流体状态是否匹配本规则（仅流体匹配时使用）。
     *
     * <p>三步判定（1.0.12 起）：
     * <ol>
     *   <li>使用 {@code Fluid.isSame} 比较流体种类，接受其源头与流动变体；</li>
     *   <li>{@link #sourceOnly} 时还要求 {@link FluidState#isSource()}（必须是源头）；</li>
     *   <li>{@link #flowingOnly} 时还要求<b>不是</b>源头（必须正在流动）。</li>
     * </ol>
     * 两个标志都不设时行为与旧版一致：只看种类，水流源/流动都能满足。
     */
    public boolean matchesFluid(FluidState fluidState) {
        if (fluid == null || fluidState.isEmpty()) {
            return false;
        }
        if (!fluid.isSame(fluidState.getType())) {
            return false;
        }
        if (sourceOnly && !fluidState.isSource()) {
            return false;
        }
        if (flowingOnly && fluidState.isSource()) {
            return false;
        }
        return true;
    }

    /** 判断给定的方块状态是否匹配本规则（仅方块匹配时使用） */
    public boolean matches(BlockState state) {
        if (block == null) {
            return false;
        }
        if (state.getBlock() != block) {
            return false;
        }
        for (Map.Entry<String, String> entry : stateProperties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                // 该方块没有这个属性
                return false;
            }
            String want = entry.getValue();
            boolean found = property.getPossibleValues().stream()
                    .anyMatch(v -> v.toString().equals(want) && state.getValue(property).equals(v));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取该规则作为合成结果时应放入世界的方块状态。
     *
     * <p>流体不能调用 {@code getBlock().defaultBlockState()}：流体匹配规则本身没有
     * 方块对象。这里把源头/流动态转换成对应的液体方块状态，确保
     * {@code result_blocks} 和 {@code transform_all} 都能写入液体。
     */
    public BlockState placementState() {
        if (fluid != null) {
            FluidState state;
            if (fluid instanceof FlowingFluid flowing) {
                // Level 7 is the usual full-height flowing liquid state in 1.20.1.
                state = flowingOnly ? flowing.getFlowing(7, false) : flowing.getSource(false);
            } else {
                // Custom non-flowing fluids expose only their default state.
                state = fluid.defaultFluidState();
            }
            return state.createLegacyBlock();
        }
        if (block == null) {
            throw new IllegalStateException("BlockMatch 没有可放置的方块或流体: " + this);
        }
        return block.defaultBlockState();
    }

    /** 解析方块 ID 字符串；未知 ID 抛出异常（配方数据错误） */
    public static Block parseBlock(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("无效的方块 ID: " + id);
        }
        if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
            throw new IllegalArgumentException("未知的方块: " + id);
        }
        return BuiltInRegistries.BLOCK.get(rl);
    }

    /** 解析流体 ID 字符串；未知 ID 抛出异常（配方数据错误） */
    public static ResourceLocation parseFluid(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            throw new IllegalArgumentException("无效的流体 ID: " + id);
        }
        if (!ForgeRegistries.FLUIDS.containsKey(rl)) {
            throw new IllegalArgumentException("未知的流体: " + id);
        }
        return rl;
    }

    @Override
    public String toString() {
        if (fluidId != null) {
            String suffix = sourceOnly ? "(source)" : flowingOnly ? "(flowing)" : "";
            return "fluid:" + fluidId + suffix;
        }
        return stateProperties.isEmpty() ? blockId.toString() : blockId + stateProperties.toString();
    }
}
