package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerWorld.class)
public abstract class SoundRangeWorldMixin {
    @ModifyVariable(method = {
            "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V",
            "playSoundFromEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V"
    }, at = @At("HEAD"), argsOnly = true)
    private RegistryEntry<SoundEvent> ssca$extendSound(RegistryEntry<SoundEvent> event,
                                                     @Local(argsOnly = true, ordinal = 0) float volume) {
        return AddonSoundRange.extendPlayback(event, volume);
    }
}
