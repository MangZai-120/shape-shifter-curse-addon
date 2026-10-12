package net.jackcooper.shapeShifterCurseAddon.compat;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Spider3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** Keep the jump's final pose until the form FSM selects another animation. */
public final class SpiderJumpAnimController extends AbstractAnimStateController {
    private static final Map<PlayerEntity, Integer> RESTART_REQUESTS = new WeakHashMap<>();
    private final Map<AnimSystem.AnimSystemData, Playback> playbacks = new WeakHashMap<>();
    private final AnimUtils.AnimationHolderData source = Form_Spider3.ANIM_JUMP;
    private @Nullable AnimationHolder original;

    private static final class Playback {
        private AnimationHolder holder;
        private int lastTick;
    }

    /** The second jump can happen while the FSM is already in JUMP. */
    public static void restartJump(PlayerEntity player) {
        RESTART_REQUESTS.put(player, player.age);
    }

    @Override
    public void registerAnim(PlayerEntity player, AnimSystem.AnimSystemData data) {
        original = source.build();
        super.registerAnim(player, data);
    }

    @Override
    public @Nullable AnimationHolder getAnimation(PlayerEntity player, AnimSystem.AnimSystemData data) {
        if (original == null || original.getAnimation() == null) return original;
        Playback playback = playbacks.get(data);
        Integer requestedTick = RESTART_REQUESTS.remove(player);
        boolean requested = requestedTick != null && player.age - requestedTick >= 0
                && player.age - requestedTick <= 2;
        if (playback == null || player.age - playback.lastTick > 1 || requested) {
            playback = new Playback();
            playback.holder = holdFinalPose(original);
            playbacks.put(data, playback);
        }
        if (requested) {
            // SSC derives its animation fall distance from positions, not Entity.fallDistance.
            data.fallDistanceTemp = player.getY();
            data.fallDistance = 0;
        }
        playback.lastTick = player.age;
        return playback.holder;
    }

    private static AnimationHolder holdFinalPose(AnimationHolder original) {
        KeyframeAnimation.AnimationBuilder builder = original.getAnimation().mutableCopy();
        builder.isLooped = true;
        builder.returnTick = builder.endTick;
        builder.stopTick = builder.endTick + 1;
        return new AnimationHolder(builder.build(), true, original.getSpeed(), original.getFade());
    }
}
