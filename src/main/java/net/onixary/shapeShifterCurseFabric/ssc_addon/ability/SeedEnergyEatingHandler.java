/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 寄生果蝠形态吃种子机制：
 *   1. 复用原版 SSC 的 {@code shape-shifter-curse:custom_edible} power（form_bat_parasitic_fruit_seed_eat.json）
 *      把种子物品标记成可吃食物，原版吃食物动画接管"持续约 1.6 秒进度"的视觉表现；
 *   2. 玩家面对方块时 BlockItem.useOnBlock 默认会触发种植；本类用 UseBlockCallback 在玩家潜行 + 持种子 +
 *      寄生果蝠形态时直接转调 ItemStack.use(...)，绕过种植走食物分支；
 *   3. 实际"加能量 + 推配额"在 {@link #onSeedEaten(ServerPlayerEntity, ItemStack)} 中完成，由
 *      LivingEntity.eatFood 的 mixin 在吃食物完成时调用；
 *   4. 配额：每 3 分钟最多吃 8 颗，超额时红字 actionBar 提示且不进入吃食流程。
 */
public final class SeedEnergyEatingHandler {

    /** 视为可"吃"的原版种子物品集合（与 form_bat_parasitic_fruit_seed_eat.json 列表对齐）。 */
    public static final Set<net.minecraft.item.Item> EDIBLE_SEEDS = Set.of(
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.WHEAT_SEEDS,
            Items.TORCHFLOWER_SEEDS,
            Items.PITCHER_POD
    );

    /** 配额窗口：3 分钟。 */
    private static final long QUOTA_WINDOW_TICKS = 60L * 20L * 3L;
    /** 配额上限：3 分钟内最多 8 次。 */
    private static final int QUOTA_LIMIT = 8;
    /** 超额提示节流。 */
    private static final int OVER_QUOTA_HINT_COOLDOWN_TICKS = 40;

    /** 每个玩家近期完成时间戳队列（3 分钟滑动窗口配额）。 */
    private static final Map<UUID, Deque<Long>> RECENT_EATS = new HashMap<>();
    /** 上次发送"吃不下了"提示的 tick，避免每 tick 刷屏。 */
    private static final Map<UUID, Long> LAST_OVER_QUOTA_HINT_TICK = new HashMap<>();

    private SeedEnergyEatingHandler() {
    }

    public static void register() {
        // 玩家面对方块持种子时，原版会优先触发 BlockItem.useOnBlock 种植；
        // 在寄生果蝠潜行场景下，主动转调 ItemStack.use 进入食物分支
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || !EDIBLE_SEEDS.contains(stack.getItem())) return ActionResult.PASS;
            if (!hasSeedEnergyPower(player)) return ActionResult.PASS;

            // 服务端进行配额预检查；超额则红字提示并阻断
            if (player instanceof ServerPlayerEntity sp) {
                long now = sp.getWorld().getTime();
                pruneQuota(sp.getUuid(), now);
                Deque<Long> recent = RECENT_EATS.get(sp.getUuid());
                if (recent != null && recent.size() >= QUOTA_LIMIT) {
                    sendOverQuotaHint(sp, now);
                    return ActionResult.FAIL;
                }
                // 能量已满则不进入吃食流程，让玩家保留种子
                int current = PowerUtils.getResourceValue(sp, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
                int max = PowerUtils.getResourceMax(sp, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
                if (max <= 0) max = 10;
                if (current >= max) return ActionResult.FAIL;
            }
            // 转调 Item.use（基类）让 SSC 的 custom_edible mixin 把 isFood 改成 true，进入吃食物动画
            TypedActionResult<ItemStack> result = stack.use(world, player, hand);
            return result.getResult();
        });

        // 玩家面对空气直接右键时也会进入 Item.use → SSC custom_edible 食物分支；
        // 此处统一在 UseItemCallback 做配额预检查，超额时直接 FAIL，阻止进入吃食物动画
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || !EDIBLE_SEEDS.contains(stack.getItem())) return TypedActionResult.pass(stack);
            if (!hasSeedEnergyPower(player)) return TypedActionResult.pass(stack);
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);

            long now = sp.getWorld().getTime();
            pruneQuota(sp.getUuid(), now);
            Deque<Long> recent = RECENT_EATS.get(sp.getUuid());
            if (recent != null && recent.size() >= QUOTA_LIMIT) {
                sendOverQuotaHint(sp, now);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }

    /**
     * 由 LivingEntity.eatFood 的 mixin 在吃种子完成时调用。
     * @return true 表示该次吃食物属于本机制（mixin 不需要再做额外处理）。
     */
    public static boolean onSeedEaten(ServerPlayerEntity sp, ItemStack stack) {
        if (stack.isEmpty() || !EDIBLE_SEEDS.contains(stack.getItem())) return false;
        if (!hasSeedEnergyPower(sp)) return false;

        long now = sp.getWorld().getTime();
        pruneQuota(sp.getUuid(), now);

        Deque<Long> recent = RECENT_EATS.computeIfAbsent(sp.getUuid(), k -> new ArrayDeque<>());
        if (recent.size() >= QUOTA_LIMIT) {
            sendOverQuotaHint(sp, now);
            return true;
        }

        int current = PowerUtils.getResourceValue(sp, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
        int max = PowerUtils.getResourceMax(sp, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);
        if (max <= 0) max = 10;
        int newValue = MathHelper.clamp(current + 1, 0, max);
        PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, newValue);

        recent.addLast(now);
        return true;
    }

    private static void pruneQuota(UUID uuid, long now) {
        Deque<Long> recent = RECENT_EATS.get(uuid);
        if (recent == null) return;
        while (!recent.isEmpty() && now - recent.peekFirst() >= QUOTA_WINDOW_TICKS) {
            recent.pollFirst();
        }
    }

    private static void sendOverQuotaHint(ServerPlayerEntity sp, long now) {
        Long last = LAST_OVER_QUOTA_HINT_TICK.get(sp.getUuid());
        if (last != null && now - last < OVER_QUOTA_HINT_COOLDOWN_TICKS) return;
        LAST_OVER_QUOTA_HINT_TICK.put(sp.getUuid(), now);
        sp.sendMessage(Text.translatable("msg.my_addon.seed_eat_too_much")
                .formatted(Formatting.RED), true);
    }

    private static boolean hasSeedEnergyPower(PlayerEntity player) {
        try {
            return PowerHolderComponent.KEY.get(player).getPowers().stream()
                    .anyMatch(p -> p.getType().getIdentifier().equals(FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY));
        } catch (Exception e) {
            return false;
        }
    }
}
