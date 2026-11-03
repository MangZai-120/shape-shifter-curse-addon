package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.minecraft.entity.player.PlayerEntity;
@Mixin(PlayerEntity.class)
public abstract class FrozenPlayerAnimationTickMixin {
    @WrapMethod(method = "tick")
    private void ssca$bindPlaybackOwner(Operation<Void> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (player.getWorld().isClient && player instanceof IAnimatedPlayer animated)
            FrozenAnimationDrivers.bind(player,animated.getAnimationStack());
        original.call();
    }
}
