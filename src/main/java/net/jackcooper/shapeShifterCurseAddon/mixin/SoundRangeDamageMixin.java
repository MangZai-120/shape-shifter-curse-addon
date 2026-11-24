package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A delayed skill hit can delegate its only sound to the victim's vanilla hurt or death handler. */
@Mixin(LivingEntity.class)
public abstract class SoundRangeDamageMixin {
    @WrapOperation(method = "damage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;playHurtSound(Lnet/minecraft/entity/damage/DamageSource;)V"))
    private void ssca$hurtSoundSource(LivingEntity victim, DamageSource damage, Operation<Void> original) {
        SoundRangeRules.withInheritedSource(() -> original.call(victim, damage));
    }

    @WrapOperation(method = "damage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;playSound(Lnet/minecraft/sound/SoundEvent;FF)V"))
    private void ssca$deathSoundSource(LivingEntity victim, SoundEvent sound, float volume, float pitch,
                                      Operation<Void> original) {
        SoundRangeRules.withInheritedSource(() -> original.call(victim, sound, volume, pitch));
    }
}
