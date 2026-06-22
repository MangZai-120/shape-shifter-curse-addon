package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP 美西螈 - 漩涡蓄力技能（服务端状态机）。
 *
 * 按 sp_primary 开始蓄力；每 10 tick 扣 8 湿润值(air) 且伤害 +2（基础 0）；
 * 最大 4 秒(80t / 8 次)、最大消耗 60 湿润值；中途再按 sp_primary 立即释放、满 4 秒自动释放。
 * 释放：半径 3 范围物理伤害 = 已扣次数 × 2 + 缓慢 III + 击退；CD 15 秒(300t) 释放后才起算。
 *
 * vortex_state 资源（my_addon:form_axolotl_sp_vortex_impact_vortex_state）用作"蓄力中"标记并同步客户端：
 * 0 = 未蓄力，>0 = 蓄力中（值=已蓄力 tick 数）。客户端据此决定按键是"开始"还是"释放"。
 */
public final class VortexChargeManager {
	public static final Identifier VORTEX_STATE =
			new Identifier("my_addon", "form_axolotl_sp_vortex_impact_vortex_state");

	private static final int AIR_PER_HIT = 8;
	private static final int MAX_AIR_SPENT = 60;
	private static final int MAX_TICKS = 80;     // 4 秒
	private static final int HIT_INTERVAL = 10;  // 每 10 tick 扣一次
	private static final int DAMAGE_PER_HIT = 2;
	private static final int CD_TICKS = 300;     // 15 秒
	private static final double RADIUS = 3.0;

	private static final Map<UUID, ChargeState> CHARGING = new ConcurrentHashMap<>();

	private static final class ChargeState {
		int ticks = 0;
		int hits = 0;
		int airSpent = 0;
	}

	private VortexChargeManager() {
	}

	public static boolean isCharging(ServerPlayerEntity player) {
		return CHARGING.containsKey(player.getUuid());
	}

	/** 客户端发「开始蓄力」包时调用。 */
	public static void start(ServerPlayerEntity player) {
		if (CHARGING.containsKey(player.getUuid())) return;
		if (!FormUtils.isAxolotlSP(player)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // CD 中
		if (player.getAir() < AIR_PER_HIT) return; // 至少够扣一次
		CHARGING.put(player.getUuid(), new ChargeState());
		PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 1); // 标记蓄力中（客户端读 >0）
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.5f, 0.5f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.6f, 1.5f);
		sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.6);
		sw.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.2f, 0.6f, 0.9f), 2.0f),
				player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
		sw.spawnParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		ChargeState s = CHARGING.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !FormUtils.isAxolotlSP(player)) {
			cancel(player); // 形态丢失/死亡 → 取消，不结算
			return;
		}
		s.ticks++;
		if (s.ticks % HIT_INTERVAL == 0) {
			if (s.airSpent < MAX_AIR_SPENT && player.getAir() >= AIR_PER_HIT) {
				int spend = Math.min(AIR_PER_HIT, MAX_AIR_SPENT - s.airSpent);
				player.setAir(player.getAir() - spend);
				s.airSpent += spend;
				s.hits++;
				ServerWorld sw = (ServerWorld) player.getWorld();
				sw.spawnParticles(ParticleTypes.BUBBLE,
						player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.6);
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.8f, 0.6f);
			} else {
				release(player); // air 不足或已扣满 60 → 自动释放
				return;
			}
		}
		PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, s.ticks); // 同步蓄力状态
		if (s.ticks >= MAX_TICKS) {
			release(player); // 满 4 秒自动释放
		}
	}

	/** 客户端发「释放」包 或 自动释放时调用。 */
	public static void release(ServerPlayerEntity player) {
		ChargeState s = CHARGING.remove(player.getUuid());
		PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 0);
		if (s == null) return;
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS); // CD 释放后起算
		int damage = s.hits * DAMAGE_PER_HIT;
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.2f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.5f, 0.5f);
		sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(),
				150, RADIUS, 1.0, RADIUS, 1.0);
		sw.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1, player.getZ(),
				8, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.1);
		if (damage <= 0) return; // 一次都没蓄到，仅取消
		Box box = player.getBoundingBox().expand(RADIUS);
		for (Entity e : sw.getOtherEntities(player, box)) {
			if (e instanceof LivingEntity living) {
				living.damage(player.getDamageSources().mobAttack(player), damage); // 物理
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
				Vec3d push = living.getPos().subtract(player.getPos());
				if (push.lengthSquared() < 1.0e-4) push = new Vec3d(0, 1, 0);
				push = push.normalize().multiply(0.8);
				living.setVelocity(push.x, 0.6, push.z);
				living.velocityModified = true;
			}
		}
	}

	/** 取消蓄力（不结算伤害、不进 CD）。 */
	public static void cancel(ServerPlayerEntity player) {
		if (CHARGING.remove(player.getUuid()) != null) {
			PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 0);
		}
	}

	public static void onPlayerDisconnect(UUID uuid) {
		CHARGING.remove(uuid);
	}
}
