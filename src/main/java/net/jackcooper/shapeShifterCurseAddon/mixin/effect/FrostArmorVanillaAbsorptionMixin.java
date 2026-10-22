package net.jackcooper.shapeShifterCurseAddon.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.AbsorptionStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbsorptionStatusEffect.class)
public abstract class FrostArmorVanillaAbsorptionMixin {
    @WrapOperation(method = "onRemoved", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;setAbsorptionAmount(F)V"))
    private void ssca$preserveFrostArmor(LivingEntity entity, float amount, Operation<Void> original) {
        if (entity instanceof FrostArmorAccess access) {
            access.ssca$frostArmor().removeOther(amount, value -> original.call(entity, value));
        } else original.call(entity, amount);
    }
}
