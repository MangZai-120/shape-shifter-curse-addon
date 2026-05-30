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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.BatBlockAttachPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;

/**
 * 幽雾化形 - 吸血蝙蝠形态核心主动技能。
 * 按下后玩家模型消散为一团雾（原版隐身 → SSC 形态渲染自动跳过），
 * 雾化期间除虚空伤害外免疫一切伤害（由数据驱动的 invulnerability 提供）。
 * 雾化持续 effectDuration tick，结束后进入 cooldownTicks 的冷却（通过 SP_PRIMARY_CD 资源驱动CD条）。
 * 雾化满 1 秒后再次按主动键可凝聚爆破；被 sp悦灵 净化（PURIFIED）会立即中断雾化并进入冷却。
 * 全部状态判定与计时均在服务端执行，保证多人/主客机一致性。
 */
public class MistFormAbilityPower extends ActiveCooldownPower {

	private final int effectDuration; // 雾化持续 tick
	private final int cooldownTicks;  // 雾化结束后冷却 tick
	// 基于服务端世界时间的内部冷却，避免多人环境下计时漂移
	private long internalCooldownEndTime = 0L;
	private boolean wasMist = false;
	// 凝聚爆破相关
	private static final int MIST_BURST_DELAY = 20;   // 化雾后可引爆的最短间隔 tick（1秒）
	private static final int CHARGE_DURATION = 20;    // 凝聚爆破蓄力时长 tick（1秒）
	private static final double AOE_RADIUS = 4.0;     // 爆破半径（格）
	private static final float AOE_DAMAGE = 6.0f;     // 爆破伤害
	private static final double AOE_KNOCKBACK = 1.0;  // 击退强度
	private long mistStartTime = 0L;  // 本次化雾起始时间
	private boolean charging = false;      // 凝聚爆破蓄力中
	private long chargeStartTime = 0L;     // 蓄力起始时间
	// 血雾红色尘埃粒子（体现“血”雾）
	private static final DustParticleEffect BLOOD_DUST = new DustParticleEffect(new org.joml.Vector3f(0.78f, 0.06f, 0.06f), 1.2f);
	// 雾血资源相关（0~100）
	private static final int BLOOD_ENTER_COST = 0;     // 进入雾化起手消耗（无消耗）
	private static final int BLOOD_PER_SECOND_COST = 0; // 雾化期间每秒持续消耗（无消耗）
	private static final int BLOOD_BURST_COST = 0;     // 凝聚爆破额外消耗（无消耗）
	private long lastBloodDrainTime = 0L;               // 上次每秒扣血时间

