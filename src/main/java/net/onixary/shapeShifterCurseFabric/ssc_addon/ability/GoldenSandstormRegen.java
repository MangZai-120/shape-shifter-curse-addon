package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 自定义回血系统
 *
 * 金沙岚禁止通过饱食度自然回血（由 GoldenSandstormHungerMixin 拦截）。
 * 仅以下方式可恢复生命值：
 *
 * 战斗内（5秒内有攻击或受击）:
 *   - 自身附加的凋零每次对敌造成伤害 → +1 HP（半颗心）
 *   - 自身造成击杀                  → +4 HP（2颗心）
 *   - 冥狼（AnubisWolfMinionEntity）造成击杀 → +3 HP（1.5颗心）
 *
 * 被动回血:
 *   - 战斗外: 每200 tick(10秒) +1 HP（半颗心）
 *   - 战斗内: 每120 tick(6秒)  +1 HP（半颗心）
 *
 * 备注:
 *   - 半颗心 = 1.0 HP, 1颗心 = 2.0 HP
 *   - 战斗状态 = 5秒(100 tick)内有主动攻击或受到伤害
 */
public final class GoldenSandstormRegen {

	/** 凋零每次伤害回血量（HP） = 半颗心 */
	private static final float WITHER_TICK_HEAL = 1.0f;
	/** 玩家击杀回血量（HP） = 2颗心 */
	private static final float KILL_HEAL = 4.0f;
	/** 冥狼击杀回血量（HP） = 1.5颗心 */
	private static final float MINION_KILL_HEAL = 3.0f;
	/** 被动回血量（HP） = 半颗心 */
	private static final float PASSIVE_HEAL = 1.0f;
	/** 战斗外被动回血间隔（tick） = 10秒 */
	private static final int PASSIVE_INTERVAL_OOC = 200;
	/** 战斗内被动回血间隔（tick） = 6秒 */
	private static final int PASSIVE_INTERVAL_IC = 120;
	/** 战斗状态持续时间（tick） = 8秒 */
	private static final long COMBAT_DURATION = 160L;
	/** 凋零源记录的额外保留时间（tick），覆盖凋零 tick 边界 */
	private static final long WITHER_SOURCE_GRACE = 40L;

	/** 玩家UUID -> 上次进入战斗的世界时间(tick) */
	private static final Map<UUID, Long> LAST_COMBAT_TICK = new ConcurrentHashMap<>();
	/** 玩家UUID -> 上次被动回血的世界时间(tick) */
	private static final Map<UUID, Long> LAST_PASSIVE_TICK = new ConcurrentHashMap<>();
	/** 受害者UUID -> { 金沙岚玩家UUID -> 凋零源记录到期世界时间(tick) } */
	private static final Map<UUID, Map<UUID, Long>> WITHER_SOURCES = new ConcurrentHashMap<>();

	private GoldenSandstormRegen() {
	}

	// ==================== 战斗状态 ====================

	/** 标记玩家进入战斗状态（仅对金沙岚生效，其他形态会被 tick 自然忽略） */
	public static void markCombat(ServerPlayerEntity player) {
		if (player == null) return;
		LAST_COMBAT_TICK.put(player.getUuid(), player.getWorld().getTime());
	}

	/** 检查玩家是否处于战斗状态 */
	public static boolean isInCombat(ServerPlayerEntity player) {
		Long last = LAST_COMBAT_TICK.get(player.getUuid());
		if (last == null) return false;
		return player.getWorld().getTime() - last <= COMBAT_DURATION;
	}

	// ==================== 凋零来源追踪 ====================

	/** 注册凋零来源（金沙岚玩家给受害者施加凋零时调用） */
	public static void registerWitherSource(LivingEntity victim, ServerPlayerEntity player, int witherDurationTicks) {
		if (victim == null || player == null) return;
		long expire = victim.getWorld().getTime() + (long) witherDurationTicks + WITHER_SOURCE_GRACE;
		WITHER_SOURCES.computeIfAbsent(victim.getUuid(), k -> new ConcurrentHashMap<>())
				.put(player.getUuid(), expire);
	}

