package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorAccess;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class FrostArmorAbsorptionMixin implements FrostArmorAccess {
    @Unique private FrostArmorState ssca$frostArmorState;

    @Override
    public FrostArmorState ssca$frostArmor() {
        if (ssca$frostArmorState == null) ssca$frostArmorState = new FrostArmorState();
        return ssca$frostArmorState;
    }

    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"))
    private void ssca$consumeFrostArmor(float amount, CallbackInfo ci) {
        if (ssca$frostArmorState != null) ssca$frostArmorState.onAbsorptionChanged(
                ((PlayerEntity) (Object) this).getAbsorptionAmount(), Math.max(0, amount));
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void ssca$saveFrostArmor(NbtCompound nbt, CallbackInfo ci) {
        if (ssca$frostArmorState != null) ssca$frostArmorState.write(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ssca$loadFrostArmor(NbtCompound nbt, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        ssca$frostArmor().read(nbt, self, self.hasStatusEffect(SscAddon.FROST_ARMOR));
    }
}
