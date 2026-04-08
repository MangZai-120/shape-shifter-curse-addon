package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * 致盲效果 - 金沙岚SP的凋零金沙技能施加的debuff
 * 效果：
 * - 视野限制（通过同时施加原版失明效果实现）
 * - 移动速度 -20%
 * - 非玩家生物的警戒和攻击距离减少至5格（通过修改FOLLOW_RANGE属性实现）
 * - 头部周围生成沙尘和黑色粒子
 * 持续时间：3秒
 */
public class SandBlindEffect extends StatusEffect {

	private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a7b8c9d0-e1f2-4a3b-8c5d-6e7f89012345");
	private static final String SPEED_MODIFIER_NAME = "Sand Blind Speed Debuff";

	private static final UUID FOLLOW_RANGE_MODIFIER_UUID = UUID.fromString("a7b8c9d0-e1f2-4a3b-8c5d-6e7f89012346");
	private static final String FOLLOW_RANGE_MODIFIER_NAME = "Sand Blind Follow Range Reduction";

	public SandBlindEffect() {
		super(StatusEffectCategory.HARMFUL, 0xD4A017); // 金沙色
	}

	@Override
	public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
		super.onApplied(entity, attributes, amplifier);

		// 施加原版失明效果（视野限制）
		StatusEffectInstance currentSandBlind = entity.getStatusEffect(this);
		int blindDuration = currentSandBlind != null ? currentSandBlind.getDuration() : 60;
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blindDuration, 0, false, false, false));

		// 移动速度 -20%
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(SPEED_MODIFIER_UUID);
			speedAttr.addTemporaryModifier(new EntityAttributeModifier(
					SPEED_MODIFIER_UUID,
					SPEED_MODIFIER_NAME,
					-0.20,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL
			));
		}

		// 非玩家生物：将警戒/攻击距离减少至5格
		if (entity instanceof MobEntity mob) {
			EntityAttributeInstance followRange = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
			if (followRange != null) {
				followRange.removeModifier(FOLLOW_RANGE_MODIFIER_UUID);
				// 计算需要减少的量，使最终值为5
				double currentBase = followRange.getValue();
				double reduction = -(currentBase - 5.0);
				if (reduction < 0) {
					followRange.addTemporaryModifier(new EntityAttributeModifier(
							FOLLOW_RANGE_MODIFIER_UUID,
							FOLLOW_RANGE_MODIFIER_NAME,
							reduction,
							EntityAttributeModifier.Operation.ADDITION
					));
				}
			}
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
		super.onRemoved(entity, attributes, amplifier);

		// 移除原版失明效果
		entity.removeStatusEffect(StatusEffects.BLINDNESS);

		// 移除速度修正
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(SPEED_MODIFIER_UUID);
		}

		// 移除跟踪距离修正
		if (entity instanceof MobEntity mob) {
			EntityAttributeInstance followRange = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
			if (followRange != null) {
				followRange.removeModifier(FOLLOW_RANGE_MODIFIER_UUID);
			}
		}
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		// 每4tick生成一次头部粒子
		return duration % 4 == 0;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (!(entity.getWorld() instanceof ServerWorld serverWorld)) return;

		double headX = entity.getX();
		double headY = entity.getEyeY() + 0.3;
		double headZ = entity.getZ();

		// 沙尘落粒子 - 广播给所有附近玩家（含受影响实体本身）
		serverWorld.spawnParticles(
				new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SAND.getDefaultState()),
				headX, headY, headZ,
				5, 0.45, 0.25, 0.45, 0);

		// 黑色烟雾粒子 - 广播给所有附近玩家
		serverWorld.spawnParticles(
				ParticleTypes.SMOKE,
				headX, headY, headZ,
				3, 0.4, 0.2, 0.4, 0.008);
	}
}
