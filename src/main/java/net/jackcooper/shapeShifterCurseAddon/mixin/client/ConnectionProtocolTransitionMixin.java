package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep protocol changes and their read-interest updates on the same Netty event loop. */
@Mixin(ClientConnection.class)
public abstract class ConnectionProtocolTransitionMixin {
    @Shadow private Channel channel;

    @Shadow public abstract boolean isLocal();

    @Shadow
    private void sendImmediately(Packet<?> packet, @Nullable PacketCallbacks callbacks) {
        throw new AssertionError();
    }

    @Inject(method = "sendImmediately", at = @At("HEAD"), cancellable = true)
    private void ssca$serializeProtocolTransition(Packet<?> packet, @Nullable PacketCallbacks callbacks,
                                                  CallbackInfo ci) {
        // Vanilla disables auto-read on the caller thread before scheduling the write. During
        // login, that can clear OP_READ after the event loop has already re-enabled auto-read.
        // Queue the whole operation so its disable/change-protocol/enable sequence cannot race.
        // In-process connections have no socket read interest. Kilt's local login
        // queries rely on vanilla dispatch on the caller thread.
        if (!isLocal() && !channel.eventLoop().inEventLoop()) {
            channel.eventLoop().execute(() -> sendImmediately(packet, callbacks));
            ci.cancel();
        }
    }
}
