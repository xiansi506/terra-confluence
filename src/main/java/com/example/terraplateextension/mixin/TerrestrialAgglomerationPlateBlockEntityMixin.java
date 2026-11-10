package com.example.terraplateextension.mixin;

import com.example.terraplateextension.TerraPlateExtension;
import com.example.terraplateextension.config.ModConfig;
import com.example.terraplateextension.event.PlateCraftEvent;
import com.example.terraplateextension.recipe.ExtendedTerraPlateRecipe;
import com.example.terraplateextension.recipe.ModRecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.api.internal.VanillaPacketDispatcher;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.network.EffectType;
import vazkii.botania.network.clientbound.BotaniaEffectPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.List;

/** 扩展凝聚板配方，同时复用 Botania 的物品检测、火花供能和合成效果。 */
@Mixin(TerrestrialAgglomerationPlateBlockEntity.class)
public abstract class TerrestrialAgglomerationPlateBlockEntityMixin {

    @Unique
    private ExtendedTerraPlateRecipe terraPlateExtension$vetoedRecipe;

    @Shadow(remap = false)
    private static Player getCraftingPlayer(List<ItemEntity> itemEntities) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private boolean isActive() {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private boolean hasValidPlatform() {
        throw new AssertionError();
    }

    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lvazkii/botania/common/block/block_entity/TerrestrialAgglomerationPlateBlockEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void terraPlateExtension$onServerTick(Level level, BlockPos pos, BlockState state,
                                                        TerrestrialAgglomerationPlateBlockEntity plate,
                                                        CallbackInfo ci) {
        if (level.isClientSide) {
            return;
        }
        var mixin = (TerrestrialAgglomerationPlateBlockEntityMixin) (Object) plate;
        mixin.terraPlateExtension$vetoedRecipe = null;
        TerrestrialAgglomerationPlateBlockEntityAccessor accessor =
                (TerrestrialAgglomerationPlateBlockEntityAccessor) plate;
        List<ItemEntity> ingredients = accessor.terraPlateExtension$getItemEntities().stream()
                .filter(item -> !item.getItem().isEmpty())
                .toList();
        List<ItemStack> items = accessor.terraPlateExtension$getItems(ingredients);
        ExtendedTerraPlateRecipe recipe = findEligibleRecipe(level, pos, items, true);
        if (recipe == null) {
            return;
        }

        int mana = plate.getCurrentMana();
        if (mana > 0) {
            VanillaPacketDispatcher.dispatchTEToNearbyPlayers(plate);
            sendTerraPlateEffect(level, pos, completion(mana, recipe.getMana()));
        }
        if (mana < recipe.getMana()) {
            transferManaViaSparks(level, pos, plate);
            return;
        }

        List<ItemStack> beforeEvent = items.stream().map(ItemStack::copy).toList();
        if (MinecraftForge.EVENT_BUS.post(new PlateCraftEvent.Pre(level, pos, plate, recipe, items))) {
            mixin.terraPlateExtension$vetoedRecipe = recipe;
            return;
        }

        if (checkedRotation(level, pos, plate, recipe, ingredients, items, beforeEvent) < 0) {
            return;
        }

        // 先确认所有产物都能生成，再消耗材料和魔力。其他模组可以取消生成或改动投料。
        ci.cancel();
        List<ItemEntity> results = new ArrayList<>();
        boolean committed = false;
        try {
            if (!spawnOutputs(level, pos, recipe, results)) {
                return;
            }
            int rotation = checkedRotation(level, pos, plate, recipe, ingredients, items, beforeEvent);
            if (rotation < 0 || results.stream().anyMatch(item -> item.isRemoved() || item.getItem().isEmpty())) {
                return;
            }
            committed = true;
            craftExtended(level, pos, recipe, plate, rotation, ingredients, results);
            MinecraftForge.EVENT_BUS.post(new PlateCraftEvent.Post(level, pos, plate, recipe));
        } finally {
            if (!committed) {
                results.forEach(ItemEntity::discard);
            }
        }
    }

    @Unique
    private static int checkedRotation(Level level, BlockPos pos, TerrestrialAgglomerationPlateBlockEntity plate,
                                       ExtendedTerraPlateRecipe recipe, List<ItemEntity> ingredients,
                                       List<ItemStack> items, List<ItemStack> beforeEvent) {
        if (plate.isRemoved() || level.getBlockEntity(pos) != plate || plate.getCurrentMana() < recipe.getMana()) {
            return -1;
        }
        int rotation = recipe.findMatchingRotation(level, pos);
        var accessor = (TerrestrialAgglomerationPlateBlockEntityAccessor) plate;
        return rotation >= 0 && ingredientsUnchanged(accessor, ingredients, items, beforeEvent) ? rotation : -1;
    }

    @Unique
    private static boolean spawnOutputs(Level level, BlockPos pos, ExtendedTerraPlateRecipe recipe,
                                        List<ItemEntity> results) {
        ItemStack output = recipe.getOutput();
        int stackLimit = recipe.getOutputStackSize();
        for (int remaining = output.getCount(); remaining > 0; remaining -= stackLimit) {
            ItemStack stack = output.copyWithCount(Math.min(remaining, stackLimit));
            var entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
            results.add(entity);
            if (!level.addFreshEntity(entity) || entity.isRemoved() || entity.getItem().isEmpty()) {
                return false;
            }
        }
        return !results.isEmpty();
    }

    // Botania 在此扣除空闲凝聚板的 1000 mana，随后立即返回。
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lvazkii/botania/common/block/block_entity/TerrestrialAgglomerationPlateBlockEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lvazkii/botania/common/block/block_entity/TerrestrialAgglomerationPlateBlockEntity;receiveMana(I)V"),
            cancellable = true, remap = false, require = 1, allow = 1)
    private static void terraPlateExtension$skipIdleDrain(Level level, BlockPos pos, BlockState state,
                                                           TerrestrialAgglomerationPlateBlockEntity plate,
                                                           CallbackInfo ci) {
        if (plate.getCurrentMana() > 0 && pendingRecipe(plate) != null) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean ingredientsUnchanged(TerrestrialAgglomerationPlateBlockEntityAccessor accessor,
                                                List<ItemEntity> ingredients, List<ItemStack> items,
                                                List<ItemStack> beforeEvent) {
        List<ItemEntity> liveEntities = accessor.terraPlateExtension$getItemEntities();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemEntity ingredient = ingredients.get(i);
            ItemStack stack = ingredient.getItem();
            if (stack != items.get(i) || !ItemStack.matches(stack, beforeEvent.get(i))
                    || liveEntities.stream().noneMatch(entity -> entity == ingredient)) {
                return false;
            }
        }
        return true;
    }

