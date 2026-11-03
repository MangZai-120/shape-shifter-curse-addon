package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
@Mixin(value = AnimSystem.class,remap = false)
public abstract class FrozenSscAnimationMixin {
    @Shadow @Final public PlayerEntity player;
    @Unique private AnimationHolder ssca$lastSelection;
    @Unique private boolean ssca$selected, ssca$wasPaused;
    @Unique private FrozenAnimationDrivers.ResumedClip ssca$resuming;
    @Shadow private void NPPA_Tick() { throw new AssertionError(); }
    @Shadow public AnimSystem.AnimSystemData data;
    @WrapMethod(method = "getAnimation")
    private AnimationHolder ssca$pauseStateMachine(Operation<AnimationHolder> original) {
        if (!player.getWorld().isClient) return original.call();
        if (FrozenAnimationDrivers.paused(player)) {
            if (!ssca$wasPaused) {
                var clip = FrozenAnimationDrivers.sscClip(player);
                ssca$resuming = clip == null || ssca$lastSelection == null || ssca$lastSelection.getSpeed() <= 0
                        ? null : new FrozenAnimationDrivers.ResumedClip(clip);
            }
            ssca$wasPaused = true;
            if (!ssca$selected) { ssca$lastSelection = original.call(); ssca$selected = true; }
            return ssca$lastSelection;
        }
        // A one-frame grace period lets the FSM replace an unfinished action on the very next tick.
        // Retain the real player/clip instead; naturally advance it until its end before switching.
        if ((ssca$wasPaused || ssca$resuming != null) && player.isAlive() && player.deathTime == 0) {
            boolean first = ssca$wasPaused;
            ssca$wasPaused = false;
            if ((ssca$resuming != null && ssca$resuming.unfinished()) || (first && ssca$resuming == null)) {
                if (data != null) data.LastPosition = player.getPos();
                NPPA_Tick();
                return ssca$lastSelection;
            }
        }
        ssca$resuming = null;
        ssca$wasPaused = false; ssca$selected = true;
        return ssca$lastSelection = original.call();
    }
}
