package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露按世界设置观察距离（protected）：口袋维度压到 3，只加载房间周围小区块，根治进房卡顿。 */
@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageAccessor {
	@Invoker("setViewDistance")
	void sscAddon$setViewDistance(int watchDistance);
}
