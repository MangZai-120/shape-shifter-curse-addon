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
 * 药品存储箱方块（jackcooper）：专存压缩能量药水（feed_potion），支持漏斗互通。
 * <p>右键打开界面（8 个存储槽）；破坏时掉落槽内物品。
 * <p>储药柜造型（门面朝 {@link #FACING}），放置时门面朝向放置者（同汲取器/装瓶器的开口朝向逻辑）。
 */
@SuppressWarnings("deprecation") // 覆写 vanilla @Deprecated 的 Block 交互/状态替换/旋转镜像方法，统一抑制
public class PotionStorageBoxBlock extends BlockWithEntity {

	/** 门面所朝的水平方向。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public PotionStorageBoxBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 门面朝向放置者：玩家水平朝向的反向（放置后门正对玩家）
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
		return new PotionStorageBoxBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// 仅服务端 tick（自动合并同类药水）
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.POTION_STORAGE_BOX_BE, PotionStorageBoxBlockEntity::tick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof PotionStorageBoxBlockEntity box) {
				player.openHandledScreen(box);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof PotionStorageBoxBlockEntity box) {
				dropInventory(world, pos, box);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	/** 破坏时把槽内物品在方块中心掉落。 */
	private static void dropInventory(World world, BlockPos pos, PotionStorageBoxBlockEntity be) {
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
