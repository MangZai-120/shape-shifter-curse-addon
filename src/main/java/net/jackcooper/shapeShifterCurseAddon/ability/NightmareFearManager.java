package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔（Nightmare）主要技能「恐惧」（Fear）—— 服务端权威状态机。
 *
 * <p>按键（sp_primary）触发：对<b>所有已入梦</b>目标施加恐惧（无论距离），要求技能 CD 就绪。
 * 恐惧持续 15 秒（300t），期间：</p>
 * <ul>
 *   <li>目标视野缩至 16 格（客户端粉雾渐进淡入，仅本人可见）+ 减速 15%；</li>
 *   <li>守卫者式心跳声（服务端周期广播，本人可闻）；</li>
 *   <li>目标的入梦到期时间<b>持续锁定回 20s</b>（每 tick 刷新到 now+400）；</li>
 *   <li>受<b>任何食梦魔</b>伤害 ×2（见 {@code SscAddonLivingEntityMixin} damage 钩子）；</li>
 *   <li>梦魔进入目标 16 格视野 → 目标客户端获得 1 秒「看不见该梦魔」窗口（渲染屏蔽，仅本地）。</li>
 * </ul>
 * <p>恐惧结束：强制出梦 + 20s（400t）入梦免疫（{@code NightmareDreamManager} 的 IMMUNE 表）。</p>
 *
 * <p>客户端包 {@code PACKET_FEAR_STATE}（S2C，仅目标本人）：varint durationTicks。
 * 粉雾淡入/心跳渲染/1s 隐身判定全在客户端 {@code NightmareFearClient}。</p>
 */
public final class NightmareFearManager {

	/** 恐惧持续 tick（15 秒）。 */
	public static final int FEAR_DURATION_TICKS = 300;
	/** 梦魇戒指：恐惧持续时长增幅（+35% → 405t ≈ 20.25 秒），施加瞬间快照。 */
	public static final float FEAR_DURATION_RING_BONUS = net.jackcooper.shapeShifterCurseAddon.item.NightmareRingItem.FEAR_DURATION_BONUS;
	/** 技能 CD（tick，20 秒）。 */
	public static final int FEAR_COOLDOWN_TICKS = 400;
	/** 诅咒之月共鸣：诅咒之月当夜恐惧 CD 降为 14 秒（280t）。 */
	public static final int FEAR_COOLDOWN_TICKS_CURSED_MOON = 280;
	/** 恐惧结束后入梦免疫时长（tick，20 秒）。 */
	public static final int DREAM_IMMUNE_TICKS = 400;
	/** 心跳音效间隔（tick，1.6 秒/拍——守卫者心跳节奏）。 */
	public static final int HEARTBEAT_INTERVAL = 32;
	/** 减速 20% 的属性 modifier UUID（固定 UUID，可幂等移除）。 */
	private static final UUID FEAR_SLOW_UUID = UUID.fromString("e3a1f7c2-9b4d-4e6a-8c15-d2f3a7b9e810");
	private static final String FEAR_SLOW_NAME = "Nightmare Fear Slow";
	/** 减速幅度（用户定稿：必备减速 20%，玩家/生物一致）。 */
	public static final float FEAR_SLOW_RATIO = 0.20f;
	/** 非玩家目标的仇恨压制：被梦魔攻击后的反击窗口（tick，2 秒）。 */
	public static final int MOB_AGGRO_WINDOW_TICKS = 40;
	/** 恐惧视野半径（格）。 */
	public static final double FEAR_SIGHT_RADIUS = 16.0;
	/** 可见性脉冲：隐匿相位时长（tick，2 秒）。 */
	public static final int PULSE_HIDDEN_TICKS = 40;
	/** 可见性脉冲：现形相位时长（tick，0.6 秒）。 */
	public static final int PULSE_VISIBLE_TICKS = 12;
	/** 攻击显形时长（tick，1.5 秒）。 */
	public static final int REVEAL_ON_ATTACK_TICKS = 30;
	/** 范围外完全隐匿的包刷新间隔（tick）。 */
	private static final int OUT_OF_RANGE_HIDE_REFRESH = 40;
	/** 心跳基础音量（范围外/无梦魔时的极小固定音量）。 */
	private static final float HEARTBEAT_BASE_VOL = 0.15f;
	/** 心跳最大音量（梦魔贴脸时）。 */
	private static final float HEARTBEAT_MAX_VOL = 1.2f;

	/** 可见性状态机：目标UUID|梦魔UUID -> 脉冲相位状态。 */
	private static final Map<String, PairState> PAIRS = new ConcurrentHashMap<>();

