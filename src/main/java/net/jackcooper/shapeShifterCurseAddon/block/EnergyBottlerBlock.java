package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 能量装瓶器方块（jackcooper）：类炼药台的三线并行装瓶机。
 * <p>右键打开界面（3 空瓶输入槽 + 3 能量瓶输出槽 + 能量条 + 手动/自动按钮）；逻辑由
 * {@link EnergyBottlerBlockEntity#tick} 驱动。从相邻能量网络抽能量，破坏时掉落槽内物品。
 */
@SuppressWarnings("deprecation") // 覆写 vanilla @Deprecated 的 Block 交互/状态替换/旋转镜像方法，统一抑制
public class EnergyBottlerBlock extends BlockWithEntity {

	/** 开口所朝的水平方向（同汲取器：放置时开口朝向放置者）。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public EnergyBottlerBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 开口朝向放置者（同汲取器）：玩家水平朝向的反向
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyBottlerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.ENERGY_BOTTLER_BE, EnergyBottlerBlockEntity::tick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyBottlerBlockEntity bottler) {
				player.openHandledScreen(bottler);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyBottlerBlockEntity bottler) {
				dropInventory(world, pos, bottler);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	/** 破坏时把槽内物品在方块中心掉落。 */
	private static void dropInventory(World world, BlockPos pos, EnergyBottlerBlockEntity be) {
		for (int i = 0; i < be.size(); i++) {
			ItemStack stack = be.getStack(i);
			if (!stack.isEmpty()) {
				world.spawnEntity(new ItemEntity(world,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
			}
		}
		be.clear();
	}
}
