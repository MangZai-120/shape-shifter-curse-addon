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
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.EffectEfficiencyReductionPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SscAddonLivingEntityMixin {

	/**
	 * 中立生物被玩家攻击时，记录全局挑衅状态。
	 * 裁决者: 攻击任何亡灵触发挑衅
	 * 金沙岚: 攻击尸壳或咒文胡狼触发挑衅
	 */
	@Inject(method = "damage", at = @At("HEAD"))
	private void ssc_addon$onUndeadDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
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
}