	/** 单对（目标×梦魔）的可见性脉冲状态。 */
	private static final class PairState {
		/** 当前是否隐匿相位。 */
		boolean hidden;
		/** 当前相位切换时刻（0 = 尚未初始化/刚从范围外回来）。 */
		long phaseEnd;
		/** 上次发隐匿包的时刻（范围外周期刷新用）。 */
		long lastHideSent;
	}

	/** 梦魔攻击恐惧目标时调用（damage mixin）：显形 1.5s 并重置该对的脉冲相位。 */
	public static void onNightmareAttackFeared(ServerPlayerEntity target, ServerPlayerEntity attacker) {
		long now = target.getWorld().getTime();
		PairState ps = PAIRS.get(target.getUuid() + "|" + attacker.getUuid());
		if (ps != null) {
			ps.hidden = false;
			ps.phaseEnd = now + REVEAL_ON_ATTACK_TICKS;
		}
		SscAddonNetworking.sendFearReveal(target, attacker.getUuid(), REVEAL_ON_ATTACK_TICKS);
	}

	/** 清理某目标的全部可见性状态（恐惧结束/断线）。 */
	private static void clearPairs(UUID tid) {
		PAIRS.keySet().removeIf(k -> k.startsWith(tid + "|"));
	}

	/** 恐惧中目标：目标 UUID -> FearState{到期时间, 施恐惧的梦魔 UUID}。 */
	private static final Map<UUID, FearState> FEARING = new ConcurrentHashMap<>();
	/** 入梦免疫表：目标 UUID -> 免疫到期世界时间（恐惧结束惩罚窗口）。 */
	private static final Map<UUID, Long> DREAM_IMMUNE = new ConcurrentHashMap<>();

	private NightmareFearManager() {
	}

	/** 目标当前是否处于恐惧中（服务端判定）。 */
	public static boolean isFeared(UUID targetUuid, long now) {
		FearState v = FEARING.get(targetUuid);
		return v != null && v.endTick > now;
	}

	/**
	 * 一次性消耗「双倍伤害」机会（用户规格：整个恐惧期间只触发一次）。
	 * 处于恐惧中且本次恐惧尚未用过 → 标记已用并返回 true（该次伤害 ×2）；否则返回 false。
	 * 恐惧重新施加时 FearState 重建，新一轮恐惧重新可触发一次。
	 */
	public static boolean tryConsumeDoubleDamage(UUID targetUuid, long now) {
		FearState v = FEARING.get(targetUuid);
		if (v == null || v.endTick <= now || v.doubleDamageUsed) return false;
		// 梦魇戒指（施加时快照）：本轮恐惧不提供首次伤害翻倍
		if (!v.doubleDamageAllowed) return false;
		v.doubleDamageUsed = true;
		return true;
	}

	/** 当前生效的恐惧 CD：诅咒之月当夜 280t，否则 400t。仅服务端调用。 */
	public static int currentFearCooldown(ServerPlayerEntity player) {
		if (net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon.isInCursedMoon(player.getWorld())) {
			return FEAR_COOLDOWN_TICKS_CURSED_MOON;
		}
		return FEAR_COOLDOWN_TICKS;
	}

	/** 目标当前是否入梦免疫（恐惧结束后的 20s 惩罚窗口）。 */
	public static boolean isDreamImmune(UUID targetUuid, long now) {
		Long until = DREAM_IMMUNE.get(targetUuid);
		return until != null && until > now;
	}

