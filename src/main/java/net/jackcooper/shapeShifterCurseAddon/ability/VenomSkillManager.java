package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 跳蛛次要技能「毒液」（sp_secondary）—— 服务端状态机。
 *
 * <p><b>基础形态</b>（按下次键）：对前方 2×2×2 格区域内全部生物造成 4 点魔法伤害 +
 * 中毒 I 15 秒。</p>
 *
 * <p><b>丝线强化形态</b>（主技能跳杀后有安全丝连着自己时按次键）：额外获得 6 格前冲——
 * 沿准星方向冲刺，途中撞到敌方生物造成额外 2 点魔法伤害并停下；冲刺结束后以自身为圆心
 * 3 格半径 AOE：6 点魔法伤害 + 中毒 II 15 秒。</p>
 *
 * <p>CD 走 SP_SECONDARY_CD。白名单：默认白名单（护玩家 + 宠物/召唤物）。全判定服务端。</p>
 */
public final class VenomSkillManager {

	private static final float BASE_DAMAGE = 4.0f;        // 基础：前方区域 4 魔法
	private static final int BASE_POISON_DURATION = 300; // 中毒 I 15 秒
	private static final double AREA_SIZE = 2.0;         // 前方 2×2×2 格
	private static final double DASH_DISTANCE = 6.0;     // 丝线强化：冲刺 6 格
	private static final double DASH_SPEED = 1.2;        // 冲刺速度
	private static final float DASH_HIT_DAMAGE = 2.0f;   // 冲刺碰撞 2 魔法
	private static final float BURST_DAMAGE = 6.0f;      // 冲刺后 AOE 6 魔法
	private static final double BURST_RADIUS = 3.0;      // AOE 半径 3 格
	private static final int BURST_POISON_DURATION = 300;// AOE 中毒 II 15 秒
	private static final int CD_TICKS = 200;             // 10 秒
	private static final int DASH_TIMEOUT = 20;          // 冲刺超时 1 秒（6 格 / 1.2 每t ≈ 5t，余量充足）

	private static final Map<UUID, DashState> DASHING = new ConcurrentHashMap<>();

	private static final class DashState {
		double traveled = 0.0;
		int ticks = 0;
		Vec3d dir;
		boolean hitDone;
	}

	private VenomSkillManager() {}

