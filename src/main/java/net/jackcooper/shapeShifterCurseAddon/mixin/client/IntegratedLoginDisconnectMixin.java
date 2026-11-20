package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class IntegratedLoginDisconnectMixin {
    @Shadow @Nullable private ClientConnection integratedServerConnection;
    @Shadow private boolean integratedServerRunning;

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/ClientConnection;tick()V", shift = At.Shift.AFTER))
    private void ssca$closeFailedLocalLogin(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (integratedServerConnection == null || integratedServerConnection.isOpen()
                || !integratedServerConnection.isLocal() || client.world != null) return;
        var server = client.getServer();
        if (server == null) return;

        // A rejected login never creates a client world or a joined host player
        // whose logout would normally stop the server and release the save lock.
        if (server.isRunning() && !server.isStopped()) server.stop(true);
        if (server.getThread().isAlive()) return;
        integratedServerRunning = false;
        client.disconnect(client.currentScreen);
    }
}
