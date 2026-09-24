package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.VariableIntPower;
import net.jackcooper.shapeShifterCurseAddon.client.CountdownClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VariableIntPower.class, remap = false)
public abstract class CountdownResourceMixin extends Power {
    protected CountdownResourceMixin(PowerType<?> type, LivingEntity entity) { super(type, entity); }

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$countdownValue(CallbackInfoReturnable<Integer> cir) {
        Integer value = CountdownClient.predicted(entity, (VariableIntPower) (Object) this);
        if (value != null) cir.setReturnValue(value);
    }

    @Inject(method = "fromTag", at = @At("TAIL"), remap = false)
    private void ssca$countdownNativeSync(NbtElement tag, CallbackInfo ci) {
        if (tag instanceof NbtInt value)
            CountdownClient.nativeSync(entity, (VariableIntPower) (Object) this, value.intValue());
    }
}
