package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenVanillaAnimations;
import net.minecraft.entity.passive.IronGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IronGolemEntity.class)
public abstract class FrozenGolemAnimationMixin {
    @ModifyReturnValue(method = "getAttackTicksLeft",at = @At("RETURN"))
    private int ssca$resumeAttack(int ticks) { return FrozenVanillaAnimations.countdown((IronGolemEntity)(Object)this,"attack",ticks); }
    @ModifyReturnValue(method = "getLookingAtVillagerTicks",at = @At("RETURN"))
    private int ssca$resumeFlower(int ticks) { return FrozenVanillaAnimations.countdown((IronGolemEntity)(Object)this,"flower",ticks); }
}
