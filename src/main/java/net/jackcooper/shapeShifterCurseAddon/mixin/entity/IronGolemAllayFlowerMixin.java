package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.ability.IronGolemOfferFlowerToAllayGoal;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolemEntity.class)
public abstract class IronGolemAllayFlowerMixin extends MobEntity {
	protected IronGolemAllayFlowerMixin(EntityType<? extends MobEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void ssc_addon$addAllayFlowerGoal(CallbackInfo ci) {
		this.goalSelector.add(6, new IronGolemOfferFlowerToAllayGoal((IronGolemEntity) (Object) this));
		// 契灵：铁傀儡主动攻击契灵玩家
		this.targetSelector.add(2, new ActiveTargetGoal<>(
				(IronGolemEntity) (Object) this,
				PlayerEntity.class,
				10,
				true,
				false,
				p -> p instanceof PlayerEntity pe && FormUtils.isForm(pe, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
		));
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$convertAllayAttackToHealing(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!(source.getAttacker() instanceof PlayerEntity player) || !FormUtils.isAllaySP(player)) return;

		IronGolemEntity golem = (IronGolemEntity) (Object) this;
		if (amount > 0.0F && golem.getHealth() < golem.getMaxHealth()) {
			golem.heal(amount);
		}
		if (golem.getTarget() == player) {
			golem.setTarget(null);
		}
		if (player.getUuid().equals(golem.getAngryAt())) {
			golem.setAngryAt(null);
			golem.setAngerTime(0);
		}
		cir.setReturnValue(false);
	}
}
