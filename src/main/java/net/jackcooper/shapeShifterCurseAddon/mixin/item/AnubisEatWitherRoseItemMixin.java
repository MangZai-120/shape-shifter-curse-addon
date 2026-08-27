/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 冥裁者「吃凋零玫瑰」：让原版凋零玫瑰（方块物品）在 SP阿努比斯手中变成可进食
 * （32t 读条进食动画，同吃牛排）。直接 mixin 而非 custom_edible，保证进食动画确定触发、
 * 无 5s 异步刷新延迟、主客机一致。
 *
 * - use：SP阿努比斯手持凋零玫瑰 → setCurrentHand 起手进食；其它形态放行（保留原版放置花）。
 * - getMaxUseTime / getUseAction：凋零玫瑰 → 32t + EAT（仅进食期间被读取；非阿努比斯不会起手，故不受影响）。
 * - finishUsing：SP阿努比斯服务端 → 施加阶梯凋零（首次凋零I 15s；重复吃等级+1上限凋零III，刷新20s）+ 消耗1。
 *
 * 看向方块时的默认放置由 SscAddon 的 UseBlockCallback 返回 FAIL 取消，使交互落到 use()。
 */
@Mixin(Item.class)
public abstract class AnubisEatWitherRoseItemMixin {

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseUse(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		ItemStack stack = user.getStackInHand(hand);
		if (!stack.isOf(Items.WITHER_ROSE)) return;
		if (!FormUtils.isForm(user, FormIdentifiers.ANUBIS_WOLF_SP)) return;
		user.setCurrentHand(hand);
		cir.setReturnValue(TypedActionResult.consume(stack));
	}

	@Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseMaxUseTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack.isOf(Items.WITHER_ROSE)) {
			cir.setReturnValue(32);
		}
	}

	@Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
		if (stack.isOf(Items.WITHER_ROSE)) {
			cir.setReturnValue(UseAction.EAT);
		}
	}

	@Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseFinish(ItemStack stack, World world, LivingEntity user,
			CallbackInfoReturnable<ItemStack> cir) {
		if (!stack.isOf(Items.WITHER_ROSE)) return;
		if (!(user instanceof ServerPlayerEntity sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.ANUBIS_WOLF_SP)) return;
		StatusEffectInstance current = sp.getStatusEffect(StatusEffects.WITHER);
		int amplifier;
		int duration;
		if (current == null) {
			// 首次吃 → 凋零 I，15 秒
			amplifier = 0;
			duration = 300;
		} else {
			// 重复吃 → 等级 +1（上限凋零 III = amplifier 2），刷新 duration 到 20 秒
			amplifier = Math.min(current.getAmplifier() + 1, 2);
			duration = 400;
		}
		sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, duration, amplifier));
		if (!sp.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		cir.setReturnValue(stack);
	}
}
