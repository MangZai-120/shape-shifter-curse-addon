package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.sound.ExtendedRangeSound;
import net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundSystem.class)
public abstract class SoundRangeSystemMixin {
    @ModifyExpressionValue(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sound/Sound;getAttenuation()I"))
    private int ssca$extendAttenuation(int distance, @Local(argsOnly = true) SoundInstance sound) {
        boolean extended = sound instanceof ExtendedRangeSound ranged && ranged.ssca$hasExtendedRange();
        return extended || SoundRangeRules.isAddonPlayback() ? distance * SoundRangeRules.MULTIPLIER : distance;
    }
}
