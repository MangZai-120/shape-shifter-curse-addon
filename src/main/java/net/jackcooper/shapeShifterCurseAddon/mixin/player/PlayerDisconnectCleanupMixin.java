package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerDisconnectCleanupMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void ssc_addon$cleanupBeforeSaving(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerSessionCleanup.disconnect(player.networkHandler, player.getServer());
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void ssc_addon$forgetSavedAbsorption(ServerPlayerEntity player, CallbackInfo ci) {
        var online = player.getServer().getPlayerManager().getPlayer(player.getUuid());
        if (online == null || online.networkHandler == player.networkHandler) {
            net.jackcooper.shapeShifterCurseAddon.ability.PlayDeadAbsorptionManager.forgetPlayer(player.getUuid());
        }
    }
}
