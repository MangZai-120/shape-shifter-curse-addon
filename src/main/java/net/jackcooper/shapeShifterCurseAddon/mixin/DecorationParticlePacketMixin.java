package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Covers vanilla /particle, Apoli spawn_particles and SSC circles without changing their spawning logic. */
@Mixin(ServerWorld.class)
public abstract class DecorationParticlePacketMixin {
    @WrapOperation(method = "sendToPlayerIfNearby", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void ssca$sendDecoration(ServerPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original) {
        if (!DecorationParticles.trySendScoped((ServerWorld) (Object) this, handler.player, packet))
            original.call(handler, packet);
    }
}
