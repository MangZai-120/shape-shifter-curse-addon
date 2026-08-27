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

		SscAddon.WS_DBG.info("[WS] ===== DEBUG BUILD LOADED (v2): 水矛合成+最多1把 监测启用 =====");
		// SP美西螈：选中快捷栏(主手)为空 + 副手持箭 + 右键 → 消耗 1 支箭“合成”获得水矛（5 秒CD；身上最多 1 把）
		// 注：主手为空时 MC 只触发副手(OFF_HAND)交互，故用副手回调
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			boolean axo = net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isAxolotlSP(player);
			net.minecraft.item.ItemStack mainStack = player.getMainHandStack();
			net.minecraft.item.ItemStack offStack = player.getOffHandStack();
			boolean arrowCd = player.getItemCooldownManager().isCoolingDown(net.minecraft.item.Items.ARROW);
			int spearCount = 0;
			if (axo) {
				net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
				for (int i = 0; i < inv.size(); i++) {
					if (inv.getStack(i).isOf(SscAddon.WATER_SPEAR)) spearCount++;
				}
				SscAddon.WS_DBG.info("[WS] side={} hand={} main={} mainEmpty={} off={} offIsArrow={} arrowCD={} spearInInv={}",
						world.isClient() ? "CLIENT" : "SERVER", hand,
						net.minecraft.registry.Registries.ITEM.getId(mainStack.getItem()), mainStack.isEmpty(),
						net.minecraft.registry.Registries.ITEM.getId(offStack.getItem()), offStack.isOf(net.minecraft.item.Items.ARROW), arrowCd, spearCount);
			}
			if (hand != net.minecraft.util.Hand.OFF_HAND || !axo || !mainStack.isEmpty()
					|| !offStack.isOf(net.minecraft.item.Items.ARROW)) {
				return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
			}
			// 身上最多一把水矛：背包已有则不合成
			if (spearCount > 0) {
				SscAddon.WS_DBG.info("[WS][{}] BLOCKED: already has {} water_spear (max 1)", world.isClient() ? "CLIENT" : "SERVER", spearCount);
				return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
			}
			if (world.isClient()) {
				SscAddon.WS_DBG.info("[WS][CLIENT] gate-passed arrowCD={} -> {}", arrowCd, arrowCd ? "PASS(cooling)" : "SUCCESS");
				return arrowCd ? net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand))
						: net.minecraft.util.TypedActionResult.success(player.getStackInHand(hand));
			}
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
				int slot = sp.getInventory().selectedSlot;
				net.minecraft.item.ItemStack selStack = sp.getInventory().getStack(slot);
				int srvSpears = 0;
				for (int i = 0; i < sp.getInventory().size(); i++) {
					if (sp.getInventory().getStack(i).isOf(SscAddon.WATER_SPEAR)) srvSpears++;
				}
				long now = sp.getServer().getTicks();
				Long until = SscAddon.WATER_SPEAR_CRAFT_CD.get(sp.getUuid());
				boolean cooling = until != null && now < until;
				SscAddon.WS_DBG.info("[WS][SERVER] gate cooling={} now={} until={} selSlot={} selStack={} selEmpty={} srvSpears={}",
						cooling, now, until, slot,
						net.minecraft.registry.Registries.ITEM.getId(selStack.getItem()), selStack.isEmpty(), srvSpears);
				if (cooling) {
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				// 服务端二次硬校验（防御）：选中槽必须真空、且身上无水矛
				if (!selStack.isEmpty()) {
					SscAddon.WS_DBG.warn("[WS][SERVER] ABORT: 选中槽非空({})，不合成", net.minecraft.registry.Registries.ITEM.getId(selStack.getItem()));
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				if (srvSpears > 0) {
					SscAddon.WS_DBG.warn("[WS][SERVER] ABORT: 身上已有 {} 把水矛", srvSpears);
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				sp.getOffHandStack().decrement(1);
				net.minecraft.item.ItemStack spear = new net.minecraft.item.ItemStack(SscAddon.WATER_SPEAR);
				sp.getInventory().setStack(slot, spear);
				sp.getInventory().markDirty();
				SscAddon.WS_DBG.info("[WS][SERVER] >>> CRAFTED into selSlot={} ; offhandEmptyNow={} (CD改为水矛消失后触发)", slot, sp.getOffHandStack().isEmpty());
				if (sp.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
					sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
							net.minecraft.sound.SoundEvents.ITEM_BOTTLE_FILL, sp.getSoundCategory(), 0.8f, 1.0f);
					net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnWaterBurst(sw, sp.getX(), sp.getY() + 1.0, sp.getZ(), 0.5);
				}
				return net.minecraft.util.TypedActionResult.success(sp.getStackInHand(hand));
			}
			return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
		});
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
