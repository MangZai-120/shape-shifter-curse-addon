package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.effect.SpellPoison;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(StatusEffectInstance.class)
abstract class SpellPoisonInstanceMixin implements SpellPoison {
    @Unique private UUID ssca$poisonCaster;

    @Override public UUID ssca$getPoisonCaster() { return ssca$poisonCaster; }
    @Override public void ssca$setPoisonCaster(UUID caster) { ssca$poisonCaster = caster; }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void ssca$copyPoisonCaster(StatusEffectInstance other, CallbackInfo ci) {
        ssca$poisonCaster = ((SpellPoison) other).ssca$getPoisonCaster();
    }

    // Both stronger replacements and same-level extensions write duration, after the old layer is saved.
    @Inject(method = "upgrade", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/entity/effect/StatusEffectInstance;duration:I"))
    private void ssca$replacePoisonCaster(StatusEffectInstance other, CallbackInfoReturnable<Boolean> cir) {
        ssca$poisonCaster = ((SpellPoison) other).ssca$getPoisonCaster();
    }

    @Inject(method = "writeTypelessNbt", at = @At("TAIL"))
    private void ssca$writePoisonCaster(NbtCompound nbt, CallbackInfo ci) {
        if (ssca$poisonCaster != null) nbt.putUuid("SscaSpellPoisonCaster", ssca$poisonCaster);
    }

    @Inject(method = "fromNbt(Lnet/minecraft/entity/effect/StatusEffect;Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/entity/effect/StatusEffectInstance;", at = @At("RETURN"))
    private static void ssca$readPoisonCaster(StatusEffect type, NbtCompound nbt, CallbackInfoReturnable<StatusEffectInstance> cir) {
        if (type == StatusEffects.POISON && nbt.containsUuid("SscaSpellPoisonCaster")) {
            ((SpellPoison) cir.getReturnValue()).ssca$setPoisonCaster(nbt.getUuid("SscaSpellPoisonCaster"));
        }
    }
}
