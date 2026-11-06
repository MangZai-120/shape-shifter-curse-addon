package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.ability.DeathDomainRecovery;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class DeathDomainRecoverySaveMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void ssca$confirmTerrainRecoverySave(ProgressListener progress, boolean flush, boolean savingDisabled,
                                                CallbackInfo ci) {
        if (flush && !savingDisabled) {
            ServerWorld world = (ServerWorld) (Object) this;
            DeathDomainRecovery.afterFlushedSave(world);
            net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager.afterFlushedSave(world);
        }
    }
}
