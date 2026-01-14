package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class SscAddonEntityIgnitionMixin implements SscIgnitedEntityAccessor {

    @Unique
    private UUID sscAddon$igniterUuid;

    @Override
    public void sscAddon$setIgniterUuid(UUID uuid) {
        this.sscAddon$igniterUuid = uuid;
    }

    @Override
    public UUID sscAddon$getIgniterUuid() {
        return this.sscAddon$igniterUuid;
    }

    @Inject(method = "writeNbt", at = @At("HEAD"))
    private void injectWriteNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        if (this.sscAddon$igniterUuid != null) {
            nbt.putUuid("SscAddonIgniter", this.sscAddon$igniterUuid);
        }
    }

    @Inject(method = "readNbt", at = @At("HEAD"))
    private void injectReadNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("SscAddonIgniter")) {
            this.sscAddon$igniterUuid = nbt.getUuid("SscAddonIgniter");
        }
    }

    @ModifyArg(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"), index = 0)
    private DamageSource modifyFireDamageSource(DamageSource source) {
        if (source.isOf(DamageTypes.ON_FIRE)) {
             UUID igniter = this.sscAddon$getIgniterUuid();
             if (igniter != null) {
                 Entity entity = (Entity)(Object)this;
                 if (!entity.getWorld().isClient) {
                     PlayerEntity player = entity.getWorld().getPlayerByUuid(igniter);
                     if (player != null) {
                         return new DamageSource(source.getTypeRegistryEntry(), null, player);
                     }
                 }
             }
        }
        return source;
    }
}
