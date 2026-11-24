package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class SoundRangeEntityMixin {
    @ModifyArg(method = "playSound(Lnet/minecraft/sound/SoundEvent;FF)V", index = 4,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private SoundEvent ssca$inheritedSound(SoundEvent sound, @Local(argsOnly = true, ordinal = 0) float volume) {
        return SoundRangeRules.isInheritedEntityPlayback(getClass()) ? AddonSoundRange.extend(sound, volume) : sound;
    }
}
