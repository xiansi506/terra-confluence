package com.example.terraplateextension.mixin;

import com.example.terraplateextension.TerraPlateExtension;
import com.example.terraplateextension.config.ModConfig;
import com.example.terraplateextension.event.PlateCraftEvent;
import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import com.example.terraplateextension.recipe.ModRecipeTypes;
import com.example.terraplateextension.recipe.PlateCondition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.api.internal.VanillaPacketDispatcher;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.network.EffectType;
import vazkii.botania.network.clientbound.BotaniaEffectPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.List;

/**
 * 挂钩 Botania 1.20.1 泰拉凝聚板（TerrestrialAgglomerationPlateBlockEntity）。
 *
 * <p>Botania 1.20.1 的凝聚板机制：
 * <ul>
 *   <li>合成是 <b>tick 驱动</b> 的：{@code serverTick} 每 tick 检查板上物品；</li>
 *   <li>魔力通过<b>火花（spark）</b>供给（ManaReceiver + 火花网络）；</li>
 *   <li>默认要求下方 3x3 活石/闪长石多方块平台（hasValidPlatform），平台无效则整个 tick 直接返回。</li>
 * </ul>
 *
 * <p>本模组在 {@code serverTick} 入口注入（cancellable）：
 * <ol>
 *   <li>检查扩展配方（terra_plate_extension:terra_plate）：物品匹配 + 下方方块条件满足 + mana 足够
 *       → 直接合成并 {@code cancel}，阻断原版逻辑（包括原版平台检查，由配方的 below 条件替代）；</li>
 *   <li>条件满足但 mana 不足 → 手动触发一次火花魔力转移（自定义平台下原版不会供能），
 *       然后不取消，让原版 tick 继续；</li>
 *   <li>物品匹配但条件不满足 → 向附近玩家发送提示，不打断原版流程（兼容原版配方）。</li>
 * </ol>
 *
 * <p><b>火花供能的关键修复</b>：Botania 的凝聚板只有「物品匹配原版配方」时才处于激活态
 * （{@code isActive} → {@code areIncomingTranfersDone()} 返回 false、{@code isFull()} 返回 false），
 * 火花才会向它转移魔力。自定义配方永远匹配不到原版配方管理器，导致：
 * <ul>
 *   <li>{@code areIncomingTranfersDone()} 恒为 true → filterTransfers 把板子火花从池子火花的
 *       outgoingTransfers 中删除，魔力永远进不来；</li>
 *   <li>{@code isFull()} 恒为 true、{@code getAvailableSpaceForMana()} 恒为 0 → 转移循环直接跳过；</li>
 *   <li>{@code serverTick} 末尾的空闲掉魔 {@code receiveMana(-1000)} 每 tick 扣 1000 魔力。</li>
 * </ul>
 * 本混入分别注入这三个方法（有扩展配方待合成时返回「可接收魔力」），并重定向空闲掉魔，
 * 使扩展配方也能通过<b>原版火花机制、按原版传输速率</b>获得魔力。
 *
 * <p>注意：目标方法均属于 Botania（模组类不做混淆，不在混淆映射表中），
 * 因此所有注入点（{@link Inject} / {@link Redirect}）都使用 {@code remap = false}，
 * 运行时按原名注入即可。
 */
@Mixin(TerrestrialAgglomerationPlateBlockEntity.class)
public abstract class TerrestrialAgglomerationPlateBlockEntityMixin {

    /**
     * 在 serverTick 入口注入：处理扩展配方的合成。
     * 使用完整方法描述符以便注解处理器精确定位；
     * remap = false：目标方法属于 Botania（模组类永不混淆），无需重映射。
     */
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lvazkii/botania/common/block/block_entity/TerrestrialAgglomerationPlateBlockEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void terraPlateExtension$onServerTick(Level level, BlockPos pos,
                                                         net.minecraft.world.level.block.state.BlockState state,
                                                         TerrestrialAgglomerationPlateBlockEntity plate,
                                                         CallbackInfo ci) {
        if (level.isClientSide) {
            return; // 服务端逻辑
        }
        List<ItemStack> items = collectItems(plate);
        if (items.isEmpty()) {
            return;
        }
        // [调试] 板上检测到物品
        TerraPlateExtension.LOGGER.info("[TPE][调试] serverTick 注入生效！板上物品 {} 个: {}", items.size(),
                items.stream().map(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem())
                        + " x" + s.getCount()).toList());

