package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
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
 * 注魔台（jackcooper）。右键打开界面，往书槽放月尘魔法书 + 燃料槽放月尘粉/纯晶充法力，
 * 达经验阈值后配合超级塑形核心升级魔法书。破坏时散落内含物品。
 * <p>1.20.1 中 onUse/onStateReplaced/rotate/mirror 的覆写只存在于 deprecated 父类方法，
 * 这是原版 BlockWithEntity 的标准写法，IDE 警告属误报，类级抑制。</p>
 */
@SuppressWarnings("deprecation")
public class InfusionAltarBlock extends BlockWithEntity {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public InfusionAltarBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 正面朝向放置者（与原版讲台一致）
		return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new InfusionAltarBlockEntity(pos, state);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
		if (factory != null) {
			player.openHandledScreen(factory);
		}
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return checkType(type, RegAddonBlockEntities.INFUSION_ALTAR_BE, InfusionAltarBlockEntity::tick);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof InfusionAltarBlockEntity altar) {
				net.minecraft.util.ItemScatterer.spawn(world, pos, altar.getItems());
				world.updateComparators(pos, this);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}
}
