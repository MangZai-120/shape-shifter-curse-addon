package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.NetworkCompatibilityClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject an identified, unnegotiated SSCA peer before its entity data reaches a tracker. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class NetworkCompatibilityMixin {
    @Inject(method = "onEntitySpawn", at = @At("HEAD"), cancellable = true)
    private void ssca$checkEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        // The vanilla forceMainThread call re-enters this handler on the client thread.
        // Do not look up a client world from the first Netty-thread invocation.
        if (!MinecraftClient.getInstance().isOnThread()) return;
        var handler = (ClientPlayNetworkHandler) (Object) this;
        if (NetworkCompatibilityClient.isConfirmed(handler.getConnection())) return;
        if (!NetworkCompatibilityClient.allowEntityType(handler.getConnection(), packet.getEntityType())) ci.cancel();
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("HEAD"), cancellable = true)
    private void ssca$checkEntityMetadata(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        var handler = (ClientPlayNetworkHandler) (Object) this;
        if (NetworkCompatibilityClient.isConfirmed(handler.getConnection())) return;
        var world = handler.getWorld();
        if (world != null && !NetworkCompatibilityClient.allowEntityMetadata(
                handler.getConnection(), world.getEntityById(packet.id()))) ci.cancel();
    }
}
