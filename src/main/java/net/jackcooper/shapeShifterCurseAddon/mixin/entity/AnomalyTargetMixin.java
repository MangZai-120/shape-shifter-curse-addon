package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyTargetAccess;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyTargetState;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AnomalyTargetMixin implements AnomalyTargetAccess, SpellAnomalyHits.SummonedData {
    @Unique private AnomalyTargetState ssca$anomalyState;
    @Unique private SpellAnomalyContext ssca$summonAnomaly;
    @Unique private long ssca$anomalyEpoch;
    @Unique private long ssca$anomalyLastSent = Long.MIN_VALUE;
    @Unique private int ssca$pendingAnomalyHits;

    @Override public AnomalyTargetState ssca$anomalyState() { return ssca$anomalyState; }
    @Override public void ssca$anomalyState(AnomalyTargetState state) { ssca$anomalyState = state; }
    @Override public long ssca$anomalyEpoch() { return ssca$anomalyEpoch; }
    @Override public void ssca$invalidateAnomalyEpoch() { ssca$anomalyEpoch++; }
    @Override public long ssca$anomalyLastSent() { return ssca$anomalyLastSent; }
    @Override public void ssca$anomalyLastSent(long tick) { ssca$anomalyLastSent = tick; }
    @Override public int ssca$pendingAnomalyHits() { return ssca$pendingAnomalyHits; }
    @Override public void ssca$pendingAnomalyHits(int count) { ssca$pendingAnomalyHits = count; }
    @Override public SpellAnomalyContext ssca$summonAnomaly() { return ssca$summonAnomaly; }
    @Override public void ssca$summonAnomaly(SpellAnomalyContext context) { ssca$summonAnomaly = context; }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void ssca$saveSummonIdentity(NbtCompound nbt, CallbackInfo ci) {
        var context = ((SpellAnomalyHits.SummonedData) this).ssca$summonAnomaly();
        if (context != null) nbt.put("SscaSummonAnomaly", context.write());
        else nbt.remove("SscaSummonAnomaly");
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ssca$readSummonIdentity(NbtCompound nbt, CallbackInfo ci) {
        ((SpellAnomalyHits.SummonedData) this).ssca$summonAnomaly(nbt.contains("SscaSummonAnomaly", NbtElement.COMPOUND_TYPE)
                ? SpellAnomalyContext.read(nbt.getCompound("SscaSummonAnomaly")) : null);
    }
}
