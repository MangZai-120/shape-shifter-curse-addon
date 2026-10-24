package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.compat.SscForceSneak;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.networking.ModPackets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class SscForceSneakSyncMixin {
    @Unique private boolean ssca$sneakInitialized;
    @Unique private boolean ssca$lastForcedSneak;
    @Unique private int ssca$lastSneakSync;
    @Unique private RegistryKey<World> ssca$sneakWorld;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ssca$syncOwnForcedSneak(CallbackInfo ci) {
        var player = (ServerPlayerEntity) (Object) this;
        if (!ServerPlayNetworking.canSend(player, ModPackets.SYNC_FORCE_SNEAK_STATE)) return;
        boolean force = SscForceSneak.required(player);
        var world = player.getWorld().getRegistryKey();
        if (!ssca$sneakInitialized || force != ssca$lastForcedSneak || world != ssca$sneakWorld
                || player.age - ssca$lastSneakSync >= 100) {
            var packet = PacketByteBufs.create();
            packet.writeBoolean(force);
            ServerPlayNetworking.send(player, ModPackets.SYNC_FORCE_SNEAK_STATE, packet);
            ssca$sneakInitialized = true;
            ssca$lastForcedSneak = force;
            ssca$lastSneakSync = player.age;
            ssca$sneakWorld = world;
        }
    }
}
