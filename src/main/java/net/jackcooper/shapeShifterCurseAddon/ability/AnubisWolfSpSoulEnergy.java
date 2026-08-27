package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

import java.util.UUID;

/**
 * SP阿努比斯之狼 - 灵魂能量系统
 * 通过击杀/战斗积累灵魂能量（最大100点），满能量时下一次死亡领域施放将被增强：
 * - 半径 24→32
 * - 充能时间 2秒→1秒
 * - 持续时间 15秒→20秒
 * - 凋零II 替代 凋零I
 * - 自动召唤6只冥狼
 * 能量满时消耗全部能量施放增强领域。
 */
public class AnubisWolfSpSoulEnergy {

	/**
	 * 最大灵魂能量
	 */
	public static final int MAX_ENERGY = 100;

// ==================== 常量 ====================
	/**
	 * 在死亡领域内击杀获得的能量
	 */
	private static final int KILL_IN_DOMAIN_ENERGY = 20;
	/**
	 * 冥狼击杀获得的能量
	 */
	private static final int MINION_KILL_ENERGY = 10;
	/**
	 * 普通击杀获得的能量
	 */
	private static final int REGULAR_KILL_ENERGY = 5;
	/**
	 * 自身处于凋零时击杀额外获得的能量（凋零收割循环）
	 */
	private static final int WITHER_KILL_BONUS_ENERGY = 10;

	private AnubisWolfSpSoulEnergy() {
	}

// ==================== 公开接口 ====================

	/**
	 * 获取玩家当前灵魂能量
	 */
	public static int getEnergy(ServerPlayerEntity player) {
		return ResourceBars.get(player, BarKeys.ANUBIS_SOUL);
	}

	/**
	 * 增加灵魂能量（自动限制上限）。
	 * <p>修复：统一框架 maxOf 在 power 查询异常路径下可能返回 0，导致 gain 被 clamp 成 0（能量加不上，
	 * 表现为召唤物/凋零击杀失效）。此处改为直接读 VariableIntPower 的真实 max，
	 * 查不到时回退常量 {@link #MAX_ENERGY}（与旧版行为一致），保证任何时序下都能累积。
	 */
	public static void addEnergy(ServerPlayerEntity player, int amount) {
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
		if (max <= 0) {
			max = MAX_ENERGY; // power 未就绪/查询失败的回退上限（旧版行为）
		}
		int current = getEnergy(player);
		int newValue = Math.min(current + amount, max);
		if (newValue != current) {
			ResourceBars.set(player, BarKeys.ANUBIS_SOUL, newValue);
		}
	}

	/**
	 * 检查灵魂能量是否已满
	 */
	public static boolean isFullEnergy(ServerPlayerEntity player) {
		return getEnergy(player) >= ResourceBars.maxOf(player, BarKeys.ANUBIS_SOUL);
	}

	/**
	 * 消耗全部灵魂能量（施放增强领域时调用）
	 */
	public static void consumeEnergy(ServerPlayerEntity player) {
		ResourceBars.set(player, BarKeys.ANUBIS_SOUL, 0);
	}

	/**
	 * 清除玩家能量（断线/变形时）
	 */
	public static void clearPlayer(ServerPlayerEntity player) {
		if (player != null) {
			ResourceBars.set(player, BarKeys.ANUBIS_SOUL, 0);
		}
	}

	/**
	 * 设置灵魂能量（由set_mana命令调用）
	 */
	public static void setEnergy(ServerPlayerEntity player, int amount) {
		ResourceBars.set(player, BarKeys.ANUBIS_SOUL, amount);
	}

	// ==================== 框架接入：满能触发音效（BarTrigger，替代原内联判断） ====================

	/**
	 * 凋零击杀回能：受害者被阿努比斯玩家（或其冥狼）施加凋零后死于凋零 DOT 时，给施加者回能。
	 * <p>原版凋零掉血的 DamageSource 无 attacker（查不到击杀者），故沿用金沙岚的「施加时注册来源」方案：
	 * 施加凋零时记录 (受害者 → 施加者 → 过期时刻)，死亡时若查不到直接击杀者且身上有表内凋零，回溯给最近施加者。
	 */
	private static final java.util.Map<UUID, java.util.Map<UUID, Long>> WITHER_SOURCES = new java.util.concurrent.ConcurrentHashMap<>();
	/** 凋零来源过期宽限（tick）：效果到期后精简存活时间，避免残留误归因。 */
	private static final long WITHER_SOURCE_GRACE = 40L;

	/** 注册凋零来源（由 SscAddonLivingEntityMixin 在阿努比斯玩家施加凋零时调用）。 */
	public static void registerWitherSource(LivingEntity victim, ServerPlayerEntity player, int witherDurationTicks) {
		if (victim == null || player == null) return;
		long expire = victim.getWorld().getTime() + (long) witherDurationTicks + WITHER_SOURCE_GRACE;
		WITHER_SOURCES.computeIfAbsent(victim.getUuid(), k -> new java.util.concurrent.ConcurrentHashMap<>())
				.put(player.getUuid(), expire);
	}