	/** 凋零 tick 对受害者造成伤害时调用：为所有注册的金沙岚玩家回血 */
	public static void onWitherTickDamage(LivingEntity victim) {
		if (victim == null) return;
		Map<UUID, Long> sources = WITHER_SOURCES.get(victim.getUuid());
		if (sources == null || sources.isEmpty()) return;
		if (!(victim.getWorld() instanceof ServerWorld serverWorld)) return;
		long now = serverWorld.getTime();
		// 清理过期记录
		sources.entrySet().removeIf(e -> e.getValue() < now);
		if (sources.isEmpty()) {
			WITHER_SOURCES.remove(victim.getUuid());
			return;
		}
		for (UUID playerUuid : sources.keySet()) {
			PlayerEntity p = serverWorld.getPlayerByUuid(playerUuid);
			if (!(p instanceof ServerPlayerEntity sp)) continue;
			if (!FormUtils.isForm(sp, FormIdentifiers.GOLDEN_SANDSTORM_SP)) continue;
			sp.heal(WITHER_TICK_HEAL);
			markCombat(sp);
		}
	}

	// ==================== 被动回血 tick ====================

	/** 每 tick 处理金沙岚的被动回血 */
	public static void tick(ServerPlayerEntity player) {
		if (player == null) return;
		if (!FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) return;
		// 满血时仅刷新计时器，避免一进战斗就立即结算累积时间
		long now = player.getWorld().getTime();
		if (player.getHealth() >= player.getMaxHealth()) {
			LAST_PASSIVE_TICK.put(player.getUuid(), now);
			return;
		}
		Long last = LAST_PASSIVE_TICK.get(player.getUuid());
		if (last == null) {
			LAST_PASSIVE_TICK.put(player.getUuid(), now);
			return;
		}
		int interval = isInCombat(player) ? PASSIVE_INTERVAL_IC : PASSIVE_INTERVAL_OOC;
		if (now - last >= interval) {
			player.heal(PASSIVE_HEAL);
			LAST_PASSIVE_TICK.put(player.getUuid(), now);
		}
	}

	// ==================== 击杀监听 ====================

	/** 注册击杀事件监听器（在 SscAddon.onInitialize 中调用） */
	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(GoldenSandstormRegen::onEntityDeath);
	}

	private static void onEntityDeath(LivingEntity entity, DamageSource source) {
		if (entity == null || entity.getWorld().isClient()) return;
		// 1. 玩家直接击杀
		if (source.getAttacker() instanceof ServerPlayerEntity killer
				&& FormUtils.isForm(killer, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			killer.heal(KILL_HEAL);
			markCombat(killer);
		}
		// 2. 冥狼击杀
		else if (source.getAttacker() instanceof AnubisWolfMinionEntity wolf
				&& entity.getWorld() instanceof ServerWorld serverWorld) {
			UUID ownerUuid = wolf.getMinionOwnerUUID();
			if (ownerUuid != null) {
				PlayerEntity owner = serverWorld.getPlayerByUuid(ownerUuid);
				if (owner instanceof ServerPlayerEntity ownerPlayer
						&& FormUtils.isForm(ownerPlayer, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
					ownerPlayer.heal(MINION_KILL_HEAL);
					markCombat(ownerPlayer);
				}
			}
		}
		// 受害者死亡，清理对应凋零源记录
		WITHER_SOURCES.remove(entity.getUuid());
	}

	// ==================== 状态清理 ====================

	/** 清空所有状态（服务器启停 / 数据包重载） */
	public static void clearAll() {
		LAST_COMBAT_TICK.clear();
		LAST_PASSIVE_TICK.clear();
		WITHER_SOURCES.clear();
	}

	/** 清理单个玩家状态（断线 / 切换形态） */
	public static void clearPlayer(UUID playerUuid) {
		if (playerUuid == null) return;
		LAST_COMBAT_TICK.remove(playerUuid);
		LAST_PASSIVE_TICK.remove(playerUuid);
		WITHER_SOURCES.values().forEach(m -> m.remove(playerUuid));
	}
}
