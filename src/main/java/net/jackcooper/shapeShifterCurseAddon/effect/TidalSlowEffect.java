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
 * 潮汐波动吸附减速效果 - 移动速度 -15%（代码减速，非药水缓慢）。
 * 每次被潮汐粒子球吸附施加 1.5 秒（30 tick），重复施加刷新时间。
 * 仅作用于移动速度，不显示药水图标（ambient=false, particles=false 由调用方控制）。
 */
public class TidalSlowEffect extends StatusEffect {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("7c1a2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final String SPEED_MODIFIER_NAME = "Tidal Slow";

    public TidalSlowEffect() {
        super(StatusEffectCategory.HARMFUL, 0x33CCFF); // 青蓝色
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            // 减速幅度随 amplifier 提升：amplifier 0 = -15%、每级额外 -10%（amplifier 2 = -35%，海晶荧光坠爆炸球用）
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    SPEED_MODIFIER_UUID,
                    SPEED_MODIFIER_NAME,
                    -(0.15 + amplifier * 0.10),
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // 偶发水滴粒子提示（与脚底青蓝粒子互补）
        if (entity.getWorld() instanceof ServerWorld sw) {
            net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(sw,
                    ParticleTypes.DRIPPING_WATER,
                    entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
                    1, entity.getWidth() / 2.0, entity.getHeight() / 4.0, entity.getWidth() / 2.0, 0.0);
        }
    }
}