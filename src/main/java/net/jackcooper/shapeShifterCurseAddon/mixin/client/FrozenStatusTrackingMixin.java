package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FrozenStatusTrackingMixin {
    @Shadow @Final private static TrackedData<Float> HEALTH;

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void ssca$checkFreezeOnHealthChange(TrackedData<?> tracked, CallbackInfo ci) {
        if (tracked != HEALTH) return;
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.getWorld().isClient && !entity.isAlive()) FrozenAnimationDrivers.observeFreeze(entity);
    }

    @Inject(method = "onStatusEffectApplied", at = @At("TAIL"))
    private void ssca$trackFreeze(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if (effect.getEffectType() == SscAddon.FROZEN) FrozenAnimationDrivers.observeFreeze((LivingEntity)(Object)this);
    }

    @Inject(method = "onStatusEffectUpgraded", at = @At("TAIL"))
    private void ssca$trackRefreshedFreeze(StatusEffectInstance effect, boolean reapply, Entity source, CallbackInfo ci) {
        if (effect.getEffectType() == SscAddon.FROZEN) FrozenAnimationDrivers.observeFreeze((LivingEntity)(Object)this);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ssca$trackRestoredFreeze(NbtCompound data, CallbackInfo ci) {
        FrozenAnimationDrivers.observeFreeze((LivingEntity)(Object)this);
    }
}
