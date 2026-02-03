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
 * 霜凝效果 - SP雪狐近战技能造成的debuff
 * 效果：
 * - 移动速度 -40%
 * - 攻击速度 -40%
 * - 受到物理/魔法伤害 +20% (通过Mixin实现)
 * - 持续时间：3秒
 */
public class FrostFreezeEffect extends StatusEffect {
    
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("f1a2b3c4-d5e6-4789-abcd-ef0123456789");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("f1a2b3c4-d5e6-4789-abcd-ef0123456790");
    
    private static final String SPEED_MODIFIER_NAME = "Frost Freeze Speed Debuff";
    private static final String ATTACK_SPEED_MODIFIER_NAME = "Frost Freeze Attack Speed Debuff";
    
    public FrostFreezeEffect() {
        super(StatusEffectCategory.HARMFUL, 0x7DD3FC); // Light ice blue color
    }
    
    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);
        
        // Apply movement speed reduction (-40%)
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MODIFIER_UUID,
                SPEED_MODIFIER_NAME,
                -0.4,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
        
        // Apply attack speed reduction (-40%)
        EntityAttributeInstance attackSpeedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
            attackSpeedAttr.addTemporaryModifier(new EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_UUID,
                ATTACK_SPEED_MODIFIER_NAME,
                -0.4,
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
        
        // Remove attack speed modifier
        EntityAttributeInstance attackSpeedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.removeModifier(ATTACK_SPEED_MODIFIER_UUID);
        }
    }
    
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // Apply update effect every 4 ticks for particles
        return duration % 4 == 0;
    }
    
    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Spawn frost particles
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.SNOWFLAKE,
                entity.getX(),
                entity.getY() + entity.getHeight() / 2.0,
                entity.getZ(),
                3,
                entity.getWidth() / 2.0,
                entity.getHeight() / 4.0,
                entity.getWidth() / 2.0,
                0.01
            );
        }
    }
    
    /**
     * 检查伤害类型是否为物理或魔法伤害
     * 用于判断是否应用+20%伤害增幅
     */
    public static boolean isPhysicalOrMagicDamage(net.minecraft.entity.damage.DamageSource source) {
        // 物理伤害类型
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.MOB_ATTACK) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.MOB_ATTACK_NO_AGGRO) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.ARROW) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.TRIDENT) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.THROWN)) {
            return true;
        }
        
        // 魔法伤害类型
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.MAGIC) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.INDIRECT_MAGIC) ||
            source.isOf(net.minecraft.entity.damage.DamageTypes.SONIC_BOOM)) {
            return true;
        }
        
        // 通用伤害(通常用于mod自定义伤害)
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC)) {
            return true;
        }
        
        return false;
    }
}
