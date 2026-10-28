package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import net.jackcooper.shapeShifterCurseAddon.ability.PlayerGravityLease;
import net.jackcooper.shapeShifterCurseAddon.ability.PlayDeadAbsorptionManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerSkillStateSaveMixin {
    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void ssc_addon$excludeTemporaryGravity(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            PlayerGravityLease.writePersistentGravity(player, cir.getReturnValue());
            PlayDeadAbsorptionManager.writePlayerData(player, cir.getReturnValue());
        }
    }

    @Inject(method = "readNbt", at = @At("RETURN"))
    private void ssc_addon$restoreOwnedAbsorption(NbtCompound nbt, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) PlayDeadAbsorptionManager.readPlayerData(player, nbt);
    }
}
