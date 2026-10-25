package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.compat.WindSpiritHungerState;
import net.jackcooper.shapeShifterCurseAddon.compat.WindSpiritPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.NovaPerks;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class WindSpiritHungerMixin implements WindSpiritHungerState {
    @Shadow private int foodLevel;
    @Unique private PlayerEntity ssca$owner;
    /** Hundredths prepaid by rounding a previous direct food bill up. Survives reload/rank changes. */
    @Unique private int ssca$foodCredit;

    @Override public void ssca$setOwner(PlayerEntity player) { ssca$owner = player; }

    @Inject(method = "update", at = @At("HEAD"))
    private void ssca$bindOwner(PlayerEntity player, CallbackInfo ci) { ssca$owner = player; }

    @Unique private int ssca$discount() {
        if (ssca$owner == null || ssca$owner.getWorld().isClient) return 0;
        int windDiscount = WindSpiritPerks.hungerDiscount(ssca$owner);
        return windDiscount != 0 ? windDiscount : NovaPerks.hungerDiscount(ssca$owner);
    }

    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float ssca$reduceExhaustion(float amount) {
        return amount > 0 ? amount * (100 - ssca$discount()) / 100.0f : amount;
    }

    @Unique private int ssca$payFood(int cost) {
        int discount = ssca$discount();
        if (cost <= 0 || discount == 0) return cost;
        long bill = (long) cost * (100 - discount) - ssca$foodCredit;
        int paid = bill <= 0 ? 0 : (int) ((bill + 99) / 100);
        ssca$foodCredit = (int) ((long) paid * 100 - bill);
        return paid;
    }

    @ModifyVariable(method = "setFoodLevel", at = @At("HEAD"), argsOnly = true)
    private int ssca$reduceDirectFoodCost(int requestedLevel) {
        return requestedLevel < foodLevel ? foodLevel - ssca$payFood(foodLevel - requestedLevel) : requestedLevel;
    }

    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ssca$reduceNegativeFood(int food) { return food < 0 ? -ssca$payFood(-food) : food; }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void ssca$saveCredit(NbtCompound nbt, CallbackInfo ci) { nbt.putInt("sscaWindFoodCredit", ssca$foodCredit); }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void ssca$loadCredit(NbtCompound nbt, CallbackInfo ci) {
        ssca$foodCredit = Math.max(0, Math.min(99, nbt.getInt("sscaWindFoodCredit")));
    }
}