	/**
	 * 主要技能「恐惧」入口（power action 调用，仅服务端）。
	 * 对所有已入梦目标施加恐惧；无入梦目标时返回 false（不进 CD，白提示）。
	 */
	public static boolean execute(ServerPlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) return false;
		// CD 检查（Apoli power 自身 cooldown=0，用 CD 资源统一管理）
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD);
		if (cd > 0) return false;

		long now = world.getTime();
		List<LivingEntity> targets = NightmareDreamManager.collectDreamTargets(player, now);
		if (targets.isEmpty()) return false; // 没有入梦目标：技能落空（不消耗 CD）

		for (LivingEntity target : targets) {
			startFear(world, player, target, now);
		}
		// 进入 CD（诅咒之月共鸣：当夜 CD 缩短）
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, currentFearCooldown(player));
		return true;
	}

	/** 对单目标施加恐惧：状态入表 + 减速 + 包 + 音效（过渡：先低沉震颤音预告，再起心跳）。
	 * 梦魇戒指（施加瞬间快照）：持续时长 +35%；双倍伤害整轮禁用。进行中的恐惧不受中途戴/摘影响。 */
	private static void startFear(ServerWorld world, ServerPlayerEntity caster, LivingEntity target, long now) {
		UUID tid = target.getUuid();
		// 戒指快照：施加瞬间判定佩戴状态，写入本轮 FearState（时长增幅 + 双倍伤害禁用）
		boolean ringWorn = net.jackcooper.shapeShifterCurseAddon.item.NightmareRingItem.isWearingBy(caster);
		int duration = ringWorn ? Math.round(FEAR_DURATION_TICKS * (1.0f + FEAR_DURATION_RING_BONUS)) : FEAR_DURATION_TICKS;
		// [DEBUG] 戒指检测链排查日志（确认佩戴检测是否真正生效）
		org.slf4j.LoggerFactory.getLogger("NightmareDebug").info(
				"[恐惧] {} 对 {} 施放恐惧：戒指检测={}，时长={}t",
				caster.getName().getString(), target.getName().getString(), ringWorn, duration);
		FEARING.put(tid, new FearState(now + duration, caster.getUuid(), !ringWorn));
		// 入梦时间重置回 20s（规格②：获得恐惧即重置）
		NightmareDreamManager.resetDream(caster.getUuid(), tid, now);
		// 减速 20%（幂等：先移除再加；用户定稿：必备减速，玩家/生物一致）
		EntityAttributeInstance attr = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr != null) {
			attr.removeModifier(FEAR_SLOW_UUID);
			attr.addPersistentModifier(new EntityAttributeModifier(
					FEAR_SLOW_UUID, FEAR_SLOW_NAME, -FEAR_SLOW_RATIO, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		// 客户端包（粉雾淡入 + 心跳启动 + 本地失明驱动），仅目标本人（时长随戒指快照变化）
		if (target instanceof ServerPlayerEntity sp) {
			SscAddonNetworking.sendFearState(sp, duration);
		}
		// 施加过渡音效：低沉梦境震颤（目标位置，全员可闻但音量克制）+ 目标本人听到尖啸耳鸣
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
				net.minecraft.sound.SoundCategory.PLAYERS, 1.4f, 0.45f);
		if (target instanceof ServerPlayerEntity sp) {
			world.playSound(sp, target.getX(), target.getY(), target.getZ(),
					net.minecraft.sound.SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
					net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.6f);
		}
		// 爆发粒子：粉紫梦境尘环绕目标一圈（他人可见的视觉信号）
		world.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
				target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
				24, 0.5, 0.6, 0.5, 0.03);
		world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
				target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
				6, 0.3, 0.5, 0.3, 0.02);
	}

	/** 梦魔玩家每 tick 推进（由 SscAddonServerEvents 调用）。
	 * 修复：只推进 <b>本梦魔自己施加</b> 的恐惧条目（此前任意在线玩家 tick 都遍历全表，
	 * 多人在线时状态机重复执行导致隐匿/现形包互相打架 → 16 格内偶发突然现形）。 */
	public static void tick(ServerPlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) return;
		long now = world.getTime();
		if (FEARING.isEmpty() && DREAM_IMMUNE.isEmpty()) return;
		boolean isCasterTick = NightmareDreamManager.isNightmare(player);

		Iterator<Map.Entry<UUID, FearState>> it = FEARING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, FearState> e = it.next();
			UUID tid = e.getKey();
			FearState v = e.getValue();
			// 仅施恐惧的梦魔本人的 tick 推进该条目（去重）
			if (isCasterTick && !v.casterUuid.equals(player.getUuid())) continue;
			if (!isCasterTick && !tid.equals(player.getUuid())) continue;
			if (v.endTick <= now) {
				it.remove();
				endFear(world, tid, v.casterUuid, now, true);
				continue;
			}
			if (!(world.getEntity(tid) instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				endFear(world, tid, v.casterUuid, now, false);
				continue;
			}
			// 规格②：恐惧期间入梦时间持续锁定回 20s
			NightmareDreamManager.lockDream(v.casterUuid, tid, now);
			// 心跳声（规格⑤）：按最近的食梦魔距离动态音量——范围外极小固定音量，
			// 16 格内越近越响（音量 = 基础 + (1-距离/16)×(最大-基础)）
			if (now % HEARTBEAT_INTERVAL == 0 && target instanceof ServerPlayerEntity sp) {
				double nearest = Double.MAX_VALUE;
				for (ServerPlayerEntity p : world.getPlayers()) {
					if (p.getUuid().equals(tid) || !NightmareDreamManager.isNightmare(p)) continue;
					nearest = Math.min(nearest, p.squaredDistanceTo(sp));
				}
				float vol = HEARTBEAT_BASE_VOL;
				if (nearest != Double.MAX_VALUE && nearest <= FEAR_SIGHT_RADIUS * FEAR_SIGHT_RADIUS) {
					double dist = Math.sqrt(nearest);
					vol = HEARTBEAT_BASE_VOL + (float) ((1.0 - dist / FEAR_SIGHT_RADIUS) * (HEARTBEAT_MAX_VOL - HEARTBEAT_BASE_VOL));
				}
				world.playSound(sp, sp.getX(), sp.getY(), sp.getZ(),
						net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT,
						net.minecraft.sound.SoundCategory.PLAYERS, vol, 0.9f);
			}
			// 可见性状态机（目标本人的 tick 推进，另一重去重；多人多梦魔各自独立）
			if (tid.equals(player.getUuid()) && target instanceof ServerPlayerEntity sp) {
				tickVisibility(world, sp, now);
			}
		}
		// 免疫表清理
		DREAM_IMMUNE.values().removeIf(until -> until <= now);
	}

	/** 可见性状态机（规格①②③）：
	 * <ul>
	 *   <li>16 格范围外 → 完全隐身（周期性重发隐匿包保活）；</li>
	 *   <li>16 格内 → 脉冲：隐 2s → 现 0.6s 循环（进入范围时从隐匿相位开始）；</li>
	 *   <li>攻击 → 现 1.5s 并重置相位（onNightmareAttackFeared）。</li>
	 * </ul> */
	private static void tickVisibility(ServerWorld world, ServerPlayerEntity target, long now) {
		for (ServerPlayerEntity nightmare : world.getPlayers()) {
			if (nightmare.getUuid().equals(target.getUuid())) continue;
			if (!NightmareDreamManager.isNightmare(nightmare)) continue;
			String key = target.getUuid() + "|" + nightmare.getUuid();
			boolean inRange = nightmare.squaredDistanceTo(target) <= FEAR_SIGHT_RADIUS * FEAR_SIGHT_RADIUS;
			PairState ps = PAIRS.computeIfAbsent(key, k -> new PairState());
			if (!inRange) {
				// 规格①：范围外完全隐身（每 2s 重发一次隐匿包保活，防包丢失导致现形）
				if (now - ps.lastHideSent >= OUT_OF_RANGE_HIDE_REFRESH) {
					ps.lastHideSent = now;
					ps.hidden = true;
					ps.phaseEnd = now + OUT_OF_RANGE_HIDE_REFRESH + 20;
					SscAddonNetworking.sendFearHide(target, nightmare.getUuid(), OUT_OF_RANGE_HIDE_REFRESH + 20);
				}
				continue;
			}
			// 范围内：脉冲相位推进（phaseEnd 到期切换）
			if (now >= ps.phaseEnd) {
				ps.hidden = !ps.hidden;
				ps.phaseEnd = now + (ps.hidden ? PULSE_HIDDEN_TICKS : PULSE_VISIBLE_TICKS);
				if (ps.hidden) {
					SscAddonNetworking.sendFearHide(target, nightmare.getUuid(), PULSE_HIDDEN_TICKS);
				} else {
					SscAddonNetworking.sendFearReveal(target, nightmare.getUuid(), PULSE_VISIBLE_TICKS);
				}
			}
		}
	}

	/** 恐惧结束：移除减速、发结束包、强制出梦 + 免疫。applyImmune=false 用于目标死亡场景。 */
	private static void endFear(ServerWorld world, UUID tid, UUID casterUuid, long now, boolean applyImmune) {
		if (world.getEntity(tid) instanceof LivingEntity target) {
			EntityAttributeInstance attr = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (attr != null) attr.removeModifier(FEAR_SLOW_UUID);
			if (target instanceof ServerPlayerEntity sp) {
				SscAddonNetworking.sendFearState(sp, 0);
			}
		}
		clearPairs(tid);
		// 强制出梦（由施恐惧的梦魔的入梦表移除该目标；其它梦魔若也入梦了它则一并清——恐惧结束强制全出）
		NightmareDreamManager.forceWakeAll(tid, now, world);
		if (applyImmune) {
			DREAM_IMMUNE.put(tid, now + DREAM_IMMUNE_TICKS);
		}
		// [DEBUG] 恐惧结束链路排查日志（确认强制出梦+免疫真空期是否执行）
		org.slf4j.LoggerFactory.getLogger("NightmareDebug").info(
				"[恐惧] 恐惧结束：目标 {} 强制出梦，免疫真空期={}t（applyImmune={}）",
				tid, applyImmune ? DREAM_IMMUNE_TICKS : 0, applyImmune);
	}

	/** 梦魔断线/死亡/失形清理：其施加的恐惧一并结束（目标恢复 + 免疫照常）。 */
	public static void onCasterRemoved(ServerPlayerEntity caster) {
		if (!(caster.getWorld() instanceof ServerWorld world)) return;
		long now = world.getTime();
		Iterator<Map.Entry<UUID, FearState>> it = FEARING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, FearState> e = it.next();
			if (e.getValue().casterUuid.equals(caster.getUuid())) {
				it.remove();
				endFear(world, e.getKey(), caster.getUuid(), now, true);
			}
		}
	}

	/** 恐惧状态（到期 + 施恐惧的梦魔 + 双倍伤害开关/已用标记）。 */
	private static final class FearState {
		final long endTick;
		final UUID casterUuid;
		/** 梦魇戒指快照：本轮恐惧是否保留双倍伤害（戴戒指 → false）。 */
		final boolean doubleDamageAllowed;
		/** 本轮恐惧的双倍伤害是否已消耗（整轮只触发一次）。 */
		boolean doubleDamageUsed;
		FearState(long endTick, UUID casterUuid, boolean doubleDamageAllowed) {
			this.endTick = endTick;
			this.casterUuid = casterUuid;
			this.doubleDamageAllowed = doubleDamageAllowed;
		}
	}

	// ===== 非玩家目标：仇恨压制（用户定稿） =====

	/**
	 * 恐惧中的非玩家生物是否允许将 target 设为攻击目标（用户定稿：只对释放玩家不可视）。
	 * <p>机制：恐惧期间 mob <b>仅对释放该恐惧的梦魇本人</b>失去仇恨（拦 setTarget）；
	 * 被梦魔攻击后仅有 {@link #MOB_AGGRO_WINDOW_TICKS}（2 秒）反击窗口，
	 * 窗口过后（lastAttackedTime 超时）仇恨消失（mobTick 清目标）。
	 * 对其它实体（其它玩家、其它梦魇、生物）一切正常，不受恐惧影响。</p>
	 */
	public static boolean isMobAggroAllowed(MobEntity mob, LivingEntity target) {
		long now = mob.getWorld().getTime();
		FearState v = FEARING.get(mob.getUuid());
		if (v == null || v.endTick <= now) return true; // 非恐惧 mob 不受控
		// 仅当目标正是释放该恐惧的梦魇本人时才压制仇恨
		if (!(target instanceof ServerPlayerEntity tp) || !tp.getUuid().equals(v.casterUuid)) return true;
		long lastHit = mob.getLastAttackedTime(); // vanilla：受击成功时更新
		return now - lastHit <= MOB_AGGRO_WINDOW_TICKS; // 仅被击 2 秒内可反击
	}

	/** 恐惧中的 mob 每刻清除对「施法梦魇」的过期仇恨（由 MobEntityMixin.mobTick 调用，仅服务端）。 */
	public static void tickFearedMobAggro(MobEntity mob) {
		long now = mob.getWorld().getTime();
		FearState v = FEARING.get(mob.getUuid());
		if (v == null || v.endTick <= now) return;
		LivingEntity cur = mob.getTarget();
		if (cur == null) return;
		if (!isMobAggroAllowed(mob, cur)) {
			mob.setTarget(null); // 仇恨消失（放行：setTarget(null) 不受拦截）
		}
	}

	/**
	 * 双倍伤害受益者扩展（用户定稿）：恐惧目标受「任何食梦魔<b>及其白名单成员</b>」的首次伤害 ×2。
	 * 白名单成员 = 在线恐惧梦魔的白名单友军（WhitelistUtils.isBuffTarget 强化类目标判定）。
	 */
	public static boolean isDoubleDamageBeneficiary(LivingEntity attacker, ServerWorld world) {
		if (!(attacker instanceof ServerPlayerEntity sp)) return false;
		if (NightmareDreamManager.isNightmare(sp)) return true;
		// 非梦魔玩家：是任一在线食梦魔的白名单友军 → 受益
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (!NightmareDreamManager.isNightmare(p)) continue;
			if (net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isBuffTarget(p, sp)) {
				return true;
			}
		}
		return false;
	}

	/** 目标断线清理。 */
	public static void onTargetDisconnect(UUID targetUuid) {
		FEARING.remove(targetUuid);
		DREAM_IMMUNE.remove(targetUuid);
		clearPairs(targetUuid);
	}

	/** 目标重生/换维度兜底清理（保留：跨维度后 world.getTime 基准变化）。 */
	public static void clearForTarget(UUID targetUuid) {
		FEARING.remove(targetUuid);
	}

	/** 当前恐惧目标数（调试用）。 */
	public static int size() {
		return FEARING.size();
	}
}
