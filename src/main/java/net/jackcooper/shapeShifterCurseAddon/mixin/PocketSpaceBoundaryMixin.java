package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅保护口袋维度外壳和出口凹槽；内部搭建与其他维度不受影响。拒绝时重发方块更新实现「破坏后立即补回」。 */
@Mixin(World.class)
public abstract class PocketSpaceBoundaryMixin {
	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
			at = @At("HEAD"), cancellable = true)
	private void sscAddon$protectPocketBoundary(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerWorld world && !PocketSpaceManager.canChangeBlock(world, pos)) {
			// 自愈：拒绝写入的同时重发原方块状态 → 客户端上表现为「破坏后立刻补回一个」
			world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
			cir.setReturnValue(false);
		}
	}
}