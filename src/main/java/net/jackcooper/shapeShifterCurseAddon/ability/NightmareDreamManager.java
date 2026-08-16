package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔（Nightmare）「入梦」核心状态机 —— 服务端权威。
 *
 * <p>机制：食梦魔玩家对目标造成<b>累计 10 点</b>伤害后（诅咒之月共鸣：当夜降为 6 点），目标进入「入梦」状态。
 * 入梦期间再次被该食梦魔攻击 → 计数清零重算 + 噬梦吸血（15% 转化回血；目标死亡额外回 4 点）。
 * 入梦中的敌方对食梦魔本人施加的一切 debuff（负面状态效果）全部无效——含定身（ssc_addon:stun）、
 * 主动/被动高光描边（GlowMarker / 蛛网高亮 / 侵蚀烙印 / 契灵标记等带源施加点都会被
 * {@code NightmareDreamGuard.isBlocked} 拦截）。
 * 食梦魔本人看入梦目标有<b>粉红色描边</b>（复用 WebHighlight 客户端描边通道，仅本人可见）。</p>
 *
 * <p>数据结构：外层 key = 食梦魔玩家 UUID；内层 key = 入梦目标 UUID。
 * 服务端每 tick（{@link #tick}）推进到期清理并广播；客户端只收同步包维护描边。
 * 死亡 / 形态丢失 / 断线 → 清理该玩家的全部入梦状态。</p>
 */
public final class NightmareDreamManager {

	/** 触发入梦所需累计伤害（每次入梦后清零重算）。 */
	public static final float DREAM_THRESHOLD = 10.0f;
	/** 诅咒之月共鸣：诅咒之月当夜入梦阈值降为 6 点（梦魇之力高涨）。 */
	public static final float DREAM_THRESHOLD_CURSED_MOON = 6.0f;
	/** 噬梦被动：对已入梦目标造成伤害的吸血比例（按面板伤害计）。 */
	public static final float DREAM_LIFESTEAL_RATIO = 0.15f;
	/** 噬梦被动：入梦目标死亡时额外回复的生命值（2 颗心）。 */
	public static final float DREAM_KILL_HEAL = 4.0f;

	/** 当前生效的入梦阈值：诅咒之月当夜（天黑 + 诅咒之月日）降为 6，否则 10。仅服务端调用。 */
	public static float currentDreamThreshold(ServerPlayerEntity player) {
		if (net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon.isInCursedMoon(player.getWorld())) {
			return DREAM_THRESHOLD_CURSED_MOON;
		}
		return DREAM_THRESHOLD;
	}
	/** 入梦持续 tick（20 秒）。 */
	public static final int DREAM_DURATION_TICKS = 400;
	/** 受击触发入梦的固定时长（tick，10 秒，不随受击刷新；恐惧重置时回到 20 秒锁定）。 */
	public static final int DREAM_FIXED_TICKS = 200;

	/** 梦境粒子发射间隔（tick，0.75 秒一次，"偶尔冒"的节奏）。 */
	public static final int DREAM_PARTICLE_INTERVAL = 15;

	/** 粉红描边颜色（RGB，仅食梦魔本人可见）。 */
	public static final int DREAM_OUTLINE_COLOR = 0xFF6EC7;

	/** 玩家 UUID -> (目标 UUID -> 入梦到期世界时间)。 */
	private static final Map<UUID, Map<UUID, Long>> DREAMING = new ConcurrentHashMap<>();
	/** 玩家 UUID -> (目标 UUID -> 累计伤害)。 */
	private static final Map<UUID, Map<UUID, Float>> ACCUM = new ConcurrentHashMap<>();

	private NightmareDreamManager() {
	}

	/** 该玩家是否为食梦魔形态（服务端）。 */
	public static boolean isNightmare(ServerPlayerEntity player) {
		return FormUtils.isForm(player, net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.WILD_CAT_NIGHTMARE);
	}

	/**
	 * 食梦魔对目标造成伤害后的累计入口（由 LivingEntityMixin damage RETURN 调用，仅服务端）。
	 * 累计达阈值 → 目标入梦（计时 20s）；已入梦再被打 → 刷新时长 + 计数清零重算。
	 */
	public static void onNightmareDealtDamage(ServerPlayerEntity player, LivingEntity target, float amount) {
		if (amount <= 0) return;
		UUID pid = player.getUuid();
		UUID tid = target.getUuid();

		Map<UUID, Long> dreams = DREAMING.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
		Map<UUID, Float> acc0 = ACCUM.computeIfAbsent(pid, k -> new ConcurrentHashMap<>());
		long now = player.getWorld().getTime();

		// 已入梦：噬梦吸血（15% 转化回血，按面板伤害计）+ 目标死亡额外回 2 心；不再刷新时长
		// （用户定稿：受击入梦固定 10 秒到期；恐惧仍可重置并锁定 20 秒）
		Long until = dreams.get(tid);
		if (until != null && until > now) {
			acc0.remove(tid); // 计数清零重算
			player.heal(amount * DREAM_LIFESTEAL_RATIO); // 噬梦：汲取梦境之力回血
			if (!target.isAlive()) {
				player.heal(DREAM_KILL_HEAL); // 梦尽人亡：额外吞噬残梦
			}
			SscAddonNetworking.sendWebHighlight(player, target.getId(),
					(int) Math.max(20, until - now), DREAM_OUTLINE_COLOR); // 描边剩余时长同步（不延长）
			return;
		}

		// 未入梦：累计伤害，达阈值触发入梦（恐惧结束后的 20s 免疫窗口内不再触发；
		// 诅咒之月共鸣：当夜阈值降为 6 点）
		Map<UUID, Float> acc = acc0;
		float total = acc.merge(tid, amount, Float::sum);
		if (total >= currentDreamThreshold(player)
				&& !NightmareFearManager.isDreamImmune(tid, now)) {
			acc.remove(tid);
			// 受击触发入梦：固定 10 秒（用户定稿；恐惧重置时才回到 20 秒锁定）
			dreams.put(tid, now + DREAM_FIXED_TICKS);
			SscAddonNetworking.sendWebHighlight(player, target.getId(), DREAM_FIXED_TICKS, DREAM_OUTLINE_COLOR);
			sendVeilToTarget(player, target, DREAM_FIXED_TICKS);
			// 入梦音效：低沉梦境钟声（全员可闻 null）+ 梦魇低语
			net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getWorld();
			sw.playSound(null, target.getX(), target.getY(), target.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, net.minecraft.sound.SoundCategory.PLAYERS, 1.2f, 0.6f);
			sw.playSound(null, target.getX(), target.getY(), target.getZ(),
					net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.6f);
			// 粒子：紫色梦境迷雾环绕目标
			sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
					target.getX(), target.getY() + target.getHeight() * 0.75, target.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		}
	}

	/**
	 * 拦截判定（仅服务端）：source 实体（debuff 施加者）是否处于「被 target 玩家入梦」状态。
	 * 即：施加者已入梦 且 受体是把它打入梦的食梦魔本人 → 该 debuff 无效。
	 */
	public static boolean isBlocked(LivingEntity source, LivingEntity target) {
		if (!(target instanceof ServerPlayerEntity player)) return false;
		Map<UUID, Long> dreams = DREAMING.get(player.getUuid());
		if (dreams == null) return false;
		Long until = dreams.get(source.getUuid());
		return until != null && until > player.getWorld().getTime();
	}

	/** 食梦魔玩家每 tick 推进（由 SscAddonServerEvents 调用）：到期出梦清理 + 梦境粒子。 */
	public static void tick(ServerPlayerEntity player) {
		Map<UUID, Long> dreams = DREAMING.get(player.getUuid());
		if (dreams == null || dreams.isEmpty()) return;
		if (!isNightmare(player)) {
			// 形态丢失：清全部
			clearAllFor(player);
			return;
		}
		net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getWorld();
		long now = sw.getTime();
		dreams.values().removeIf(until -> until <= now);
		// 梦境粒子：入梦目标身上偶尔冒粉色尘粒（与描边同色 0xFF6EC7）；目标本人不收包 → 第一人称不可见
		if (now % DREAM_PARTICLE_INTERVAL == 0) {
			for (Map.Entry<UUID, Long> e : dreams.entrySet()) {
				if (e.getValue() <= now) continue;
				if (!(sw.getEntity(e.getKey()) instanceof LivingEntity target) || !target.isAlive()) continue;
				spawnDreamParticles(sw, target);
			}
		}
	}

	/** 入梦目标周围的粉色梦境粒子：逐观察者发包（排除目标本人 + 32 格距离剔除）。 */
	private static void spawnDreamParticles(net.minecraft.server.world.ServerWorld world, LivingEntity target) {
		net.minecraft.util.math.Vec3d pos = target.getPos();
		// 粉 0xFF6EC7 → (1.0, 0.4314, 0.7804)；偶发 1~2 粒、缓慢上飘
		net.minecraft.network.packet.s2c.play.ParticleS2CPacket pkt = new net.minecraft.network.packet.s2c.play.ParticleS2CPacket(
				new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1.0f, 0.4314f, 0.7804f), 1.0f),
				false,
				pos.x, pos.y + target.getHeight() * 0.6, pos.z,
				0.35f, 0.4f, 0.35f, 0.01f, 1);
		UUID targetUuid = target.getUuid();
		for (net.minecraft.server.network.ServerPlayerEntity viewer : world.getPlayers()) {
			if (viewer.getUuid().equals(targetUuid)) continue; // 目标本人不可见（第一人称不冒粒子挡视线）
			if (viewer.squaredDistanceTo(target) > 32 * 32) continue;
			viewer.networkHandler.sendPacket(pkt);
		}
	}

	/** 断线 / 死亡 / 形态丢失：清理该玩家全部入梦状态（不动其他食梦魔的状态）。 */
	public static void clearAllFor(ServerPlayerEntity player) {
		Map<UUID, Long> dreams = DREAMING.remove(player.getUuid());
		ACCUM.remove(player.getUuid());
		// 提前出梦：向所有仍在入梦的目标发关系结束包（粉色晕影消失 + 客户端移除入梦关系）；自然到期由客户端自行淡出
		if (dreams != null && player.getServer() != null) {
			for (UUID tid : dreams.keySet()) {
				if (player.getUuid().equals(tid)) continue;
				net.minecraft.server.network.ServerPlayerEntity target =
						player.getServer().getPlayerManager().getPlayer(tid);
				if (target != null) {
					SscAddonNetworking.sendDreamVeil(target, 0, player.getUuid());
				}
			}
		}
	}

	// ===== 客户端镜像（仅客户端使用；服务端永远为空） =====
	/** 客户端：「把我（本地玩家）入梦的食梦魔 UUID -> 到期客户端时间」。供透视拦截查询。 */
	private static final Map<UUID, Long> CLIENT_DREAMED_BY = new ConcurrentHashMap<>();

	/** 客户端收到晕影包时维护镜像（dreamVeilRenderer 调用）。 */
	public static void clientUpdateDreamedBy(UUID nightmareUuid, long endClientTime) {
		if (nightmareUuid == null) return;
		if (endClientTime <= 0) {
			CLIENT_DREAMED_BY.remove(nightmareUuid);
		} else {
			CLIENT_DREAMED_BY.put(nightmareUuid, endClientTime);
		}
	}

	/** 客户端查询：该实体（透视 power 持有者）是否正处于「把我入梦」状态（供 EntityGlowPower 拦截）。
	 *  无世界时间上下文时退化为存在性判断。 */
	public static boolean clientIsDreamingMe(UUID holderUuid, long nowClientTime) {
		if (holderUuid == null) return false;
		Long end = CLIENT_DREAMED_BY.get(holderUuid);
		if (end == null) return false;
		return nowClientTime <= 0 || end > nowClientTime;
	}

	/** 客户端查询：本地玩家当前是否被任一食梦魔入梦（nowClientTime<=0 时不校验到期）。 */
	public static boolean clientHasAnyDream(long nowClientTime) {
		if (CLIENT_DREAMED_BY.isEmpty()) return false;
		if (nowClientTime <= 0) return true;
		return CLIENT_DREAMED_BY.values().stream().anyMatch(end -> end > nowClientTime);
	}

	/** 客户端断线/换服清理镜像。 */
	public static void clientClear() {
		CLIENT_DREAMED_BY.clear();
	}

	/** 向入梦目标本人发粉色晕影包（仅玩家目标有屏幕；生物目标跳过）。包内携带食梦魔 UUID，
	 * 供客户端维护「被谁入梦」镜像 → 拦截入梦者自己的 entity_glow 透视对食梦魔的描边。 */
	private static void sendVeilToTarget(ServerPlayerEntity nightmare, LivingEntity target, int durationTicks) {
		if (target instanceof ServerPlayerEntity targetPlayer) {
			SscAddonNetworking.sendDreamVeil(targetPlayer, durationTicks, nightmare.getUuid());
		}
	}

	/** 断线钩子。 */
	public static void onPlayerDisconnect(ServerPlayerEntity player) {
		clearAllFor(player);
	}

	/** 目标死亡 / 消失时移除其入梦项（由外部按需调用）。 */
	public static void removeTarget(UUID playerId, UUID targetId) {
		Map<UUID, Long> dreams = DREAMING.get(playerId);
		if (dreams != null) dreams.remove(targetId);
		Map<UUID, Float> acc = ACCUM.get(playerId);
		if (acc != null) acc.remove(targetId);
	}

	// ===== 「恐惧」技能支撑（由 NightmareFearManager 调用，仅服务端） =====

	/** 收集某食梦魔当前已入梦的全部存活目标实体（跨实体查找，找不到的跳过）。 */
	public static List<LivingEntity> collectDreamTargets(ServerPlayerEntity nightmare, long now) {
		List<LivingEntity> out = new ArrayList<>();
		Map<UUID, Long> dreams = DREAMING.get(nightmare.getUuid());
		if (dreams == null) return out;
		for (Map.Entry<UUID, Long> e : dreams.entrySet()) {
			if (e.getValue() <= now) continue;
			if (nightmare.getServer() == null) continue;
			net.minecraft.entity.Entity ent = null;
			for (net.minecraft.server.world.ServerWorld w : nightmare.getServer().getWorlds()) {
				net.minecraft.entity.Entity found = w.getEntity(e.getKey());
				if (found != null) { ent = found; break; }
			}
			if (ent instanceof LivingEntity le && le.isAlive()) out.add(le);
		}
		return out;
	}

	/** 把某目标在该食梦魔名下的入梦到期时间重置为 now+400（20s）。 */
	public static void resetDream(UUID nightmareUuid, UUID targetUuid, long now) {
		Map<UUID, Long> dreams = DREAMING.get(nightmareUuid);
		if (dreams != null) dreams.put(targetUuid, now + DREAM_DURATION_TICKS);
	}

	/** 恐惧期间持续锁定：把该目标的入梦到期时间钉回 now+400（每 tick 调用）。 */
	public static void lockDream(UUID nightmareUuid, UUID targetUuid, long now) {
		resetDream(nightmareUuid, targetUuid, now);
	}

	/** 强制唤醒目标：从<b>所有</b>食梦魔的入梦表中移除该目标，并熄灭其客户端晕影/入梦镜像（恐惧结束用）。 */
	public static void forceWakeAll(UUID targetUuid, long now, net.minecraft.server.world.ServerWorld contextWorld) {
		List<UUID> wokeBy = new ArrayList<>();
		for (Map.Entry<UUID, Map<UUID, Long>> e : DREAMING.entrySet()) {
			if (e.getValue().remove(targetUuid) != null) wokeBy.add(e.getKey());
		}
		ACCUM.values().forEach(acc -> acc.remove(targetUuid));
		// 向目标本人逐梦魔发关系结束包（客户端镜像逐条移除、晕影无关系时熄灭）
		if (!wokeBy.isEmpty() && contextWorld != null
				&& contextWorld.getEntity(targetUuid) instanceof ServerPlayerEntity targetPlayer) {
			for (UUID nightmareUuid : wokeBy) {
				SscAddonNetworking.sendDreamVeil(targetPlayer, 0, nightmareUuid);
			}
		}
	}
}
