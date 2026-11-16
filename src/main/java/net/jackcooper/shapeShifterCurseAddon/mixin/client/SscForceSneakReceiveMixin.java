package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SscForceSneakClient;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.onixary.shapeShifterCurseFabric.client.ClientPlayerStateManager;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModPacketsS2C.class)
public abstract class SscForceSneakReceiveMixin {
    // Keep SSC's one-boolean packet and client-thread scheduling, without relying on
    // a wildcard selecting compiler-generated lambdas in Forge's older Mixin.
    @Inject(method = "receiveForceSneakState", remap = false, at = @At("HEAD"), cancellable = true)
    private static void ssca$receiveForcedSneak(MinecraftClient client, ClientPlayNetworkHandler handler,
                                               PacketByteBuf buffer, PacketSender sender, CallbackInfo ci) {
        boolean force = buffer.readBoolean();
        client.execute(() -> ClientPlayerStateManager.shouldForceSneak = SscForceSneakClient.receive(force));
        ci.cancel();
    }
}
