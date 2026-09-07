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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

/**
 * 法术研究台（jackcooper）。右键打开双页签界面：
 * 「法阵抄写」（空白法阵纸+油墨 → 抄写已学习法阵）与「法术学习」（月尘 → 学习已记录法阵）。
 * 破坏时散落内含物品。
 * <p>1.20.1 中 onUse/onStateReplaced/rotate/mirror 的覆写只存在于 deprecated 父类方法，
 * 这是原版 BlockWithEntity 的标准写法，IDE 警告属误报，类级抑制。</p>
 */
@SuppressWarnings("deprecation")
public class SpellResearchTableBlock extends BlockWithEntity {

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	/** 朝北（FACING=north）基准碰撞箱：四角桌腿(0~10) + 桌面(10~12) + 背板(12~16，模型视觉高 18 超出方块上界，碰撞截到 16)。
	 * 桌上墨水池/羽毛笔/书本/纸张等薄小装饰不进碰撞，避免挡手。 */
	private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(
			Block.createCuboidShape(0, 0, 0, 2, 10, 2),     // 桌腿·西北
			Block.createCuboidShape(14, 0, 0, 16, 10, 2),   // 桌腿·东北
			Block.createCuboidShape(14, 0, 14, 16, 10, 16), // 桌腿·东南
			Block.createCuboidShape(0, 0, 14, 2, 10, 16),   // 桌腿·西南
			Block.createCuboidShape(0, 10, 0, 16, 12, 16),  // 桌面
			Block.createCuboidShape(0, 12, 0, 16, 16, 1)    // 背板（北侧）
	);

	/** 各朝向形状（与 blockstate 的 y 旋转约定一致，绕 Y 轴由基准形状生成）。 */
	private static final EnumMap<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction dir : Direction.Type.HORIZONTAL) {
			SHAPES.put(dir, rotateShape(Direction.NORTH, dir, SHAPE_NORTH));
		}
	}

	public SpellResearchTableBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// 背板朝向玩家对面：FACING 直接取玩家水平朝向（桌面/斜面书正对放置者）
		return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	/** 绕 Y 轴把 VoxelShape 从 from 旋转到 to，单次旋转与 blockstate "y": 90 等价（社区通用实现，同 EnergyExtractorBlock）。 */
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
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPES.getOrDefault(state.get(FACING), SHAPE_NORTH);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPES.getOrDefault(state.get(FACING), SHAPE_NORTH);
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
		return new SpellResearchTableBlockEntity(pos, state);
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
		// 无 tick 逻辑（抄写/学习全由按钮 C2S 驱动）
		return null;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof SpellResearchTableBlockEntity table) {
				net.minecraft.util.ItemScatterer.spawn(world, pos, table.getItems());
				world.updateComparators(pos, this);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}
}
