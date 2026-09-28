package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
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
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;

import java.util.List;

// 吸血蝙蝠次要技能：直线超声波（3.5 格宽（直径）× 8 格长的圆柱作用域）
// 命中目标：4 点 playerAttack 伤害；玩家附加 DEAFEN 60t（静音）；非玩家附加 BLINDNESS 60t（模拟听觉抽离）；统一附加 NAUSEA 60t（反胃）
// 默认白名单：玩家及其宠物/召唤物豁免
// CD 默认 8 秒（160t），由本 power JSON 的 cooldown 字段配置
public class BatSonicWaveAbilityPower extends ActiveCooldownPower implements net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder {

	private static final double RANGE = 8.0;          // 默认长度（8 格）；运行时从 balance 快照读取（可数据包覆盖）
	private static final double HALF_WIDTH = 1.75;    // 默认宽度半径（3.5 格直径）
	private static final float DAMAGE = 6.0f;   // 默认伤害
	private static final int DEBUFF_TICKS = 60; // 默认负面时长（3 秒）

	// 阶段 4：服务端权威快照读取（快照未初始化回退默认常量；伤害/范围/时长可由 balance 数据包覆盖）
	private static double range() { var s = BalanceIntegration.currentSnapshot(); return s != null ? s.getDouble("abilities.bat_sonic_wave", "range") : RANGE; }
	private static double halfWidth() { var s = BalanceIntegration.currentSnapshot(); return s != null ? s.getDouble("abilities.bat_sonic_wave", "half_width") : HALF_WIDTH; }
	private static float damage() { var s = BalanceIntegration.currentSnapshot(); return s != null ? (float) s.getDouble("abilities.bat_sonic_wave", "damage") : DAMAGE; }
	private static int debuffTicks() { var s = BalanceIntegration.currentSnapshot(); return s != null ? (int) s.getInt("abilities.bat_sonic_wave", "debuff_ticks") : DEBUFF_TICKS; }
	private final net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec;
	private long internalCooldownEndTime = 0L;

	public BatSonicWaveAbilityPower(PowerType<?> type, LivingEntity entity,
	                                net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec, HudRender hudRender, Active.Key key) {
		super(type, entity, Math.max(1, spec.cooldown()), hudRender, (e) -> {
		});
		this.spec = spec;
		this.setKey(key);
	}

	@Override
	public net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec cooldownSpec() {
		return spec;
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "sonic_wave"),
				net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.addFields(new SerializableData()
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
						160, SkillCastManager.START_ON_CAST),
				data -> {
					var spec = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.read(data);
					return (type, player) -> new BatSonicWaveAbilityPower(type, player, spec, data.get("hud_render"), data.get("key"));
				}
		).allowCondition();
	}

	private boolean isInternalCooldownReady() {
		if (entity instanceof ServerPlayerEntity sp) {
			return net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(sp, powerIdentifier());
		}
		return entity.getWorld().getTime() >= internalCooldownEndTime;
	}

	private void applyCooldown() {
		internalCooldownEndTime = entity.getWorld().getTime() + spec.cooldown();
		if (entity instanceof ServerPlayerEntity sp) {
			net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.instant(sp, powerIdentifier());
		}
	}

	/** 本 power 的稳定技能 ID（= power 注册路径）。 */
	public String powerIdentifier() {
		return type != null && type.getIdentifier() != null
				? type.getIdentifier().toString() : "my_addon:sonic_wave";
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public void onUse() {
        if (entity instanceof net.minecraft.server.network.ServerPlayerEntity syncPlayer
                && !net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isPlayerReady(syncPlayer)) return;
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

		double range = range();
		double halfWidth = halfWidth();
		double halfWidthSq = halfWidth * halfWidth;   // 派生值：从同源快照值计算，不独立开放

		Box box = new Box(eye.x - range, eye.y - range, eye.z - range,
				eye.x + range, eye.y + range, eye.z + range);
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
			// 沿视线投影距离：必须在 [0, range] 区间内（排除背后与超远目标）
			double forward = toTarget.dotProduct(look);
			if (forward <= 0.0 || forward > range) continue;
			// 垂直于视线的偏离平方：必须在圆柱半径内
			double perpSq = toTarget.lengthSquared() - forward * forward;
			if (perpSq > halfWidthSq) continue;
			// 默认白名单：玩家/宠物/召唤物豁免
			if (entity instanceof ServerPlayerEntity sp && WhitelistUtils.isProtected(sp, target)) continue;

			target.damage(source, damage());
			// 反胃（统一）；带施法者 source 供入梦拦截归因
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, debuffTicks(), 0, false, true, true), entity);
			// 失聪：玩家用自定义 DEAFEN（客户端静音）；非玩家附加短暂失明模拟听觉抽离
			if (target instanceof PlayerEntity) {
				target.addStatusEffect(new StatusEffectInstance(SscAddon.DEAFEN, debuffTicks(), 0, false, true, true), entity);
			} else {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, debuffTicks(), 0, false, true, true), entity);
			}
			hits.add(target);
		}

		// 直线粒子表现：从眼睛沿视角方向喷射 SONIC_BOOM
		spawnBeamParticles(world, eye, look);
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.6f, 1.6f);

		// 命中触发血渴值结算
		if (!hits.isEmpty() && entity instanceof ServerPlayerEntity sp) {
			net.jackcooper.shapeShifterCurseAddon.ability.BatDesmodusBloodThirst.onSkillHit(sp, hits);
		}
	}

	private void spawnBeamParticles(ServerWorld world, Vec3d origin, Vec3d look) {
		double range = range();
		double halfWidth = halfWidth();
		// 主轴：从眼前 0.5 格起，向前每 0.7 格一发 SONIC_BOOM
		for (double d = 0.5; d <= range; d += 0.7) {
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
			if (d > range) break;   // 粒子采样不超出快照范围
			Vec3d center = origin.add(look.multiply(d));
			for (int i = 0; i < 12; i++) {
				double ang = 2 * Math.PI * i / 12;
				Vec3d off = perp1.multiply(Math.cos(ang) * halfWidth).add(perp2.multiply(Math.sin(ang) * halfWidth));
				Vec3d p = center.add(off);
				ParticleUtils.spawnParticles(world, ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
