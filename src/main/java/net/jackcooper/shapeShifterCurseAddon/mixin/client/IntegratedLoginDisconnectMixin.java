package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.integrated.IntegratedServer;
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
    @Shadow @Nullable private IntegratedServer server;
    @Shadow @Nullable public ClientWorld world;
    @Shadow @Nullable public Screen currentScreen;

    @Shadow public abstract void disconnect(Screen screen);

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/ClientConnection;tick()V", shift = At.Shift.AFTER))
    private void ssca$closeFailedLocalLogin(CallbackInfo ci) {
        if (integratedServerConnection == null || integratedServerConnection.isOpen()
                || !integratedServerConnection.isLocal() || world != null || server == null) return;

        // A rejected login never creates a client world or a joined host player
        // whose logout would normally stop the server and release the save lock.
        if (server.isRunning() && !server.isStopped()) server.stop(true);
        if (server.getThread().isAlive()) return;
        integratedServerRunning = false;
        disconnect(currentScreen);
    }
}
