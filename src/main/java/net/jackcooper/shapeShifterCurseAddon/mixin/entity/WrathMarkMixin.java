package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.spell.WrathMark;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class WrathMarkMixin implements WrathMark.Access {
    @Unique private WrathMark ssca$wrathMark;
    @Override public WrathMark ssca$getWrathMark() { return ssca$wrathMark; }
    @Override public void ssca$setWrathMark(WrathMark mark) { ssca$wrathMark=mark; }
    @Inject(method="damage",at=@At("HEAD"),cancellable=true)
    private void ssca$wrathTarget(net.minecraft.entity.damage.DamageSource source,float amount,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if(source.getAttacker() instanceof net.minecraft.entity.mob.MobEntity mob
                && WrathMark.blocksMobHit(mob,(LivingEntity)(Object)this)) {
            cir.setReturnValue(false);
            return;
        }
        if(amount>0 && source.getAttacker() instanceof LivingEntity attacker && attacker!=(Object)this)
            WrathMark.onHit(attacker,(LivingEntity)(Object)this);
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void ssca$tickWrath(CallbackInfo ci) {
        if(ssca$wrathMark!=null) ssca$wrathMark.tick((LivingEntity)(Object)this);
    }
    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void ssca$writeWrath(NbtCompound nbt,CallbackInfo ci) {
        if(ssca$wrathMark!=null) nbt.put("SscaWrathMark",ssca$wrathMark.write());
    }
    @Inject(method="readCustomDataFromNbt",at=@At("TAIL"))
    private void ssca$readWrath(NbtCompound nbt,CallbackInfo ci) {
        ssca$wrathMark=WrathMark.read(nbt.getCompound("SscaWrathMark"), (LivingEntity)(Object)this);
    }
}
