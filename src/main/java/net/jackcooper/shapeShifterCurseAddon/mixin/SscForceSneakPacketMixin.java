package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModPacketsS2CServer.class)
public abstract class SscForceSneakPacketMixin {
    /** The replacement uses state on each player entity, instead of SSC's shared static state/ticker. */
    @Inject(method = "sendForceSneakState", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$replaceSharedSneakSync(ServerPlayerEntity player, boolean force, CallbackInfo ci) {
        ci.cancel();
    }
}