	private static boolean isSalticidae(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE);
	}

	/** 次键按下：基础毒液区域 / 丝线强化冲刺。 */
	public static void onPress(ServerPlayerEntity player) {
		if (!isSalticidae(player)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return; // CD 中
		if (DASHING.containsKey(player.getUuid())) return; // 冲刺中不可重入
		if (!(player.getWorld() instanceof ServerWorld sw)) return;

		// 丝线强化判定：安全丝锚点存在且在有效窗口内 = 「有丝线连着自己」
		boolean silkActive = JumpKillManager.hasActiveSilk(player);

		if (silkActive) {
			startDash(player, sw);
		} else {
			venomArea(player, sw);
		}
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, CD_TICKS);
	}

	/** 基础形态：前方 2×2×2 区域毒液——4 魔法 + 中毒 I 15s（毒液腺体：等级+1 / 时长×70%）。 */
	private static void venomArea(ServerPlayerEntity player, ServerWorld sw) {
		Vec3d look = player.getRotationVector().normalize();
		Vec3d center = player.getEyePos().add(look.multiply(AREA_SIZE * 0.75)); // 区域中心在身前
		Box box = new Box(center.add(-AREA_SIZE / 2, -AREA_SIZE / 2, -AREA_SIZE / 2),
				center.add(AREA_SIZE / 2, AREA_SIZE / 2, AREA_SIZE / 2));
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator()
						&& !WhitelistUtils.isProtected(player, e));
		boolean gland = net.jackcooper.shapeShifterCurseAddon.item.VenomGlandItem.isWearingBy(player);
		int amp = gland ? 1 : 0;
		int dur = gland ? Math.round(BASE_POISON_DURATION * net.jackcooper.shapeShifterCurseAddon.item.VenomGlandItem.DURATION_SCALE) : BASE_POISON_DURATION;
		for (LivingEntity t : targets) {
			t.damage(t.getDamageSources().indirectMagic(player, player), BASE_DAMAGE);
			t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, dur, amp, false, true, true), player);
		}
		// 反馈：毒液喷溅粒子（区域中心）+ 喷吐音效
		sw.spawnParticles(ParticleTypes.WITCH, center.x, center.y, center.z,
				24, AREA_SIZE / 2, AREA_SIZE / 2, AREA_SIZE / 2, 0.1);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.5f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WITCH_THROW, SoundCategory.PLAYERS, 0.8f, 0.6f);
	}

	/** 丝线强化：6 格冲刺（撞敌 2 魔法停下 → 结束后 3 格 AOE 6 魔法 + 中毒 II 15s）。 */
	private static void startDash(ServerPlayerEntity player, ServerWorld sw) {
		DashState d = new DashState();
		Vec3d look = player.getRotationVector().normalize();
		d.dir = look;
		DASHING.put(player.getUuid(), d);
		player.fallDistance = 0.0f;
		// 冲刺起手：蛛鸣 + 破空
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.9f, 1.4f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.2f);
		// 丝线强化提示：消耗丝线（冲刺沿丝荡出，锚点清除）
		JumpKillManager.consumeSilk(player);
	}

	/** 每服务端 tick 对每个在线玩家调用（推进冲刺）。 */
	public static void tick(ServerPlayerEntity player) {
		DashState d = DASHING.get(player.getUuid());
		if (d == null) return;
		if (player.isDead() || !isSalticidae(player)) { DASHING.remove(player.getUuid()); return; }
		if (!(player.getWorld() instanceof ServerWorld sw)) return;

		d.ticks++;
		// 结束条件：跑满 6 格 / 超时 / 撞墙 / 碰到敌方（碰撞伤害后停下）
		if (d.traveled >= DASH_DISTANCE || d.ticks > DASH_TIMEOUT || player.horizontalCollision || d.hitDone) {
			finishDash(player, sw);
			return;
		}

		// 冲刺推进：沿准星方向（起跳瞬间锁定方向，途中不转向——直线冲刺）
		Vec3d v = d.dir.multiply(DASH_SPEED);
		player.setVelocity(v.x, Math.min(0.1, v.y), v.z); // 竖直限幅防冲天
		player.velocityModified = true;
		player.fallDistance = 0.0f;
		if (player.networkHandler != null) {
			player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		}
		d.traveled += DASH_SPEED;

		// 途中碰撞判定：碰到敌方生物 → 2 魔法 + 停下
		Box hitbox = player.getBoundingBox().expand(0.5);
		List<LivingEntity> hits = sw.getEntitiesByClass(LivingEntity.class, hitbox,
				e -> e != player && e.isAlive() && !e.isSpectator()
						&& !WhitelistUtils.isProtected(player, e));
		if (!hits.isEmpty()) {
			for (LivingEntity t : hits) {
				t.damage(t.getDamageSources().indirectMagic(player, player), DASH_HIT_DAMAGE);
			}
			d.hitDone = true; // 本 tick 结束后停下
		}

		// 冲刺拖尾
		sw.spawnParticles(ParticleTypes.WITCH, player.getX(), player.getBodyY(0.5), player.getZ(),
				3, 0.15, 0.2, 0.15, 0.05);
	}

	/** 冲刺结束：以自身为圆心 3 格 AOE——6 魔法 + 中毒 II 15s（毒液腺体：等级+1 / 时长×70%）。 */
	private static void finishDash(ServerPlayerEntity player, ServerWorld sw) {
		DASHING.remove(player.getUuid());
		Vec3d c = player.getPos();
		Box box = player.getBoundingBox().expand(BURST_RADIUS);
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator()
						&& !WhitelistUtils.isProtected(player, e)
						&& e.getPos().distanceTo(c) <= BURST_RADIUS);
		boolean gland = net.jackcooper.shapeShifterCurseAddon.item.VenomGlandItem.isWearingBy(player);
		int amp = (gland ? 1 : 0) + 1; // 基础中毒 II，腺体 +1
		int dur = gland ? Math.round(BURST_POISON_DURATION * net.jackcooper.shapeShifterCurseAddon.item.VenomGlandItem.DURATION_SCALE) : BURST_POISON_DURATION;
		for (LivingEntity t : targets) {
			t.damage(t.getDamageSources().indirectMagic(player, player), BURST_DAMAGE);
			t.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, dur, amp, false, true, true), player);
		}
		// AOE 反馈：毒爆粒子环 + 女巫泼溅
		sw.spawnParticles(ParticleTypes.WITCH, c.x, c.y + 0.5, c.z,
				60, BURST_RADIUS * 0.7, 0.6, BURST_RADIUS * 0.7, 0.2);
		sw.spawnParticles(ParticleTypes.CLOUD, c.x, c.y + 0.3, c.z,
				16, 0.6, 0.3, 0.6, 0.05);
		sw.playSound(null, c.x, c.y, c.z,
				SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 1.0f, 0.9f);
		sw.playSound(null, c.x, c.y, c.z,
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.7f);
	}

	/** 断线/停服清理。 */
	public static void clearPlayer(UUID uuid) {
		DASHING.remove(uuid);
	}

	public static void clearAll() {
		DASHING.clear();
	}
}
