package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
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
        if (targetEntity != null) {
            // Check for SP familiar fox visibility
            PowerType<?> powerTypeSp = PowerTypeRegistry.get(new Identifier("my_addon", "form_familiar_fox_sp_visibility"));
            if (powerTypeSp != null && PowerHolderComponent.KEY.get(targetEntity).hasPower(powerTypeSp)) {
                return d * 0.67D;
            }
            // Check for RED familiar fox visibility
            PowerType<?> powerTypeRed = PowerTypeRegistry.get(new Identifier("my_addon", "form_familiar_fox_red_visibility"));
            if (powerTypeRed != null && PowerHolderComponent.KEY.get(targetEntity).hasPower(powerTypeRed)) {
                return d * 0.67D;
            }
        }
        return d;
    }
}