	/** 死亡回溯：查直接击杀者无果时，若身上有未过期凋零来源，给最近的施加者回能并清理。 */
	private static void onWitherDeath(LivingEntity victim) {
		java.util.Map<UUID, Long> sources = WITHER_SOURCES.get(victim.getUuid());
		if (sources == null || sources.isEmpty()) return;
		long now = victim.getWorld().getTime();
		UUID best = null;
		long bestExpire = Long.MIN_VALUE;
		for (java.util.Map.Entry<UUID, Long> e : sources.entrySet()) {
			if (e.getValue() < now) {
				sources.remove(e.getKey());
				continue;
			}
			if (e.getValue() > bestExpire) {
				bestExpire = e.getValue();
				best = e.getKey();
			}
		}
		if (sources.isEmpty()) {
			WITHER_SOURCES.remove(victim.getUuid());
			return;
		}
		// 只回溯身上仍常驻凋零效果的（防过宽）
		if (!victim.hasStatusEffect(StatusEffects.WITHER)) return;
		PlayerEntity ownerEntity = victim.getWorld().getPlayerByUuid(best);
		if (ownerEntity instanceof ServerPlayerEntity ownerPlayer
				&& FormUtils.isForm(ownerPlayer, FormIdentifiers.ANUBIS_WOLF_SP)) {
			addEnergy(ownerPlayer, WITHER_KILL_BONUS_ENERGY);
		}
		WITHER_SOURCES.remove(victim.getUuid());
	}

	/** 满能提示音：跨入满值时播（与原 addEnergy 内联逻辑等价）。 */
	private static final net.jackcooper.shapeShifterCurseAddon.resource.BarTrigger ANUBIS_FULL_TRIGGER =
			(player, oldV, newV, max) -> {
				if (oldV < max && newV >= max) {
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.2f);
				}
			};

	static {
		// 挂到统一框架（可插拔：移除本行即去掉音效，不影响其它逻辑）
		BarKeys.ANUBIS_SOUL.addTrigger(ANUBIS_FULL_TRIGGER);
	}

// ==================== 事件注册 ====================

	/**
	 * 注册击杀事件监听器（在SscAddon.onInitialize中调用）
	 */
	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register(AnubisWolfSpSoulEnergy::onEntityDeath);
	}

	/**
	 * 实体死亡时的处理逻辑
	 */
	private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
		if (entity.getWorld().isClient()) return;

// 情况0：凋零 DOT 击杀（无 attacker 归因）——按施加来源回溯（含玩家/冥狼施加的凋零）
		if (damageSource.getAttacker() == null
				&& damageSource.isOf(net.minecraft.entity.damage.DamageTypes.WITHER)) {
			onWitherDeath(entity);
			return;
		}

// 情况1：玩家直接击杀
		if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
			if (!FormUtils.isForm(killer, FormIdentifiers.ANUBIS_WOLF_SP)) return;

// 检查击杀是否发生在死亡领域内
			if (AnubisWolfSpDeathDomain.hasActiveDomain(killer.getUuid())
					&& AnubisWolfSpDeathDomain.isInActiveDomain(killer.getUuid(), entity.getBlockPos())) {
// 增强领域范围内击杀不获取能量
				if (!AnubisWolfSpDeathDomain.isEnhancedDomain(killer.getUuid())) {
					addEnergy(killer, KILL_IN_DOMAIN_ENERGY);
				}
			} else {
				addEnergy(killer, REGULAR_KILL_ENERGY);
			}
// 凋零击杀回能：自身处于凋零时击杀额外 +10 灵魂能量
			if (killer.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER)) {
				addEnergy(killer, WITHER_KILL_BONUS_ENERGY);
			}
			return;
		}

// 情况2：冥狼击杀（AnubisWolfMinionEntity的owner获得能量）
		if (damageSource.getAttacker() instanceof AnubisWolfMinionEntity wolf) {
			java.util.UUID ownerUuid = wolf.getMinionOwnerUUID();
			if (ownerUuid == null) return;

			PlayerEntity ownerEntity = entity.getWorld().getPlayerByUuid(ownerUuid);
			if (ownerEntity instanceof ServerPlayerEntity ownerPlayer) {
				if (!FormUtils.isForm(ownerPlayer, FormIdentifiers.ANUBIS_WOLF_SP)) return;
// 增强领域范围内冥狼击杀不获取能量
				if (AnubisWolfSpDeathDomain.isEnhancedDomain(ownerUuid)
						&& AnubisWolfSpDeathDomain.isInActiveDomain(ownerUuid, entity.getBlockPos())) {
					return;
				}
				addEnergy(ownerPlayer, MINION_KILL_ENERGY);
// 凋零击杀回能：冥狼击杀时，若主人处于凋零，额外 +10 灵魂能量
				if (ownerPlayer.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER)) {
					addEnergy(ownerPlayer, WITHER_KILL_BONUS_ENERGY);
				}
			}
		}
	}
}
