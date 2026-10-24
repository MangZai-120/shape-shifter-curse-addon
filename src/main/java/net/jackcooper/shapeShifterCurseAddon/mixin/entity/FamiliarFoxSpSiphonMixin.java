package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.compat.FamiliarFoxSpPerks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both vanilla implementations record only damage that remains after absorption. */
@Mixin({LivingEntity.class, PlayerEntity.class})
public abstract class FamiliarFoxSpSiphonMixin {
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/damage/DamageTracker;onDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
    private void ssca$restoreManaOnHealthDamage(DamageTracker tracker, DamageSource source, float damage,
                                               Operation<Void> original) {
        original.call(tracker, source, damage);
        FamiliarFoxSpPerks.onHealthDamage((LivingEntity) (Object) this, source, damage);
    }
}
