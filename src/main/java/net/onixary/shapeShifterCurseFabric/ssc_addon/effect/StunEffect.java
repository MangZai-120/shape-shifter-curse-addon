package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

import java.util.UUID;

public class StunEffect extends StatusEffect {
	// 固定 UUID：暴露为常量，供 SscAddon 的「孤儿修正兜底清理」按 UUID 精确移除残留。
	public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("7107DE5E-7CE8-4030-940E-514C1F160890");
	public static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("22653B89-116E-49DC-9B6B-9971489B5C0A");

	public StunEffect() {
		super(StatusEffectCategory.HARMFUL, 0x888888);
		this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
				SPEED_MODIFIER_UUID.toString(), -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
		this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
				ATTACK_MODIFIER_UUID.toString(), -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		// Removed explicit velocity reset to allow gravity/knockback to work
		// entity.setVelocity(0, entity.getVelocity().y, 0);
		// entity.velocityModified = true;
	}
}
