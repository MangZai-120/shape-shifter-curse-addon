package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.ParasiticAbsorptionAccess;
import net.jackcooper.shapeShifterCurseAddon.ability.ParasiticAbsorptionState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ParasiticAbsorptionMixin implements ParasiticAbsorptionAccess {
    @Unique private ParasiticAbsorptionState ssca$parasiticAbsorption;

    @Override public ParasiticAbsorptionState ssca$parasiticAbsorption() {
        if (ssca$parasiticAbsorption == null) ssca$parasiticAbsorption = new ParasiticAbsorptionState();
        return ssca$parasiticAbsorption;
    }

    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"))
    private void ssca$consume(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity) && ssca$parasiticAbsorption != null)
            ssca$parasiticAbsorption.onChanged(self, self.getAbsorptionAmount(), amount);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void ssca$save(NbtCompound nbt, CallbackInfo ci) { ssca$parasiticAbsorption().write(nbt); }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ssca$load(NbtCompound nbt, CallbackInfo ci) {
        ssca$parasiticAbsorption().read(nbt, (LivingEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ssca$expire(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && ssca$parasiticAbsorption != null && ssca$parasiticAbsorption.remaining() > 0
                && (!self.hasStatusEffect(SscAddon.BAT_ABSORPTION)
                || ssca$parasiticAbsorption.expired(self.getWorld().getTime()))) {
            ssca$parasiticAbsorption.remove(self);
            self.removeStatusEffect(SscAddon.BAT_ABSORPTION);
        }
    }
}