	public MistFormAbilityPower(PowerType<?> type, LivingEntity entity, int cooldownAfter, int effectDuration, int cooldownTicks, HudRender hudRender, Active.Key key) {
		super(type, entity, cooldownAfter, hudRender, (e) -> {
		});
		this.effectDuration = effectDuration;
		this.cooldownTicks = cooldownTicks;
		this.setKey(key);
		this.setTicking(true);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "mist_form"),
				new SerializableData()
						.add("cooldown", SerializableDataTypes.INT, 200)
						.add("duration", SerializableDataTypes.INT, 90)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
				data ->
						(type, player) -> new MistFormAbilityPower(
								type,
								player,
								data.getInt("cooldown"),
								data.getInt("duration"),
								data.getInt("cooldown"),
								data.get("hud_render"),
								data.get("key")
						)
		).allowCondition();
	}

	private boolean isMist() {
		return entity.hasStatusEffect(SscAddon.MIST_FORM);
	}

	/** 读取当前雾血值（仅服务端有效） */
	private int getBlood() {
		if (entity instanceof ServerPlayerEntity serverPlayer) {
			return PowerUtils.getResourceValue(serverPlayer, FormIdentifiers.BAT_BLOOD_RESOURCE);
		}
		return 0;
	}

	/** 设置雾血值并同步（自动夹取 0~100） */
	private void setBlood(int value) {
		if (entity instanceof ServerPlayerEntity serverPlayer) {
			int v = Math.max(0, Math.min(100, value));
			PowerUtils.setResourceValueAndSync(serverPlayer, FormIdentifiers.BAT_BLOOD_RESOURCE, v);
		}
	}

	/** 雾化期间豁免 vanilla 服务器悬空踢人检测（悦灵同款：用无重力而非 allowFlying，避免误触原版创造飞行），退出时复位；创造/观察模式不改动。仅服务端生效 */
	private void setFlightExempt(boolean exempt) {
		if (!(entity instanceof ServerPlayerEntity sp)) return;
		if (sp.isCreative() || sp.isSpectator()) return;
		sp.setNoGravity(exempt);
	}

	/** 内部冷却是否就绪（服务端tick基准） */
	public boolean isInternalCooldownReady() {
		return entity.getWorld().getTime() >= internalCooldownEndTime;
	}

	/** 进入冷却并同步CD条资源 */
	private void applyCooldown() {
		internalCooldownEndTime = entity.getWorld().getTime() + cooldownTicks;
		if (entity instanceof ServerPlayerEntity serverPlayer) {
			PowerUtils.setResourceValueAndSync(serverPlayer, FormIdentifiers.SP_PRIMARY_CD, cooldownTicks);
		}
	}

	/** 进入雾化状态：标记效果 + 原版隐身（让形态模型消失）+ 起始粒子与音效 */
	private void enterMist() {
		// 进入雾化前解除蝙蝠的右键贴墙附着，避免附着锁定与化雾飞行相互冲突
		if (entity instanceof PlayerEntity player) {
			PowerHolderComponent.getPowers(player, BatBlockAttachPower.class).stream()
					.filter(BatBlockAttachPower::isAttached).findFirst()
					.ifPresent(p -> p.detach(player, false));
		}
		entity.addStatusEffect(new StatusEffectInstance(SscAddon.MIST_FORM, effectDuration, 0, false, false, true));
		// 原版隐身：FormRenderFeature 在 isInvisible() 时跳过形态模型渲染，从而实现“模型消失”
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, effectDuration, 0, false, false, false));
		entity.calculateDimensions();
		wasMist = true;
		mistStartTime = entity.getWorld().getTime();
		lastBloodDrainTime = entity.getWorld().getTime();
		// 进入雾化起手扣血
		setBlood(getBlood() - BLOOD_ENTER_COST);

		ServerWorld serverWorld = (ServerWorld) entity.getWorld();
		double cx = entity.getX();
		double cy = entity.getY() + entity.getHeight() * 0.5;
		double cz = entity.getZ();
		// 进入雾化：浓密雾团（CLOUD 主体 + 灰烟点缀），明显可见的化雾爆发
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD,
				cx, cy, cz, 80, 0.5, 0.7, 0.5, 0.04);
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CAMPFIRE_COSY_SMOKE,
				cx, cy, cz, 50, 0.45, 0.7, 0.45, 0.02);
		// 红色血雾点缀
		ParticleUtils.spawnParticles(serverWorld, BLOOD_DUST,
				cx, cy, cz, 30, 0.5, 0.7, 0.5, 0.0);
		serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 0.8f);
		// 进入雾化：开启服务端飞行豁免，防 vanilla 服务器飞行检测踢人
		setFlightExempt(true);
	}

	/** 退出雾化状态：清除雾化与隐身效果，可选播放收束粒子与音效 */
	private void exitMist(boolean withEffect) {
		if (entity.hasStatusEffect(SscAddon.MIST_FORM)) {
			entity.removeStatusEffect(SscAddon.MIST_FORM);
		}
		if (entity.hasStatusEffect(StatusEffects.INVISIBILITY)) {
			entity.removeStatusEffect(StatusEffects.INVISIBILITY);
		}
		entity.calculateDimensions();
		wasMist = false;
		// 退出雾化：复位飞行豁免
		setFlightExempt(false);

		if (withEffect && entity.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CAMPFIRE_COSY_SMOKE,
					entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
					25, 0.4, 0.6, 0.4, 0.02);
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 1.2f);
		}
	}

	/** 凝聚爆破：在原地引爆一团雾，对范围内非白名单生物造成伤害与击退 */
	private void detonate() {
		ServerWorld serverWorld = (ServerWorld) entity.getWorld();
		DamageSource source = entity.getDamageSources().magic();
		Box box = entity.getBoundingBox().expand(AOE_RADIUS);
		double radiusSq = AOE_RADIUS * AOE_RADIUS;
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
				living -> living != entity && living.isAlive());
		// 收集真正命中（非白名单）的目标，供血渴值「凝聚爆破命中 +12/6/3」结算
		java.util.List<LivingEntity> hitTargets = new java.util.ArrayList<>();
		for (LivingEntity target : targets) {
			if (entity.squaredDistanceTo(target) > radiusSq) continue;
			// 默认白名单：受保护个体（玩家及其宠物/召唤物）不受伤害与击退
			if (entity instanceof ServerPlayerEntity serverPlayer && WhitelistUtils.isProtected(serverPlayer, target)) {
				continue;
			}
			target.damage(source, AOE_DAMAGE);
			// 从玩家位置向外击退
			target.takeKnockback(AOE_KNOCKBACK, entity.getX() - target.getX(), entity.getZ() - target.getZ());
			hitTargets.add(target);
		}
		if (entity instanceof ServerPlayerEntity serverPlayer && !hitTargets.isEmpty()) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst
					.onSkillHit(serverPlayer, hitTargets);
		}
		// 爆破粒子与音效
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.EXPLOSION,
				entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
				8, 1.0, 1.0, 1.0, 0.0);
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CAMPFIRE_COSY_SMOKE,
				entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
				60, AOE_RADIUS * 0.5, 0.8, AOE_RADIUS * 0.5, 0.05);
		// 血雾红色尘埃：集中起爆点 + 大速度 → 突然向外快速扩散
		ParticleUtils.spawnParticles(serverWorld, BLOOD_DUST,
				entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
				50, 0.1, 0.1, 0.1, 0.7);
		serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	/** 进入凝聚爆破蓄力：减速50%（MIST_CHARGING 标记驱动）+ 防雾化提前消失（延长至爆破完成）+ 蓄力起手音效 */
	private void startCharge(long now) {
		charging = true;
		chargeStartTime = now;
		setBlood(getBlood() - BLOOD_BURST_COST);
		// 蓄力标记：客户端据此将化雾飞行减速 50%
		entity.addStatusEffect(new StatusEffectInstance(SscAddon.MIST_CHARGING, CHARGE_DURATION + 5, 0, false, false, false));
		// 防止蓄力期间雾化提前结束：剩余不足则延长 MIST_FORM/隐身至爆破完成
		StatusEffectInstance mistInst = entity.getStatusEffect(SscAddon.MIST_FORM);
		if (mistInst == null || mistInst.getDuration() < CHARGE_DURATION + 5) {
			entity.addStatusEffect(new StatusEffectInstance(SscAddon.MIST_FORM, CHARGE_DURATION + 5, 0, false, false, true));
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, CHARGE_DURATION + 5, 0, false, false, false));
		}
		if (entity.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
		}
	}

	/** 蓄力期间的向内聚集粒子：外圈红色血雾以 count=0 定向粒子朝中心收束，半径随蓄力缩小 */
	private void spawnChargeConvergence(long elapsed) {
		if (!(entity.getWorld() instanceof ServerWorld sw)) return;
		double px = entity.getX();
		double py = entity.getY() + entity.getHeight() * 0.5;
		double pz = entity.getZ();
		double progress = Math.min(1.0, elapsed / (double) CHARGE_DURATION);
		double radius = 2.6 * (1.0 - progress) + 0.4; // 半径随蓄力缩小，营造向内聚集
		int n = 10;
		for (int i = 0; i < n; i++) {
			double ang = 2 * Math.PI * i / n + elapsed * 0.35;
			double ox = Math.cos(ang) * radius;
			double oz = Math.sin(ang) * radius;
			double oy = (entity.getRandom().nextDouble() - 0.5) * entity.getHeight();
			// count=0 定向粒子：初速度 = (offset)*speed，方向指向中心 → 向内聚集
			ParticleUtils.spawnParticles(sw, BLOOD_DUST, px + ox, py + oy, pz + oz,
					0, -ox, -oy, -oz, 0.3);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (entity == null || entity.getWorld().isClient) return;

		// 被 sp悦灵净化：立即中断雾化（含蓄力）并进入冷却
		if (entity.hasStatusEffect(SscAddon.PURIFIED)) {
			if (isMist()) {
				exitMist(true);
				applyCooldown();
			}
			charging = false;
			if (entity.hasStatusEffect(SscAddon.MIST_CHARGING)) {
				entity.removeStatusEffect(SscAddon.MIST_CHARGING);
			}
			wasMist = false;
			return;
		}

		boolean mist = isMist();

		// 凝聚爆破蓄力进行中：持续向内聚集粒子，蓄满 1 秒后突然引爆向外扩散
		if (charging) {
			long elapsed = entity.getWorld().getTime() - chargeStartTime;
			spawnChargeConvergence(elapsed);
			if (elapsed >= CHARGE_DURATION) {
				charging = false;
				if (entity.hasStatusEffect(SscAddon.MIST_CHARGING)) {
					entity.removeStatusEffect(SscAddon.MIST_CHARGING);
				}
				detonate();
				exitMist(false);
				applyCooldown();
				wasMist = false;
			}
			return; // 蓄力期间不走常规雾化逻辑（雾血扣减/自然结束/普通粒子）
		}

		// 自然结束检测：上一tick仍在雾化、本tick效果到期消失 -> 进入冷却
		if (wasMist && !mist) {
			if (entity.hasStatusEffect(StatusEffects.INVISIBILITY)) {
				entity.removeStatusEffect(StatusEffects.INVISIBILITY);
			}
			applyCooldown();
			// 自然结束：复位飞行豁免
			setFlightExempt(false);
			entity.calculateDimensions();
			if (entity.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 1.2f);
			}
			wasMist = false;
			return;
		}

		// 雾化持续期间：每秒持续消耗雾血，耗尽则强制解除；并散发雾气粒子
		if (mist) {
			if (entity.getWorld().getTime() - lastBloodDrainTime >= 20) {
				lastBloodDrainTime = entity.getWorld().getTime();
				if (BLOOD_PER_SECOND_COST > 0) {
					int newBlood = getBlood() - BLOOD_PER_SECOND_COST;
					setBlood(newBlood);
					if (newBlood <= 0) {
						// 雾血耗尽：强制解除雾化并进入冷却
						exitMist(true);
						applyCooldown();
						wasMist = false;
						return;
					}
				}
				// 75-100 血渴值阶段：血雾光环每秒对周围 2 格内非白名单生物造成 2 点伤害，每命中一次回吸血蝙蝠 2 点生命
				if (entity instanceof ServerPlayerEntity sp
						&& net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.getStage(sp) == 3) {
					Box aura = entity.getBoundingBox().expand(2.0, 2.0, 2.0);
					double auraSq = 2.0 * 2.0;
					DamageSource auraSrc = entity.getDamageSources().magic();
					List<LivingEntity> auraTargets = ((ServerWorld) entity.getWorld()).getEntitiesByClass(
							LivingEntity.class, aura, t -> t != entity && t.isAlive());
					for (LivingEntity t : auraTargets) {
						if (entity.squaredDistanceTo(t) > auraSq) continue;
						if (WhitelistUtils.isProtected(sp, t)) continue;
						net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.SUPPRESS_OUTGOING_BUFF.set(true);
						boolean dealt;
						try {
							dealt = t.damage(auraSrc, 2.0f);
						} finally {
							net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.SUPPRESS_OUTGOING_BUFF.set(false);
						}
						if (dealt) {
							sp.heal(2.0f);
						}
					}
				}
			}
			// 持续雾化：每 tick 生成浓雾团包裹全身，明显可见
			ServerWorld serverWorld = (ServerWorld) entity.getWorld();
			double px = entity.getX();
			double py = entity.getY() + entity.getHeight() * 0.5;
			double pz = entity.getZ();
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD,
					px, py, pz, 6, 0.4, 0.6, 0.4, 0.01);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CAMPFIRE_COSY_SMOKE,
					px, py - entity.getHeight() * 0.05, pz, 3, 0.35, 0.5, 0.35, 0.01);
			// 红色血雾点缀：少量红色尘埃掺杂在雾中，体现“血”雾
			ParticleUtils.spawnParticles(serverWorld, BLOOD_DUST,
					px, py, pz, 2, 0.4, 0.6, 0.4, 0.0);
			wasMist = true;
		}
	}

	@Override
	public boolean canUse() {
		// 始终允许触发，冷却逻辑在 onUse 内部处理
		return true;
	}

	@Override
	public void onUse() {
		if (entity == null || entity.getWorld().isClient) return;
		// 净化期间禁止施放
		if (entity.hasStatusEffect(SscAddon.PURIFIED)) return;

		long now = entity.getWorld().getTime();
		if (isMist()) {
			// 凝聚爆破需化雾满 MIST_BURST_DELAY 后才可触发；蓄力中再按键无效
			boolean burstReady = now - mistStartTime >= MIST_BURST_DELAY;
			if (burstReady && getBlood() >= BLOOD_BURST_COST && !charging) {
				// 再次按主动键：进入凝聚爆破蓄力（减速50%蓄力1秒后引爆）
				startCharge(now);
			}
		} else if (isInternalCooldownReady() && getBlood() >= BLOOD_ENTER_COST) {
			// 冷却就续且雾血足够：进入雾化
			enterMist();
		}
	}
	@Override
	public void onLost() {
		super.onLost();
		if (entity == null || entity.getWorld().isClient) return;
		// 形态切换/能力移除时兜底：解除雾化、蓄力与飞行豁免，避免残留作弊飞行
		charging = false;
		if (entity.hasStatusEffect(SscAddon.MIST_CHARGING)) {
			entity.removeStatusEffect(SscAddon.MIST_CHARGING);
		}
		if (isMist()) {
			exitMist(false);
		}
		setFlightExempt(false);
	}}
