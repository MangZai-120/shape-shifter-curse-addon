package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class PlayingDeadEffect extends StatusEffect {
    public PlayingDeadEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x586e7c);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity == null || entity.isDead()){return;}
        // Use SWIMMING pose which forces the model to lie flat (crawl animation) on client side
        entity.setPose(EntityPose.SWIMMING);
        entity.setSwimming(true);
        entity.setVelocity(0, entity.getVelocity().y, 0);
        entity.velocityModified = true;
    }
}
