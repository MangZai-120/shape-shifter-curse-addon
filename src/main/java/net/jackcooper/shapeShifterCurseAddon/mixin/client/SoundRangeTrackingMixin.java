package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.sound.ExtendedRangeSound;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackingSoundInstance.class)
public abstract class SoundRangeTrackingMixin implements ExtendedRangeSound {
    @Unique private boolean ssca$extendedRange;
    @Override public boolean ssca$hasExtendedRange() { return ssca$extendedRange; }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ssca$readTrackingRange(SoundEvent event, SoundCategory category, float volume, float pitch,
                                       Entity entity, long seed, CallbackInfo ci) {
        ssca$extendedRange = AddonSoundRange.isExtended(event, volume);
    }
}