    /** 有多个符合条件的配方时，按配方 ID 的自然顺序选择第一个。 */
    @Unique
    private static ExtendedTerraPlateRecipe findEligibleRecipe(Level level, BlockPos pos, List<ItemStack> items,
                                                               boolean reportConditionFailure) {
        if (items.isEmpty()) {
            return null;
        }
        ExtendedTerraPlateRecipe selected = null;
        boolean matchingItems = false;
        for (ExtendedTerraPlateRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TERRA_PLATE.get())) {
            if ((selected != null && recipe.getId().compareTo(selected.getId()) >= 0) || !recipe.matchesStacks(items)) {
                continue;
            }
            matchingItems = true;
            if (recipe.findMatchingRotation(level, pos) >= 0) {
                selected = recipe;
            }
        }
        if (reportConditionFailure && selected == null && matchingItems) {
            sendConditionFeedback(level, pos);
        }
        return selected;
    }

    /** 按 Botania 的速率和网络规则注册从魔力池到板子的火花转移。 */
    private static void transferManaViaSparks(Level level, BlockPos pos,
                                             TerrestrialAgglomerationPlateBlockEntity plate) {
        ManaSpark spark = plate.getAttachedSpark();
        if (spark == null) {
            return;
        }
        var nearby = SparkHelper.getSparksAround(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                spark.getNetwork());
        for (ManaSpark source : nearby) {
            if (source != spark && source.getAttachedManaReceiver() instanceof ManaPool) {
                source.registerTransfer(spark);
            }
        }
    }

    private static ExtendedTerraPlateRecipe pendingRecipe(TerrestrialAgglomerationPlateBlockEntity plate) {
        Level level = plate.getLevel();
        if (level == null) {
            return null;
        }
        TerrestrialAgglomerationPlateBlockEntityAccessor accessor =
                (TerrestrialAgglomerationPlateBlockEntityAccessor) plate;
        List<ItemStack> items = accessor.terraPlateExtension$getItems(accessor.terraPlateExtension$getItemEntities());
        return findEligibleRecipe(level, plate.getBlockPos(), items, false);
    }

    @Unique
    private TerrestrialAgglomerationPlateBlockEntity terraPlateExtension$this() {
        return (TerrestrialAgglomerationPlateBlockEntity) (Object) this;
    }

    @Unique
    private ExtendedTerraPlateRecipe terraPlateExtension$chargingRecipe() {
        ExtendedTerraPlateRecipe recipe = pendingRecipe(terraPlateExtension$this());
        // 扩展配方被取消后，符合条件的原版配方可按原版容量继续接收魔力。
        return recipe != null && recipe == terraPlateExtension$vetoedRecipe && hasValidPlatform() && isActive()
                ? null : recipe;
    }

    @Inject(method = "areIncomingTranfersDone()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$areIncomingTranfersDone(CallbackInfoReturnable<Boolean> cir) {
        if (terraPlateExtension$chargingRecipe() != null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isFull()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$isFull(CallbackInfoReturnable<Boolean> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = terraPlateExtension$chargingRecipe();
        if (recipe != null) {
            cir.setReturnValue(plate.getCurrentMana() >= recipe.getMana());
        }
    }

    @Inject(method = "getAvailableSpaceForMana()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$getAvailableSpaceForMana(CallbackInfoReturnable<Integer> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = terraPlateExtension$chargingRecipe();
        if (recipe != null) {
            cir.setReturnValue(Math.max(0, recipe.getMana() - plate.getCurrentMana()));
        }
    }

    @Inject(method = "getCompletion()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void terraPlateExtension$getCompletion(CallbackInfoReturnable<Float> cir) {
        TerrestrialAgglomerationPlateBlockEntity plate = terraPlateExtension$this();
        ExtendedTerraPlateRecipe recipe = terraPlateExtension$chargingRecipe();
        if (recipe != null) {
            cir.setReturnValue(completion(plate.getCurrentMana(), recipe.getMana()));
        }
    }

    @Unique
    private static float completion(int mana, int required) {
        return required <= 0 ? 0.0F : Math.min(1.0F, (float) mana / required);
    }

    private static void craftExtended(Level level, BlockPos pos, ExtendedTerraPlateRecipe recipe,
                                      TerrestrialAgglomerationPlateBlockEntity plate, int rotation,
                                      List<ItemEntity> ingredients, List<ItemEntity> results) {
        Player craftingPlayer = getCraftingPlayer(ingredients);
        for (ItemEntity ingredient : ingredients) {
            ingredient.discard();
        }
        plate.receiveMana(-recipe.getMana());

        if (craftingPlayer != null) {
            craftingPlayer.awardRecipes(List.of(recipe));
            for (ItemEntity result : results) {
                ItemStack stack = result.getItem();
                stack.onCraftedBy(level, craftingPlayer, stack.getCount());
            }
        }

        recipe.applyTransforms(level, pos, rotation);
        sendTerraPlateEffect(level, pos, 1.0F);
        level.playSound(null, pos, BotaniaSounds.terrasteelCraft, SoundSource.BLOCKS, 1.0F, 1.0F);
        TerraPlateExtension.LOGGER.debug("扩展泰拉凝聚板合成完成: {} @ {}", recipe.getId(), pos);
    }

    /** 发送 Botania 原版 TERRA_PLATE 效果包（板上粒子动画）。 */
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
