package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;

// 吸血蝙蝠次要技能：直线超声波（3.5 格宽（直径）× 8 格长的圆柱作用域）
// 命中目标：4 点 playerAttack 伤害；玩家附加 DEAFEN 60t（静音）；非玩家附加 BLINDNESS 60t（模拟听觉抽离）；统一附加 NAUSEA 60t（反胃）
// 默认白名单：玩家及其宠物/召唤物豁免
// CD 8 秒（160t），通过 SP_SECONDARY_CD 资源驱动 HUD CD 条
public class BatSonicWaveAbilityPower extends ActiveCooldownPower {

	private static final double RANGE = 8.0;          // 长度（8 格）
	private static final double HALF_WIDTH = 1.75;    // 宽度半径（3.5 格直径）
	private static final double HALF_WIDTH_SQ = HALF_WIDTH * HALF_WIDTH;
	private static final float DAMAGE = 4.0f;
	private static final int DEBUFF_TICKS = 60; // 3 秒
	private final int cooldownTicks;
	private long internalCooldownEndTime = 0L;

	public BatSonicWaveAbilityPower(PowerType<?> type, LivingEntity entity, int cooldownTicks, HudRender hudRender, Active.Key key) {
		super(type, entity, cooldownTicks, hudRender, (e) -> {
		});
		this.cooldownTicks = cooldownTicks;
		this.setKey(key);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "sonic_wave"),
				new SerializableData()
						.add("cooldown", SerializableDataTypes.INT, 160)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
				data ->
						(type, player) -> new BatSonicWaveAbilityPower(
								type,
								player,
								data.getInt("cooldown"),
								data.get("hud_render"),
								data.get("key")
						)
		).allowCondition();
	}

	private boolean isInternalCooldownReady() {
		return entity.getWorld().getTime() >= internalCooldownEndTime;
	}

	private void applyCooldown() {
		internalCooldownEndTime = entity.getWorld().getTime() + cooldownTicks;
		if (entity instanceof ServerPlayerEntity sp) {
			PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.SP_SECONDARY_CD, cooldownTicks);
		}
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public void onUse() {
		if (entity == null || entity.getWorld().isClient) return;
		if (entity.hasStatusEffect(SscAddon.PURIFIED)) return;
		if (!isInternalCooldownReady()) return;
		// 雾化期间禁止释放，避免与雾化爆破节奏冲突
		if (entity.hasStatusEffect(SscAddon.MIST_FORM) || entity.hasStatusEffect(SscAddon.MIST_CHARGING)) return;

		fire();
		applyCooldown();
	}

	private void fire() {
		ServerWorld world = (ServerWorld) entity.getWorld();
		Vec3d eye = entity.getEyePos();
		Vec3d look = entity.getRotationVec(1.0F).normalize();

		Box box = new Box(eye.x - RANGE, eye.y - RANGE, eye.z - RANGE,
				eye.x + RANGE, eye.y + RANGE, eye.z + RANGE);
		List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, box,
				living -> living != entity && living.isAlive());

		DamageSource source;
		if (entity instanceof PlayerEntity player) {
			source = entity.getDamageSources().playerAttack(player);
		} else {
			source = entity.getDamageSources().mobAttack(entity);
		}

		java.util.List<LivingEntity> hits = new java.util.ArrayList<>();
		for (LivingEntity target : candidates) {
			Vec3d toTarget = target.getBoundingBox().getCenter().subtract(eye);
			// 沿视线投影距离：必须在 [0, RANGE] 区间内（排除背后与超远目标）
			double forward = toTarget.dotProduct(look);
			if (forward <= 0.0 || forward > RANGE) continue;
			// 垂直于视线的偏离平方：必须在圆柱半径内
			double perpSq = toTarget.lengthSquared() - forward * forward;
			if (perpSq > HALF_WIDTH_SQ) continue;
			// 默认白名单：玩家/宠物/召唤物豁免
			if (entity instanceof ServerPlayerEntity sp && WhitelistUtils.isProtected(sp, target)) continue;

			target.damage(source, DAMAGE);
			// 反胃（统一）
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, DEBUFF_TICKS, 0, false, true, true));
			// 失聪：玩家用自定义 DEAFEN（客户端静音）；非玩家附加短暂失明模拟听觉抽离
			if (target instanceof PlayerEntity) {
				target.addStatusEffect(new StatusEffectInstance(SscAddon.DEAFEN, DEBUFF_TICKS, 0, false, true, true));
			} else {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, DEBUFF_TICKS, 0, false, true, true));
			}
			hits.add(target);
		}

		// 直线粒子表现：从眼睛沿视角方向喷射 SONIC_BOOM
		spawnBeamParticles(world, eye, look);
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.6f);

		// 命中触发血渴值结算
		if (!hits.isEmpty() && entity instanceof ServerPlayerEntity sp) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.onSkillHit(sp, hits);
		}
	}

	private void spawnBeamParticles(ServerWorld world, Vec3d origin, Vec3d look) {
		// 主轴：从眼前 0.5 格起，向前每 0.7 格一发 SONIC_BOOM
		for (double d = 0.5; d <= RANGE; d += 0.7) {
			Vec3d p = origin.add(look.multiply(d));
			ParticleUtils.spawnParticles(world, ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		// 圆柱表面表现：在前方 2 / 4 / 6 / 8 格截面，沿半径 1.75 的圆环撒云雾
		Vec3d perp1 = new Vec3d(-look.z, 0, look.x);
		if (perp1.lengthSquared() < 1e-6) {
			perp1 = new Vec3d(1, 0, 0);
		}
		perp1 = perp1.normalize();
		Vec3d perp2 = look.crossProduct(perp1).normalize();
		for (double d : new double[]{2.0, 4.0, 6.0, 8.0}) {
			Vec3d center = origin.add(look.multiply(d));
			for (int i = 0; i < 12; i++) {
				double ang = 2 * Math.PI * i / 12;
				Vec3d off = perp1.multiply(Math.cos(ang) * HALF_WIDTH).add(perp2.multiply(Math.sin(ang) * HALF_WIDTH));
				Vec3d p = center.add(off);
				ParticleUtils.spawnParticles(world, ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
