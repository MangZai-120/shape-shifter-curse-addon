package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks vanilla gameplay actions; mod protocol/configuration payloads keep their normal receivers. */
@Mixin(value = ServerPlayNetworkHandler.class, priority = 2000)
public abstract class FrozenNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ssca$allowFrozenSuspension(CallbackInfo ci) {
        if (FrozenControl.blocked(player)) {
            floating = false;
            floatingTicks = 0;
        }
    }

    @ModifyVariable(method = "onPlayerMove", at = @At("HEAD"), argsOnly = true)
    private PlayerMoveC2SPacket ssca$restrictMove(PlayerMoveC2SPacket packet) {
        if (!player.getServer().isOnThread()) return packet;
        if (FrozenControl.blocked(player)) {
            // Keep vanilla position/collision validation: falling, entity pushes and piston motion must be accepted.
            return FrozenControl.lockLook(player,packet);
        }
        return packet;
    }

    @Inject(method = {"onPlayerInteractEntity", "onPlayerInteractBlock", "onPlayerInteractItem", "onPlayerAction",
            "onClientCommand", "onHandSwing", "onClickSlot", "onCreativeInventoryAction", "onButtonClick",
            "onUpdateSelectedSlot", "onPlayerInput", "onVehicleMove", "onRecipeBookData", "onCraftRequest"},
            at = @At("HEAD"), cancellable = true)
    private void ssca$blockActions(CallbackInfo ci) {
        // The off-thread invocation is queued by vanilla; its main-thread replay executes this guard.
        if (player.getServer().isOnThread() && FrozenControl.blocked(player)) ci.cancel();
    }
}
