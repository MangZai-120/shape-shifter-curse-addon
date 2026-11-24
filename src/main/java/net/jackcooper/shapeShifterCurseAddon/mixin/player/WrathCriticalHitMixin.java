package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.WrathMark;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
abstract class WrathCriticalHitMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z"))
    private boolean ssca$wrathPreventsCritical(PlayerEntity player, StatusEffect effect, Operation<Boolean> original) {
        return original.call(player, effect) || effect == StatusEffects.BLINDNESS
                && (player.hasStatusEffect(SscAddon.SAND_BLIND) || WrathMark.isActive(player));
    }
}

