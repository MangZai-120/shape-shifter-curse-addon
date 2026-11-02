package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.CollapsePenalties;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keep vanilla body turning active when collapse slows a walking player below 0.05 blocks/tick. */
@Mixin(LivingEntity.class)
public abstract class CollapseBodyYawMixin {
    @ModifyConstant(method="tick",constant=@Constant(floatValue=0.0025000002F),require=1)
    private float ssca$collapseBodyYawThreshold(float threshold) {
        return CollapsePenalties.bodyYawMovementThreshold((LivingEntity)(Object)this,threshold);
    }
}
