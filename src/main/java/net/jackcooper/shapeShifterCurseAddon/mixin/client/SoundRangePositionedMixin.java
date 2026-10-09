package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.sound.ExtendedRangeSound;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PositionedSoundInstance.class)
public abstract class SoundRangePositionedMixin implements ExtendedRangeSound {
    @Unique private boolean ssca$extendedRange;
    @Override public boolean ssca$hasExtendedRange() { return ssca$extendedRange; }

    @ModifyExpressionValue(method = "<init>(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFLnet/minecraft/util/math/random/Random;ZILnet/minecraft/client/sound/SoundInstance$AttenuationType;DDD)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/sound/SoundEvent;getId()Lnet/minecraft/util/Identifier;"))
    private static Identifier ssca$originalSoundId(Identifier id) {
        return AddonSoundRange.originalId(id);
    }

    @Inject(method = "<init>(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFLnet/minecraft/util/math/random/Random;ZILnet/minecraft/client/sound/SoundInstance$AttenuationType;DDD)V", at = @At("RETURN"))
    private void ssca$readSoundRange(SoundEvent event, SoundCategory category, float volume, float pitch,
                                    Random random, boolean repeat, int delay, SoundInstance.AttenuationType attenuation,
                                    double x, double y, double z, CallbackInfo ci) {
        ssca$extendedRange = AddonSoundRange.isExtended(event, volume);
    }
}
