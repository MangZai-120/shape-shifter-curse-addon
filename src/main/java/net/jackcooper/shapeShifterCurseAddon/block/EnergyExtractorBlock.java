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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.text.Text;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/**
 * 能量汲取器方块（jackcooper）：单格空心舱，一面开口。
 * <p>开口朝向放置者（{@link #FACING} = 开口所朝方向）。碰撞体为「底板 + 后壁 + 左右壁」，
 * 前面与顶部开口，使魔系玩家可从开口走进本格站立（上半身从顶部露出）。右键打开交互界面。
 * 逻辑（mana→能量转化、能量瓶合成）由 {@link EnergyExtractorBlockEntity#tick} 驱动。
 */
@SuppressWarnings("deprecation") // 覆写多个 vanilla @Deprecated 的 Block 方法（形状/交互/状态替换/旋转镜像），统一抑制
public class EnergyExtractorBlock extends BlockWithEntity {

	/** 开口所朝的水平方向。 */
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	/** 开口朝北（-Z）时的基准舱壳形状：底板 + 后壁(南) + 左壁(西) + 右壁(东)，前(北)与顶开口。 */
	private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(
			Block.createCuboidShape(0, 0, 0, 16, 2, 16),   // 底板
			Block.createCuboidShape(0, 2, 14, 16, 14, 16), // 后壁（南 +Z）
			Block.createCuboidShape(0, 2, 0, 2, 14, 16),   // 左壁（西 -X）
			Block.createCuboidShape(14, 2, 0, 16, 14, 16)  // 右壁（东 +X）
	);

	/** 各朝向的舱壳形状（由基准形状绕 Y 旋转生成）。 */
	private static final EnumMap<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction dir : Direction.Type.HORIZONTAL) {
			SHAPES.put(dir, rotateShape(Direction.NORTH, dir, SHAPE_NORTH));
		}
	}

	public EnergyExtractorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	/** 绕 Y 轴把 VoxelShape 从 from 旋转到 to（社区通用实现）。 */
	private static VoxelShape rotateShape(Direction from, Direction to, VoxelShape shape) {
		VoxelShape[] buffer = new VoxelShape[]{shape, VoxelShapes.empty()};
		int times = (to.getHorizontal() - from.getHorizontal() + 4) % 4;
		for (int i = 0; i < times; i++) {
			buffer[0].forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
					buffer[1] = VoxelShapes.union(buffer[1],
							VoxelShapes.cuboid(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
			buffer[0] = buffer[1];
			buffer[1] = VoxelShapes.empty();
		}
		return buffer[0];
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 开口朝向放置者：玩家水平朝向的反向（放置后开口正对玩家，方便直接走进）
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPES.getOrDefault(state.get(FACING), SHAPE_NORTH);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPES.getOrDefault(state.get(FACING), SHAPE_NORTH);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyExtractorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// 仅服务端 tick（转化与合成均为服务端权威）
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.ENERGY_EXTRACTOR_BE, EnergyExtractorBlockEntity::tick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyExtractorBlockEntity ext) {
				// 右键在动作栏显示所在网络的能量总量（汲取器自身无 GUI）
				List<EnergyNetworkMember> net = ext.getNetwork();
				player.sendMessage(Text.translatable("message.ssc_addon.energy_network.status",
						EnergyNetwork.getTotalEnergy(net), EnergyNetwork.getTotalCapacity(net)), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		// 放置后广播刷新所在能量网络拓扑缓存（事件驱动，非高频扫描）
		if (!world.isClient && !state.isOf(oldState.getBlock())) {
			EnergyNetwork.broadcastInvalidate(world, pos);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			super.onStateReplaced(state, world, pos, newState, moved);
			// 破坏后广播刷新网络（此时本方块实体已移除，从邻接洪泛遍历其余成员）
			if (!world.isClient) {
				EnergyNetwork.broadcastInvalidate(world, pos);
			}
		}
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
