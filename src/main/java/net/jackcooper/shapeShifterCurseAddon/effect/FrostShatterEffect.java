package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * 霜碎效果（寒霜系法术·冰霜新星附加 debuff，jackcooper）：护甲 -50%。
 *
 * <p>实现走原版 {@code GENERIC_ARMOR} 修饰符（onApplied/onRemoved 对称加减，
 * 到期/净化/喝奶自动还原，无泄漏、无残留）。时长由冰霜新星施法时决定（与缓速同步，
 * L1=3s → L5=5s）。破甲是本 mod 首个护甲类属性 debuff，与缓速分离为独立效果，
 * 便于法术免疫表与提示文本独立控制。</p>
 */
public class FrostShatterEffect extends StatusEffect {

	/** 护甲修饰符 UUID（固定，防重复叠加）。 */
	private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("a3b7c8d9-e0f1-4a2b-8c3d-4e5f60718293");
	private static final String ARMOR_MODIFIER_NAME = "SSCA Frost Shatter Armor Break";

	/** 护甲减半。 */
	private static final double ARMOR_REDUCTION = -0.5;

	public FrostShatterEffect() {
		super(StatusEffectCategory.HARMFUL, 0x9FD8EF); // 浅冰蓝（比霜冻略淡）
	}

	@Override
	public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
		super.onApplied(entity, attributes, amplifier);
		EntityAttributeInstance armorAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armorAttr != null) {
			// 先移除旧实例再添加（刷新施放不叠加，恒 -50%）
			armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
			armorAttr.addTemporaryModifier(new EntityAttributeModifier(
					ARMOR_MODIFIER_UUID,
					ARMOR_MODIFIER_NAME,
					ARMOR_REDUCTION,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL
			));
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
		super.onRemoved(entity, attributes, amplifier);
		EntityAttributeInstance armorAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armorAttr != null) {
			armorAttr.removeModifier(ARMOR_MODIFIER_UUID);
		}
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 10 == 0;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		// 破甲期持续飘细碎冰晶（提示目标护甲已被击碎）
		if (entity.getWorld() instanceof ServerWorld serverWorld) {
			net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(serverWorld,
					ParticleTypes.SNOWFLAKE,
					entity.getX(), entity.getBodyY(0.6), entity.getZ(),
					2, entity.getWidth() / 3.0, 0.15, entity.getWidth() / 3.0, 0.01);
		}
	}
}