        for (ExtendedTerraPlateRecipe recipe : extendedRecipes(level)) {
            // 1) 物品集合不匹配 -> 跳过
            if (!recipe.matchesStacks(items)) {
                continue;
            }
            // [调试] 物品匹配成功
            TerraPlateExtension.LOGGER.info("[TPE][调试] 配方 {} 物品匹配成功", recipe.getId());
            // 2) 物品匹配但下方方块条件不满足 -> 明确反馈（不打断原版流程）
            //    朝向无关匹配：自动尝试配方图案的 4 种旋转（0/90/180/270°），任一旋转匹配即通过
            int rotation = recipe.findMatchingRotation(level, pos);
            if (rotation < 0) {
                // [调试] 逐个位置检查，打印不满足的具体位置
                for (PlateCondition.PositionRequirement req : recipe.getResolvedRequirements()) {
                    net.minecraft.core.BlockPos p = req.position().resolve(pos);
                    net.minecraft.world.level.block.state.BlockState actual = level.getBlockState(p);
                    boolean matches = req.block().isFluidMatch()
                            ? req.block().matchesFluid(level.getFluidState(p)) : req.block().matches(actual);
                    if (!matches) {
                        TerraPlateExtension.LOGGER.info("[TPE][调试] 位置({},{},{}) 需要 {}，实际方块 {}",
                                req.position().x(), req.position().y(), req.position().z(), req.block(),
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual.getBlock()));
                    }
                }
                sendConditionFeedback(level, pos);
                continue;
            }
            // 3) 条件满足：通过原版火花机制供能（保留原版火花传输速率 1000 mana/火花/次）
            //    手动注册一次火花转移（自定义配方下原版 serverTick 因 getCurrentRecipe 为 null
            //    不会注册；下方 areIncomingTranfersDone/isFull/getAvailableSpaceForMana 的注入
            //    保证转移不会被 filterTransfers 过滤掉）。
            transferManaViaSparks(level, pos, plate);
            TerraPlateExtension.LOGGER.info("[TPE][调试] 条件满足, mana={}/{}", plate.getCurrentMana(), recipe.getMana());
            // 与原版一致：充能中（mana > 0）每 tick 发送 TERRA_PLATE 粒子效果并同步 mana 给客户端
            if (plate.getCurrentMana() > 0) {
                VanillaPacketDispatcher.dispatchTEToNearbyPlayers(plate);
                sendTerraPlateEffect(level, pos,
                        (float) plate.getCurrentMana() / Math.max(1, recipe.getMana()));
            }
            if (plate.getCurrentMana() < recipe.getMana()) {
                continue; // 魔力不足：不取消，让原版 tick 继续（也能继续供能）
            }
            // 4) 全部满足：抛出"合成前"事件，允许其他模组取消
            PlateCraftEvent.Pre pre = new PlateCraftEvent.Pre(level, pos, plate, recipe, items);
            if (MinecraftForge.EVENT_BUS.post(pre)) {
                continue;
            }
            // 5) 执行合成（下方方块转化按匹配到的旋转角度进行）
            TerraPlateExtension.LOGGER.info("[TPE][调试] 开始合成: {}（旋转 {}°）", recipe.getOutput(), rotation * 90);
            craftExtended(level, pos, recipe, plate, rotation);
            TerraPlateExtension.LOGGER.info("[TPE][调试] 合成完成！");
            // 6) 抛出"合成后"事件
            MinecraftForge.EVENT_BUS.post(new PlateCraftEvent.Post(level, pos, plate, recipe));
            // 7) 阻断原版逻辑（含平台检查），本配方已由我们处理
            ci.cancel();
            return;
        }
    }

    /** 收集凝聚板上所有物品（复用原版私有方法 getItems(List)，规则与原版 serverTick 完全一致） */
    private static List<ItemStack> collectItems(TerrestrialAgglomerationPlateBlockEntity plate) {
        TerrestrialAgglomerationPlateBlockEntityAccessor accessor =
                (TerrestrialAgglomerationPlateBlockEntityAccessor) plate;
        return accessor.terraPlateExtension$getItems(accessor.terraPlateExtension$getItemEntities());
    }

    /** 获取所有扩展配方 */
    private static List<ExtendedTerraPlateRecipe> extendedRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TERRA_PLATE.get());
    }

    /**
     * 手动触发一次火花魔力转移（与 Botania 原版 serverTick 中的逻辑一致：
     * SparkHelper.getSparksAround + ManaSpark.registerTransfer，仅把附近「魔力池上的火花」
     * 注册为向本板供能）。
     *
     * <p>为什么要手动注册：原版只对「匹配 Botania 配方」的物品做这一步（getCurrentRecipe != null），
     * 自定义配方永远走不到；而我们手动注册的转移，配合下方 areIncomingTranfersDone/isFull/
     * getAvailableSpaceForMana 的注入，会通过 filterTransfers 的检查并在火花实体 tick 中
     * 按原版速率（每火花每次最多 1000 mana）转移到板上。
     */
    private static void transferManaViaSparks(Level level, BlockPos pos,
                                              TerrestrialAgglomerationPlateBlockEntity plate) {
        ManaSpark spark = plate.getAttachedSpark();
        // [调试] 检查火花是否放好
        TerraPlateExtension.LOGGER.info("[TPE][调试] 凝聚板火花: {}", spark);
        if (spark == null) {
            return;
        }
        DyeColor network = spark.getNetwork();
        var nearby = SparkHelper.getSparksAround(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, network);
        // [调试] 逐个打印附近火花的接收器与魔力，便于排查
        TerraPlateExtension.LOGGER.info("[TPE][调试] 附近火花 {} 个（网络 {}）:", nearby.size(), network);
        for (ManaSpark s : nearby) {
            ManaReceiver r = s.getAttachedManaReceiver();
            TerraPlateExtension.LOGGER.info("[TPE][调试]   - 火花 {} -> 接收器: {} , mana={}",
                    s, r == null ? "null" : r.getClass().getSimpleName(),
                    r == null ? -1 : r.getCurrentMana());
        }
        for (ManaSpark s : nearby) {
            // 与原版一致：只让「魔力池上的火花」向本板供能；排除板子自己的火花
            if (s != spark && s.getAttachedManaReceiver() instanceof ManaPool) {
                s.registerTransfer(spark);
            }
        }
    }

    /**
     * 查找板上物品匹配的扩展配方（不限下方条件；供能阶段不要求平台就绪）。
     * 返回 null 表示没有待合成的扩展配方（此时板子保持原版行为）。
     */
    private static ExtendedTerraPlateRecipe pendingRecipe(TerrestrialAgglomerationPlateBlockEntity plate) {
        Level level = plate.getLevel();
        if (level == null) {
            return null;
        }
        List<ItemStack> items = collectItems(plate);
        if (items.isEmpty()) {
            return null;
        }
        for (ExtendedTerraPlateRecipe recipe : extendedRecipes(level)) {
            if (recipe.matchesStacks(items)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 混入后 {@code this} 即目标凝聚板实体。实例方法注入（areIncomingTranfersDone 等）的
     * handler 必须是实例方法（Mixin 0.8.5 要求 handler 与目标 static 修饰符一致），
     * 此方法用于把 {@code this} 转回目标类型。
     */
    @Unique
    private TerrestrialAgglomerationPlateBlockEntity terraPlateExtension$this() {
        return (TerrestrialAgglomerationPlateBlockEntity) (Object) this;
    }

    /**
     * areIncomingTranfersDone 注入：原版实现返回 {@code !isActive()}，
     * 而 isActive() 要求物品匹配 Botania 原版配方——自定义配方永远不激活，
     * 火花会认为「供能已完成」而拒绝转移。有扩展配方待合成时强制返回 false（继续供能）。
     */
    @Inject(method = "areIncomingTranfersDone()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$areIncomingTranfersDone(CallbackInfoReturnable<Boolean> cir) {
        if (pendingRecipe(terraPlateExtension$this()) != null) {
            cir.setReturnValue(false);
        }
    }

    /**
     * isFull 注入：原版实现 {@code recipe == null || mana >= recipe.getMana()}，
     * 自定义配方下 recipe 恒为 null → 恒为 true → 火花把板子当「已满」过滤掉。
     * 有扩展配方待合成时按实际需求判断（未满 → false，可继续接收）。
     */
    @Inject(method = "isFull()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$isFull(CallbackInfoReturnable<Boolean> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = pendingRecipe(plate);
        if (recipe != null) {
            cir.setReturnValue(plate.getCurrentMana() >= recipe.getMana());
        }
    }

    /**
     * getAvailableSpaceForMana 注入：原版实现 {@code recipe == null ? 0 : ...}，
     * 自定义配方下恒为 0 → 火花转移循环算出的每火花额度为 0，永远不转。
     * 有扩展配方待合成时返回「还差多少魔力」。
     */
    @Inject(method = "getAvailableSpaceForMana()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$getAvailableSpaceForMana(CallbackInfoReturnable<Integer> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = pendingRecipe(plate);
        if (recipe != null) {
            cir.setReturnValue(Math.max(0, recipe.getMana() - plate.getCurrentMana()));
        }
    }

    /**
     * serverTick 末尾空闲掉魔的拦截：原版在「平台无效或无原版配方」时每 tick
     * {@code receiveMana(-1000)}。自定义配方永远走这个分支，会把火花刚送来的魔力
     * 又扣掉。有扩展配方待合成时跳过负值掉魔（正值供能、合成时的扣魔都不受影响：
     * 合成扣魔发生在物品已被消耗之后，此时 pendingRecipe 已为 null）。
     */
    @Inject(method = "receiveMana(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$blockIdleDrain(int amount, CallbackInfo ci) {
        if (amount < 0 && pendingRecipe(terraPlateExtension$this()) != null) {
            ci.cancel(); // 有扩展配方待合成：跳过空闲掉魔，保留火花送来的魔力
        }
    }

    /**
     * getCompletion 注入（比较器信号用）：原版实现 {@code recipe = getCurrentRecipe(...)}，
     * 查的是 <b>Botania 原版配方</b>——魔改配方查不到 → recipe 为 null → 完成度恒 0，
     * 导致 {@code getComparatorLevel()} 在 {@code getCurrentMana() > 0} 时恒为 1
     * （"有魔力但进度不可见"）。
     *
     * <p>有扩展配方待合成时，改用扩展配方的 mana 需求计算完成度
     * （{@code mana / recipe.mana}，范围 0..1），使比较器信号随充能进度
     * 在 1..14 之间递增（0 仍表示板上无匹配物品/配方）。
     */
    @Inject(method = "getCompletion()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$getCompletion(CallbackInfoReturnable<Float> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = pendingRecipe(plate);
        if (recipe != null) {
            int need = recipe.getMana();
            float completion = need <= 0 ? 0.0F : (float) plate.getCurrentMana() / need;
            cir.setReturnValue(completion);
        }
    }

    /** 执行扩展合成：消耗物品、扣 mana、生成产物、转化下方方块、播放音效 */
    private static void craftExtended(Level level, BlockPos pos, ExtendedTerraPlateRecipe recipe,
                                      TerrestrialAgglomerationPlateBlockEntity plate, int rotation) {
        // 0) 找"扔出原料的玩家"（与原版 getCraftingPlayer 一致：找最后一个投料者）
        //    必须在 discard 前做（物品实体还在时才能拿到 owner）
        AABB box = new AABB(pos.offset(-1, -1, -1), pos.offset(2, 3, 2));
        var toDiscard = level.getEntitiesOfClass(ItemEntity.class, box);
        Player craftingPlayer = getCraftingPlayer(toDiscard);
        TerraPlateExtension.LOGGER.info("[TPE][调试] 合成清理: 区域 {} 内物品实体 {} 个, 投料玩家={}",
                box, toDiscard.size(), craftingPlayer == null ? "null" : craftingPlayer.getName().getString());

        // 1) 消耗凝聚板上的所有物品实体
        //    原版 getItems() 扫描 AABB(pos, pos.offset(1,1,1))（即 pos..pos+2 的 2x2x2 空间）。
        //    凝聚板方块无碰撞箱，物品会穿过板子落到下方平台顶面（y 约 pos.y），
        //    因此清理范围必须从 pos.y-1 起算（x/z 各扩 1 格余量），否则删不到原材料。
        for (ItemEntity item : toDiscard) {
            item.discard();
        }

        // 2) 扣除 mana（receiveMana 为 Botania 公开 API，内部为 mana = max(0, mana + amount)）
        plate.receiveMana(-recipe.getMana());

        // 3) 生成产物（与原版一致的位置）
        ItemStack out = recipe.getOutput().copy();
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, out));

        // 3.5) 合成归属（与原版一致）：给投料玩家解锁配方 + 触发"合成物品"统计
        //      ——魔改配方也应像原版凝聚板配方一样计入玩家进度/配方书/成就
        if (craftingPlayer != null) {
            craftingPlayer.awardRecipes(List.of(recipe));
            out.onCraftedBy(level, craftingPlayer, out.getCount());
        }

        // 4) 下方方块转化（setBlock 带 UPDATE_CLIENTS 标志，自动同步客户端；
        //    位置按匹配到的旋转角度旋转，保证转化落在实际摆放的方块上）
        recipe.applyTransforms(level, pos, rotation);

        // 5) 原版合成特效：TERRA_PLATE 粒子（满充能）+ 原版合成音效 terrasteelCraft
        sendTerraPlateEffect(level, pos, 1.0F);
        level.playSound(null, pos, BotaniaSounds.terrasteelCraft, SoundSource.BLOCKS, 1.0F, 1.0F);

        TerraPlateExtension.LOGGER.debug("扩展泰拉凝聚板合成完成: {} -> {} @ {}", recipe.getInputs(), recipe.getOutput(), pos);
    }

    /**
     * 找"扔出板上物品的玩家"（与原版 getCraftingPlayer 语义一致）：
     * 物品实体有 owner 字段（谁把它扔出来的），充能期间物品一直躺在板上，
     * 因此只要 owner 是玩家即可归属（无需处于拾取冷却——充能数十秒后冷却早过了）。
     * 找不到返回 null。
     */
    private static Player getCraftingPlayer(List<ItemEntity> itemEntities) {
        for (ItemEntity item : itemEntities) {
            Entity e = item.getOwner();
            if (e instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** 发送 Botania 原版 TERRA_PLATE 效果包（板上粒子动画），参数与原版 serverTick 一致 */
    private static void sendTerraPlateEffect(Level level, BlockPos pos, float completion) {
        XplatAbstractions.INSTANCE.sendToNear(level, pos,
                new BotaniaEffectPacket(EffectType.TERRA_PLATE, pos.getX(), pos.getY(), pos.getZ(),
                        new int[]{Float.floatToIntBits(completion)}));
    }

    /** 向附近玩家发送"下方方块条件不满足"提示（按配置节流，避免每 tick 刷屏） */
    private static void sendConditionFeedback(Level level, BlockPos pos) {
        if (!ModConfig.FEEDBACK_ENABLED.get()) {
            return;
        }
        long time = level.getGameTime();
        if (time % ModConfig.FEEDBACK_INTERVAL_TICKS.get() != 0) {
            return;
        }
        int radius = ModConfig.FEEDBACK_RADIUS.get();
        double r2 = (double) radius * radius;
        Component msg = Component.translatable("message." + TerraPlateExtension.MOD_ID + ".condition_fail")
                .withStyle(ChatFormatting.RED);
        for (var player : level.players()) {
            if (player instanceof ServerPlayer sp
                    && sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) {
                sp.displayClientMessage(msg, true); // actionbar 提示
            }
        }
    }
}
