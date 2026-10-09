package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeRules;
import net.minecraft.server.command.PlaySoundCommand;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlaySoundCommand.class)
public abstract class SoundRangeCommandMixin {
    @ModifyExpressionValue(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/sound/SoundEvent;of(Lnet/minecraft/util/Identifier;)Lnet/minecraft/sound/SoundEvent;"))
    private static SoundEvent ssca$extendSkillCommand(SoundEvent event,
                                                     @Local(argsOnly = true, ordinal = 0) float volume) {
        return SoundRangeRules.isAddonPlayback() ? AddonSoundRange.extend(event, volume) : event;
    }
}
