package net.jackcooper.shapeShifterCurseAddon.mixin;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import net.jackcooper.shapeShifterCurseAddon.client.hud.ChargeCooldownHud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.onixary.shapeShifterCurseFabric.additional_power.ChargePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;

/** Extend the already-sent power snapshot without changing any charge/cooldown gameplay fields. */
@Mixin(value = ChargePower.class, remap = false)
public abstract class ChargePowerHudMixin extends Power implements ChargeCooldownHud {
    @Shadow public int nowCooldown;
    @Shadow public ArrayList<ChargePower.ChargeTier> ChargeTierList;
    @Unique private long ssca$hudCooldownEnd;
    @Unique private int ssca$hudCooldownTotal;

    protected ChargePowerHudMixin(PowerType<?> type, LivingEntity entity) { super(type, entity); }

    @Inject(method = "toTag", at = @At("RETURN"))
    private void ssca$writeHudCooldown(CallbackInfoReturnable<NbtElement> cir) {
        if (entity.getWorld().isClient || !(cir.getReturnValue() instanceof NbtCompound tag)) return;
        int total = 0;
        for (var tier : ChargeTierList) total = Math.max(total, tier.cooldown);
        var snapshot = new NbtCompound();
        snapshot.putInt("remaining", Math.max(0, nowCooldown));
        snapshot.putInt("total", Math.max(nowCooldown, total));
        tag.put("ssca_hud_cd", snapshot);
    }

    @Inject(method = "fromTag", at = @At("TAIL"))
    private void ssca$readHudCooldown(NbtElement data, CallbackInfo ci) {
        if (!entity.getWorld().isClient || !(data instanceof NbtCompound tag)) return;
        var snapshot = tag.getCompound("ssca_hud_cd");
        int remaining = Math.max(0, snapshot.getInt("remaining"));
        ssca$hudCooldownTotal = Math.max(remaining, snapshot.getInt("total"));
        ssca$hudCooldownEnd = entity.getWorld().getTime() + remaining;
    }

    @Override public int ssca$remainingCooldown() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, ssca$hudCooldownEnd - entity.getWorld().getTime()));
    }
    @Override public int ssca$totalCooldown() { return ssca$hudCooldownTotal; }
}
