package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlaySoundS2CPacket.class)
public abstract class SoundRangePacketMixin {
    @Shadow @Final @Mutable private RegistryEntry<SoundEvent> sound;

    @Inject(method = "<init>(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;DDDFFJ)V", at = @At("RETURN"))
    private void ssca$extendDirectSound(RegistryEntry<SoundEvent> event, SoundCategory category,
                                       double x, double y, double z, float volume, float pitch, long seed,
                                       CallbackInfo ci) {
        sound = AddonSoundRange.extendPlayback(sound, volume);
    }
}
