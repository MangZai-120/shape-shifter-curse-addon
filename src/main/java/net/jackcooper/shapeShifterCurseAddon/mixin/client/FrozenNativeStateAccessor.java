package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.minecraft.entity.AnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AnimationState.class)
public interface FrozenNativeStateAccessor {
    @Accessor("updatedAt") void ssca$updatedAt(long value);
    @Accessor("timeRunning") void ssca$timeRunning(long value);
}
