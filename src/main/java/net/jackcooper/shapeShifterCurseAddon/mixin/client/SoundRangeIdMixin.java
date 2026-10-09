package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Resolve the wire marker to the original resource/stop ID before creating a sound instance. */
@Mixin(AbstractSoundInstance.class)
public abstract class SoundRangeIdMixin {
    @ModifyExpressionValue(method = "<init>(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;Lnet/minecraft/util/math/random/Random;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/sound/SoundEvent;getId()Lnet/minecraft/util/Identifier;"))
    private static Identifier ssca$originalSoundId(Identifier id) {
        return AddonSoundRange.originalId(id);
    }
}
