package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaPassive;
import net.jackcooper.shapeShifterCurseAddon.power.WaterSpearCraftPower;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * SSCA 各形态交互事件注册（从 SscAddon.registerMancianimaEvents 拆分而来）：
 * - 契灵：村民/商人击杀掉落、唤魔者+下界之星换图腾、敲钟触发袭击
 * - 吸血蝙蝠血雾期间禁用一切左右键交互
 * - 进化美西螈：副手持箭右键“合成”水矛
 * - SP阿努比斯：手持凋零玫瑰右键改为进食
 */
public final class SscAddonInteractionEvents {

	private SscAddonInteractionEvents() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// 1. 袭击目标死亡检测
			MancianimaPassive.onAssaultTargetDeath(entity);
			// 2. 村民/商人 被契灵击杀 → 掉落
			if (entity instanceof MerchantEntity merchant
					&& source.getAttacker() instanceof ServerPlayerEntity killer
					&& FormUtils.isForm(killer,
							FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				MancianimaPassive
						.onMerchantKilledByMancianima(merchant, killer);
			}
		});

		// 唤魔者 + 下界之星 → 2 个不死图腾
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
			if (!FormUtils.isForm(sp,
					FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return ActionResult.PASS;
			}
			// 契灵不能与村民/商人交易
			if (entity instanceof MerchantEntity) {
				// Fabric's interaction callback runs before Apoli's PlayerEntity.interact hook.
				// Resolve this existing drain power before refusing ordinary merchant trading.
				io.github.apace100.apoli.power.PowerType<?> drainType =
						io.github.apace100.apoli.power.PowerTypeRegistry.get(
								new net.minecraft.util.Identifier("my_addon", "form_familiar_fox_mancianima_villager_drain"));
				var drain = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(sp).getPower(drainType);
				if (drain instanceof io.github.apace100.apoli.power.ActionOnEntityUsePower use
						&& use.isActive() && use.shouldExecute(entity, hand, sp.getStackInHand(hand))) {
					return use.executeAction(entity, hand);
				}
				return ActionResult.FAIL;
			}
			if (!(entity instanceof EvokerEntity)) return ActionResult.PASS;
			ItemStack stack = sp.getStackInHand(hand);
			if (!stack.isOf(Items.NETHER_STAR)) return ActionResult.PASS;
			if (!sp.getAbilities().creativeMode) stack.decrement(1);
			ItemStack reward = new ItemStack(Items.TOTEM_OF_UNDYING, 2);
			if (!sp.getInventory().insertStack(reward)) {
				sp.dropItem(reward, false);
			}
			if (sp.getWorld() instanceof ServerWorld sw) {
				sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON,
						sp.getSoundCategory(), 1.0f, 1.2f);
			}
			return ActionResult.SUCCESS;
		});

		// 契灵敲钟触发村庄袭击（1 MC 天 1 次）
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
			if (!FormUtils.isForm(sp,
					FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return ActionResult.PASS;
			}
			BlockState state = world.getBlockState(hitResult.getBlockPos());
			if (!(state.getBlock() instanceof BellBlock)) return ActionResult.PASS;
			MancianimaPassive.tryTriggerAssaultByBell(sp);
			// 让钟声照常播放
			return ActionResult.PASS;
		});

		// 吸血蝙蝠血雾期间禁用一切右键交互（用物品/放方块/与生物互动/吃喝/盾牌副手等）
		UseItemCallback.EVENT.register((player, world, hand) -> {			if (player.hasStatusEffect(SscAddon.MIST_FORM)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return TypedActionResult.fail(player.getStackInHand(hand));
			}			return TypedActionResult.pass(player.getStackInHand(hand));
		});


		// 只有方块或实体未接收右键时，原版才会尝试使用副手的箭。
		UseItemCallback.EVENT.register(WaterSpearCraftPower::useArrow);
		// SP阿努比斯吃凋零玫瑰：进食效果由 custom_edible(form_anubis_wolf_sp_eat_wither_rose) + eatFood mixin 处理。
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			// SP阿努比斯手持凋零玫瑰看向方块右键时，原版会放置花；返回 FAIL 取消放置，
			// 让交互落到 use() → 由 custom_edible(form_anubis_wolf_sp_eat_wither_rose) 驱动进食(32t 读条，同吃牛排)。
			// 看向空气时不经过 UseBlockCallback，use() 直接进食，无需在此处理。
			if (hand == Hand.MAIN_HAND
					&& player.getMainHandStack().isOf(Items.WITHER_ROSE)
					&& FormUtils.isForm(player,
							FormIdentifiers.ANUBIS_WOLF_SP)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.hasStatusEffect(SscAddon.MIST_FORM)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasStatusEffect(SscAddon.MIST_FORM)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		// 同时禁用左键破坏方块
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.hasStatusEffect(SscAddon.MIST_FORM)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		// 禁用左键攻击实体（含挥剑/普攻起手动作本身）
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasStatusEffect(SscAddon.MIST_FORM)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}
}
