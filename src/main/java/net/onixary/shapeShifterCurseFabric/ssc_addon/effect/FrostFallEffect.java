package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

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
 * 霜降效果 - SP雪狐远程技能造成的debuff
 * 效果：
 * - 移动速度 -30%
 * - 持续时间：4秒
 */
public class FrostFallEffect extends StatusEffect {
    
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("f2a3b4c5-d6e7-4890-bcde-f01234567891");
    private static final String SPEED_MODIFIER_NAME = "Frost Fall Speed Debuff";
    
    public FrostFallEffect() {
        super(StatusEffectCategory.HARMFUL, 0xADD8E6); // Light blue color
    }
    
    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);
        
        // Apply movement speed reduction (-30%)
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MODIFIER_UUID,
                SPEED_MODIFIER_NAME,
                -0.3,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }
    
    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);
        
        // Remove movement speed modifier
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
        }
    }
    
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // Apply update effect every 5 ticks for particles
        return duration % 5 == 0;
    }
    
    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Spawn snowflake particles
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                entity.getX(),
                entity.getY() + entity.getHeight() / 2.0,
                entity.getZ(),
                2,
                entity.getWidth() / 2.0,
                entity.getHeight() / 4.0,
                entity.getWidth() / 2.0,
                0.02
            );
        }
    }
}
