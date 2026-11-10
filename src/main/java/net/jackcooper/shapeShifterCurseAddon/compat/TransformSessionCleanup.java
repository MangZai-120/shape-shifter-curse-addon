package net.jackcooper.shapeShifterCurseAddon.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.mixin.TransformManagerAccessor;

/** SSC 1.10 keeps finished transform entries alive until the next server start. */
public final class TransformSessionCleanup {
    private TransformSessionCleanup() {}

    /** Cancel only the expected transformation; a replacement animation belongs to its new caller. */
    public static void cancelExpected(net.minecraft.server.network.ServerPlayerEntity player,
            net.minecraft.util.Identifier expected) {
        if (expected == null) return;
        var form = net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (form.transformTargetForm != null && !expected.equals(form.transformTargetForm.getFormID())) return;
        var entries = TransformManagerAccessor.ssca$playerData();
        var animation = entries.get(player.getUuid());
        if (animation != null && (animation.transformEndForm == null
                || !expected.equals(animation.transformEndForm.getFormID()))) return;
        if (animation == null && form.transformTargetForm == null) return;
        if (animation != null) {
            animation.onTransformComplete = null;
            animation.transformTimer = -1;
            entries.remove(player.getUuid(), animation);
        }
        form.transformTargetForm = null;
        net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils.playerInstinctLock.remove(player.getUuid());
        player.removeStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.STUN);
        net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer.sendTransformState(player, false,
                form.nowForm == null ? null : form.nowForm.getFormID(), expected);
        net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer.sendNoMoveTick(player, 0);
        net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer.sendNoJumpTick(player, 0);
    }

    public static void init() {
        // PlayerSessionCleanup verifies this is the current connection before invoking us.
        // Removing the current UUID also covers an old entity retained through respawn.
        PlayerSessionCleanup.register((handler, server) ->
                TransformManagerAccessor.ssca$playerData().remove(handler.player.getUuid()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var entries = TransformManagerAccessor.ssca$playerData();
            var previous = entries.get(handler.player.getUuid());
            if (previous != null && previous.player != handler.player)
                entries.remove(handler.player.getUuid(), previous);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                TransformManagerAccessor.ssca$playerData().clear());
    }
}
