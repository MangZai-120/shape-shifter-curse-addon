package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPRangedHitPassive;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.EffectEfficiencyReductionPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntity.class)
public abstract class SscAddonLivingEntityMixin {

	/**
	 * 寄生果蝠被动：被「灵果寄生」的敌方削弱果寄生时，目标受到的任何形式回血减少 50%。
	 * heal(float) 是 vanilla 几乎所有回血的统一入口（自然回血、再生效果、金苹果等食物、治疗等），
	 * 在 HEAD 处按 0.5 倍缩放 amount 即可统一削减；持续时间跟随削弱果存在时间（由
	 * ParasiticFruitSeedPower.tick 维护的全局寄生表决定）。仅服务端判定，主客机一致。
	 */
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float ssc_addon$reduceHealWhenParasitized(float amount) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient() || amount <= 0.0f) {
			return amount;
		}
		if (net.onixary.shapeShifterCurseFabric.ssc_addon.power.ParasiticFruitSeedPower
				.isParasitizedByEnemyFruit(self.getUuid(), self.getWorld().getTime())) {
			return amount * 0.5f;
		}
		return amount;
	}

	/**
	 * 中立生物被玩家攻击时，记录全局挑衅状态。
	 * 裁决者: 攻击任何亡灵触发挑衅
	 * 金沙岚: 攻击尸壳或咒文胡狼触发挑衅
	 */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onUndeadDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof MobEntity mob
				&& source.getAttacker() instanceof PlayerEntity player) {
			// 裁决者: 所有亡灵触发挑衅
			if (mob.getGroup() == EntityGroup.UNDEAD
					&& FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), mob.getWorld().getTime());
			}
			// 金沙岚: 仅尸壳和咒文胡狼触发挑衅
			if ((mob instanceof HuskEntity || FormUtils.isTransformativeWolf(mob))
					&& FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), mob.getWorld().getTime());
			}
		}

		// ==== 金沙岚回血系统 ====
		if (!self.getWorld().isClient()) {
			// 凋零 tick 伤害 → 为已注册的金沙岚来源回血
			if (source.isOf(DamageTypes.WITHER)) {
				GoldenSandstormRegen.onWitherTickDamage(self);
			}
			// 金沙岚玩家亲手造成伤害 → 标记战斗状态
			if (source.getAttacker() instanceof ServerPlayerEntity attacker
					&& FormUtils.isForm(attacker, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
				GoldenSandstormRegen.markCombat(attacker);
			}
			// 冥狼造成伤害 → 为主人（金沙岚）标记战斗状态
			if (source.getAttacker() instanceof net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity wolf
					&& self.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
				java.util.UUID ownerUuid = wolf.getMinionOwnerUUID();
				if (ownerUuid != null) {
					net.minecraft.entity.player.PlayerEntity owner = serverWorld.getPlayerByUuid(ownerUuid);
					if (owner instanceof ServerPlayerEntity ownerPlayer
							&& FormUtils.isForm(ownerPlayer, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
						GoldenSandstormRegen.markCombat(ownerPlayer);
					}
				}
			}
		}
	}

	/**
	 * 拦截带源的 addStatusEffect：当金沙岚玩家给受害者施加凋零时，注册凋零来源用于回血。
	 */
	@Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
	private void ssc_addon$registerGoldenSandstormWitherSource(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (effect.getEffectType() != StatusEffects.WITHER) return;
		if (!(source instanceof ServerPlayerEntity sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.GOLDEN_SANDSTORM_SP)) return;
		LivingEntity self = (LivingEntity) (Object) this;
		GoldenSandstormRegen.registerWitherSource(self, sp, effect.getDuration());
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void ssc_addon$onAllayRangedHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		LivingEntity self = (LivingEntity) (Object) this;
		AllaySPRangedHitPassive.onDamageApplied(self, source);
	}

	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$capAllayIncomingDamage(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient() || !FormUtils.isAllaySP(self)) return;
		DamageSource source = args.get(0);
		if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return;

		float amount = args.get(1);
		float maxDamage = self.getMaxHealth() * 0.25F;
		if (amount > maxDamage) {
			args.set(1, maxDamage);
		}
	}

	/**
	 * 寄生果蝠「感染孢子」：被感染的实体造成伤害时减免 15%。
	 * 伤害源攻击者命中：检查 attacker 是否处于感染状态，是则按 0.85x 缩放 amount。
	 */
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$infectionAttackerDamageReduction(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient()) return;
		DamageSource source = args.get(0);
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof LivingEntity living)) return;
		if (!InfectionSporeManager.isInfected(living.getUuid())) return;
		float amount = args.get(1);
		args.set(1, InfectionSporeManager.reduceDamageIfInfected(living, amount));
	}

	@ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private StatusEffectInstance modifyStatusEffect(StatusEffectInstance effect) {
		if (!effect.getEffectType().isInstant() && PowerHolderComponent.hasPower((LivingEntity) (Object) this, EffectEfficiencyReductionPower.class)) {
			int originalAmp = effect.getAmplifier();
			int newDuration;

			// Logic:
			// Level 1 (amp 0): Duration * 0.4 (40%)
			// Level 2 (amp 1): Duration * 0.6 (60%), Amp -> 0
			// Level 3 (amp 2): Duration * 0.8 (80%), Amp -> 0
			// Level 4+ (amp 3+): Duration * 1.0 (100%), Amp -> 0

			if (originalAmp == 0) {
				// Level 1
				newDuration = (int) (effect.getDuration() * 0.4);
			} else if (originalAmp == 1) {
				// Level 2
				newDuration = (int) (effect.getDuration() * 0.6);
			} else if (originalAmp == 2) {
				// Level 3
				newDuration = (int) (effect.getDuration() * 0.8);
			} else {
				// Level 4+
				newDuration = effect.getDuration();
			}

			return new StatusEffectInstance(
					effect.getEffectType(),
					newDuration,
					0, // Always force to Level 1 (amplifier 0)
					effect.isAmbient(),
					effect.shouldShowParticles(),
					effect.shouldShowIcon(),
					null,
					effect.getFactorCalculationData()
			);
		}
		return effect;
	}

	// ============== 契灵 - 抗伤值 / 无敌帧 / 恐惧伤害修正 ==============

	/**
	 * HEAD 注入：契灵抗伤值与无敌帧。
	 * - 受害者是契灵 + iframes>0 → 取消伤害（不消耗抗伤）
	 * - 受害者是契灵 + resistance>0 → 取消伤害+取消击退，消耗1抗伤，置iframes=4tick(0.2s)
	 */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$mancianimaResistance(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayerEntity sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;
		// 跳过虚空/直接击杀，避免BUG
		if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return;
		// 仅处理"由其它玩家/生物造成的伤害"（近战、远程、魔法）。
		// 环境伤害（坠落、溺水、岩浆、火焰、窒息、仙人掌、饥饿等）的 attacker 为 null，将不抵挡也不进入战斗。
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof LivingEntity) || attacker == sp) return;
		// 受击 → 进入战斗状态（用于 15s 抗伤回复门槛）
		MancianimaMarkManager.markCombat(sp.getUuid(), sp.getServerWorld().getTime());
		int iframes = PowerUtils.getResourceValue(sp, FormIdentifiers.MANCIANIMA_IFRAMES);
		if (iframes > 0) {
			cir.setReturnValue(false);
			return;
		}
		int resist = PowerUtils.getResourceValue(sp, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (resist > 0) {
			PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.MANCIANIMA_RESISTANCE, resist - 1);
			PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.MANCIANIMA_IFRAMES, 4);
			// 抵抗触发音效：铁砧落地（全场可听见，提示周围玩家）
			sp.getServerWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND,
					net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.6f);
			cir.setReturnValue(false);
		}
	}

	/**
	 * 恐惧伤害修正：契灵 marker 对其红标受害者 +25%；红标受害者对 marker -25%。
	 * 注入 applyDamage 调用点，可同时访问 DamageSource 和 amount。
	 */
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$mancianimaFearAmount(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		DamageSource source = args.get(0);
		float amount = args.get(1);
		Entity attacker = source.getAttacker();
		// 攻击发生在契灵玩家身上 → 进入战斗（攻击方为契灵也算）
		if (attacker instanceof ServerPlayerEntity ap && FormUtils.isForm(ap, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			MancianimaMarkManager.markCombat(ap.getUuid(), ap.getServerWorld().getTime());
		}
		if (attacker instanceof ServerPlayerEntity ap
				&& MancianimaMarkManager.isRedMarkedBy(ap.getUuid(), self.getUuid())) {
			args.set(1, amount * 1.25f);
			return;
		}
		if (self instanceof ServerPlayerEntity sp && attacker != null) {
			java.util.UUID markerOf = MancianimaMarkManager.getMarkerOf(attacker.getUuid());
			if (markerOf != null && markerOf.equals(sp.getUuid())) {
				MancianimaMarkManager.Mark m = MancianimaMarkManager.getMark(sp.getUuid());
				if (m != null && m.color == MancianimaMarkManager.MarkColor.RED) {
					args.set(1, amount * 0.75f);
				}
			}
		}
	}

	// ============== 吸血蝙蝠 - 血渴值系统 ==============

	/**
	 * 蝙蝠玩家受到/造成伤害时的战斗打点（HEAD）。
	 * - 攻击方为蝙蝠玩家：标记战斗
	 * - 受害方为蝙蝠玩家：标记战斗
	 * 实际命中累计 +8 走 RETURN 分支（保证伤害真正生效）。
	 */
	@Inject(method = "damage", at = @At("HEAD"))
	private void ssc_addon$batDesmodusCombatHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient()) return;
		if (source.getAttacker() instanceof ServerPlayerEntity attacker
				&& FormUtils.isForm(attacker, FormIdentifiers.BAT_DESMODUS)) {
			BatDesmodusBloodThirst.markCombat(attacker);
		}
		if (self instanceof ServerPlayerEntity sp
				&& source.getAttacker() != null && source.getAttacker() != sp
				&& FormUtils.isForm(sp, FormIdentifiers.BAT_DESMODUS)) {
			BatDesmodusBloodThirst.markCombat(sp);
		}
		// 进化使魔战斗打点（受击或主动伤敌）：用于脱战 mana 回复判定，复用契灵的 LAST_COMBAT 计时
		if (source.getAttacker() instanceof ServerPlayerEntity atkFox
				&& FormUtils.isForm(atkFox, FormIdentifiers.UPGRADE_FAMILIAR_FOX)) {
			MancianimaMarkManager.markCombat(atkFox.getUuid(), atkFox.getServerWorld().getTime());
		}
		if (self instanceof ServerPlayerEntity defFox
				&& source.getAttacker() != null && source.getAttacker() != defFox
				&& FormUtils.isForm(defFox, FormIdentifiers.UPGRADE_FAMILIAR_FOX)) {
			MancianimaMarkManager.markCombat(defFox.getUuid(), defFox.getServerWorld().getTime());
		}
	}

	/**
	 * 蝙蝠玩家普攻命中其它生物（伤害真正生效）→ 累计 +8（受白名单与 0.3s 内CD约束）。
	 * 同时承担「造成伤害的吸血效果」：50-75 → 20%、75-100 → 35%。
	 */
	@Inject(method = "damage", at = @At("RETURN"))
	private void ssc_addon$batDesmodusOnHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient()) return;
		if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return;
		if (!FormUtils.isForm(attacker, FormIdentifiers.BAT_DESMODUS)) return;
		if (self == attacker) return;

		// 普攻命中：仅近战玩家攻击算（排除魔法 / 间接魔法 / 起爆 AOE 等）
		// 标准玩家近战伤害 source 类型为 player_attack
		boolean isMeleeAttack = source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK);
		if (isMeleeAttack) {
			BatDesmodusBloodThirst.onAttackHit(attacker, self);
		}

		// 吸血：玩家亲手造成的近战 / 起爆 AOE 都吸血（魔法源 + 玩家发起，但排除环境 / 间接伤害）
		if (isMeleeAttack || (source.getSource() == attacker
				&& !source.isOf(net.minecraft.entity.damage.DamageTypes.INDIRECT_MAGIC)
				&& !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
				&& !source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL))) {
			int stage = BatDesmodusBloodThirst.getStage(attacker);
			float lifestealRate = 0f;
			if (stage == 2) lifestealRate = 0.20f;
			else if (stage == 3) lifestealRate = 0.35f;
			// 嗜血指环：高血渴阶段（已有吸血）额外 +15% 吸血率
			if (lifestealRate > 0f && BatDesmodusBloodThirst.hasBloodlustRing(attacker)) {
				lifestealRate += 0.15f;
			}
			if (lifestealRate > 0f && amount > 0f) {
				attacker.heal(amount * lifestealRate);
			}
		}
	}

	/**
	 * 血渴值阶段对伤害的修正（同时处理 incoming 与 outgoing，复用 applyDamage 调用点）：
	 * - 受害方为蝙蝠玩家 + 0-25 阶段 → 受到伤害 ×0.85（排除虚空 / 直接击杀 / 间接魔法-喷溅滞留药水）
	 * - 攻击方为蝙蝠玩家 + 75-100 阶段 → 造成伤害 ×1.15
	 */
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$batDesmodusDamageScaling(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient()) return;
		DamageSource source = args.get(0);
		float amount = args.get(1);
		Entity attacker = source.getAttacker();

		// 0-25 阶段：受害方为蝙蝠玩家 → -15%
		if (self instanceof ServerPlayerEntity sp
				&& FormUtils.isForm(sp, FormIdentifiers.BAT_DESMODUS)
				&& !source.isOf(DamageTypes.OUT_OF_WORLD)
				&& !source.isOf(DamageTypes.GENERIC_KILL)
				&& !source.isOf(DamageTypes.INDIRECT_MAGIC)) {
			if (BatDesmodusBloodThirst.getStage(sp) == 0) {
				amount *= 0.85f;
				args.set(1, amount);
			}
		}

		// 75-100 阶段：攻击方为蝙蝠玩家 → +15%（血雾光环等被动伤害不受加成）
		if (attacker instanceof ServerPlayerEntity ap
				&& attacker != self
				&& !BatDesmodusBloodThirst.SUPPRESS_OUTGOING_BUFF.get()
				&& FormUtils.isForm(ap, FormIdentifiers.BAT_DESMODUS)) {
			if (BatDesmodusBloodThirst.getStage(ap) == 3) {
				args.set(1, amount * 1.15f);
			}
		}
	}
}
