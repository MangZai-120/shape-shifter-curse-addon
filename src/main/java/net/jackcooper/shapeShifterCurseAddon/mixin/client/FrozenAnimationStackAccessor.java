package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import java.util.ArrayList;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AnimationStack.class,remap = false)
public interface FrozenAnimationStackAccessor {
    @Accessor("layers") ArrayList<Pair<Integer,IAnimation>> ssca$layers();
}
