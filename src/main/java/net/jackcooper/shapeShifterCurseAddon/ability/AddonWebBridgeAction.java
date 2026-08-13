package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.block.WebMembraneOwners;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.blocks.RegCustomBlock;
import net.onixary.shapeShifterCurseFabric.blocks.TempWebBridgeBlock;

/**
 * 蛛丝搭路工具（jackcooper）：从原版 SSC {@code WebBridgeAction} 移植的搭路逻辑，
 * 仅一处行为差异——放置桥/梯块时若目标格是附属的减速蛛网膜（web_membrane），
 * 先静默拆除网膜（顺带清 {@link WebMembraneOwners} 施法者记录）再放置，
 * 实现「搭路方块替代网膜」；其余放置规则（空气 / 既有桥块）与原版一致。
 *
 * <p>放置的方块沿用原版 {@link RegCustomBlock#TEMP_WEB_BRIDGE}（继承其寿命 /
 * 邻居塌落 / 渲染等全部行为），故对游戏内已有蛛丝桥的存档兼容。</p>
 */
public final class AddonWebBridgeAction {

	private AddonWebBridgeAction() {}

	/** 搭路放置门槛：空气 / 既有桥块 / 附属网膜（替代） → 可放。 */
	public static boolean SetWebBlock(World world, BlockPos pos, Block webBlock, Direction facing) {
		BlockState blockState = world.getBlockState(pos);
		if (blockState.isOf(RegAddonBlocks.WEB_MEMBRANE)) {
			// 网膜让位：拆掉后视作空气继续放置（removeBlock 不触发邻居连锁，后续 setBlockState 全量同步）
			world.removeBlock(pos, false);
			WebMembraneOwners.remove(pos);
			blockState = world.getBlockState(pos);
		}
		if (blockState.isAir() || blockState.isOf(webBlock)) {
			BlockState state = webBlock.getDefaultState().with(TempWebBridgeBlock.HORIZONTAL_FACING, facing);
			world.setBlockState(pos, state);
			return true;
		}
		return false;
	}

	/**
	 * 命中方块后建蛛丝天梯（从原版 BuildWebLadder 移植，放块改走 {@link #SetWebBlock}）。
	 * 命中顶面 → 向上爬升；命中底面 → 向下延伸；命中侧面 → 贴墙向下搭。加宽档（tier2+）
	 * 沿途随机补侧面邻格。
	 */
	public static void BuildWebLadder(World world, BlockHitResult blockHitResult, WebLadderConfig config, Block ladderBlock) {
		BlockPos pos = blockHitResult.getBlockPos();
		Direction direction = blockHitResult.getSide();

		Direction[] horizontalDirections = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		Random random = world.getRandom();

		BlockPos nowPos;
		Direction ladderDirection;
		int length;
		switch (direction) {
			case UP -> {
				nowPos = pos.up();
				ladderDirection = Direction.UP;
				length = config.TopBlockNum;
			}
			case DOWN -> {
				nowPos = pos.down();
				ladderDirection = Direction.DOWN;
				length = config.BottomBlockNum;
			}
			default -> {
				nowPos = pos.offset(direction);
				ladderDirection = Direction.DOWN;
				length = config.SideBlockNum;
			}
		}

		int largerLadderCount = (int) (config.LargerLadderCountPercent * length);

		for (int i = 0; i < length; i++) {
			Direction randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
			if (!SetWebBlock(world, nowPos, ladderBlock, randomFacing)) {
				break;
			}
			if (config.LargerLadder && largerLadderCount > 0) {
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, nowPos.east(), ladderBlock, randomFacing);
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, nowPos.west(), ladderBlock, randomFacing);
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, nowPos.north(), ladderBlock, randomFacing);
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, nowPos.south(), ladderBlock, randomFacing);
				largerLadderCount--;
			}
			nowPos = nowPos.offset(ladderDirection);
		}
	}

	/**
	 * 脚下平铺蛛丝桥（从原版 BuildWebBridge 移植，放块改走 {@link #SetWebBlock}）。
	 * 以 pos 为起点沿 direction 铺 length 长、两侧各 width 格的桥面。
	 */
	public static void BuildWebBridge(World world, BlockPos pos, Direction direction, WebBridgeConfig config, Block webBlock) {
		if (direction == Direction.UP || direction == Direction.DOWN) {
			return;
		}
		Direction[] horizontalDirections = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		Random random = world.getRandom();
		BlockPos nowPos = pos;
		for (int k = -config.Width; k <= config.Width; k++) {
			for (int m = -config.Width; m <= config.Width; m++) {
				Direction randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, pos.add(k, 0, m), webBlock, randomFacing);
			}
		}
		for (int i = 0; i < config.Length; i++) {
			Direction randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
			SetWebBlock(world, nowPos, webBlock, randomFacing);
			BlockPos tempPos = nowPos;
			Direction tempDirection = direction.rotateYClockwise();
			for (int j = 0; j < config.Width; j++) {
				tempPos = tempPos.offset(tempDirection);
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, tempPos, webBlock, randomFacing);
			}
			tempPos = nowPos;
			tempDirection = direction.rotateYCounterclockwise();
			for (int j = 0; j < config.Width; j++) {
				tempPos = tempPos.offset(tempDirection);
				randomFacing = horizontalDirections[random.nextInt(horizontalDirections.length)];
				SetWebBlock(world, tempPos, webBlock, randomFacing);
			}
			nowPos = nowPos.offset(direction);
		}
	}

	/** 天梯配置（对应原版 WebLadderConfig record）。 */
	public record WebLadderConfig(int SideBlockNum, int BottomBlockNum, int TopBlockNum,
								  boolean LargerLadder, float LargerLadderCountPercent) {}

	/** 桥面配置（对应原版 WebBridgeConfig record）。 */
	public record WebBridgeConfig(int Length, int Width) {}
}
