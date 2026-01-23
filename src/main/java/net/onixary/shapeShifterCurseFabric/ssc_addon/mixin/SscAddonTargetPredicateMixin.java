package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TargetPredicate.class)
public class SscAddonTargetPredicateMixin {

    @ModifyVariable(method = "test", at = @At("STORE"), ordinal = 0)
    private double modifyMaxDistance(double d, @Nullable LivingEntity baseEntity, LivingEntity targetEntity) {
        if (targetEntity != null && PowerHolderComponent.hasPower(targetEntity, new Identifier("my_addon", "form_familiar_fox_sp_visibility"))) {
            return d * 0.67D;
        }
        return d;
    }
}
