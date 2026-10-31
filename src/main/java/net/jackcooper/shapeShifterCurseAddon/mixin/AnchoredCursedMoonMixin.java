package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 暂缓个人变形事务，防止未实际变形却记录了月夜退回形态；不改全局月相与本能锁。 */
@Mixin(value = CursedMoon.class, remap = false)
public abstract class AnchoredCursedMoonMixin {
    @Inject(method = {"applyStartCursedMoonEffect", "applyEndCursedMoonEffect"}, at = @At("HEAD"),
            cancellable = true, require = 2)
    private static void ssca$deferMoonTransform(World world, PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InstinctAnchors.locked(player)) ci.cancel();
    }
}
