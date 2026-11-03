package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A projectile keeps the launch spell even after its caster switches scrolls or logs out. */
@Mixin(ProjectileEntity.class)
public abstract class SpellAnomalyProjectileMixin implements SpellAnomalyHits.ProjectileData {
    @Unique private SpellAnomalyContext ssca$anomaly;

    @Override public SpellAnomalyContext ssca$anomaly() { return ssca$anomaly; }
    @Override public void ssca$anomaly(SpellAnomalyContext context) { ssca$anomaly = context; }

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void ssca$bindAnomaly(Entity owner, CallbackInfo ci) {
        var context = SpellAnomalyHits.current();
        if (context != null && owner != null && context.casterUUID().equals(owner.getUuid()))
            ssca$anomaly = context;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void ssca$writeAnomaly(NbtCompound nbt, CallbackInfo ci) {
        if (ssca$anomaly != null) nbt.put("SscaSpellAnomaly", ssca$anomaly.write());
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ssca$readAnomaly(NbtCompound nbt, CallbackInfo ci) {
        ssca$anomaly = nbt.contains("SscaSpellAnomaly", NbtElement.COMPOUND_TYPE)
                ? SpellAnomalyContext.read(nbt.getCompound("SscaSpellAnomaly")) : null;
    }
}
