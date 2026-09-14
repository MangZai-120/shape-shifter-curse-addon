package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OperatorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 口袋空间功能方块（jackcooper）。三类方块均不注册 BlockItem（无物品形态：
 * 创造物品栏不可见、/give 不可得、中键取不到），并实现原版 OperatorBlock
 * （屏障同款）：非OP玩家创造模式下客户端直接拒绝破坏预测，服务端同拦。
 */
public final class PocketSpaceBlocks {
	/** 地面：平滑石英外观，不可破坏（OperatorBlock），保留完整轮廓箱可瞄准可右键。 */
	public static final Block FLOOR = new FloorBlock();
	/** 墙壁/顶盖：隐形基岩（仿基岩版 barrier 行为），隐形渲染但保留轮廓箱，可瞄准可右键。 */
	public static final Block BEDROCK_WALL = new InvisibleBedrockBlock();
	/** 传送台：低矮台面，不可破坏（OperatorBlock），保留轮廓箱。 */
	public static final Block PORTAL = new PortalBlock();

	private PocketSpaceBlocks() {}

	public static void init() {
		// 只注册 BLOCK，不注册 ITEM：无物品形态 → 创造物品栏没有、指令调不出、中键取不到
		Registry.register(Registries.BLOCK, new Identifier("ssc_addon", "pocket_space_floor"), FLOOR);
		Registry.register(Registries.BLOCK, new Identifier("ssc_addon", "pocket_space_bedrock_wall"), BEDROCK_WALL);
		Registry.register(Registries.BLOCK, new Identifier("ssc_addon", "pocket_space_portal"), PORTAL);
		VoidWallBlockEntity.TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				new Identifier("ssc_addon", "pocket_space_bedrock_wall"),
				net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
						.create(VoidWallBlockEntity::new, BEDROCK_WALL).build());
	}

	/** 地面：不可破坏的平滑石英（不落任何掉落物）。 */
	private static final class FloorBlock extends Block implements OperatorBlock {
		private FloorBlock() {
			super(AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ)
					.strength(-1.0f, 3_600_000.0f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK));
		}
	}

	/**
	 * 隐形基岩：完全透光（opacity=0）+ 由方块实体渲染器画不受光照影响的虚空色面（末地折跃门同款机制），
	 * 因此透过它看到的永远是虚空；配合服务端仅向玩家发送自己房间的区块，墙后不存在任何可见方块。
	 * 保留完整碰撞与轮廓箱（可瞄准、可右键）。
	 */
	public static final class InvisibleBedrockBlock extends net.minecraft.block.BlockWithEntity implements OperatorBlock {
		private InvisibleBedrockBlock() {
			super(AbstractBlock.Settings.copy(Blocks.BEDROCK)
					.strength(-1.0f, 3_600_000.0f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK)
					.nonOpaque());
		}

		@Override
		public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
			return true; // 光照 0 衰减：天光穿过顶盖照亮房间
		}

		@Override
		public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new VoidWallBlockEntity(pos, state);
		}
	}

	/** 隐形基岩的方块实体：无数据，只为挂载虚空面渲染器。 */
	public static final class VoidWallBlockEntity extends net.minecraft.block.entity.BlockEntity {
		public static net.minecraft.block.entity.BlockEntityType<VoidWallBlockEntity> TYPE;

		public VoidWallBlockEntity(BlockPos pos, BlockState state) {
			super(TYPE, pos, state);
		}
	}

	private static final class PortalBlock extends Block implements OperatorBlock {
		private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

		private PortalBlock() {
			super(AbstractBlock.Settings.copy(Blocks.SMOOTH_QUARTZ).strength(-1.0f, 3_600_000.0f)
					.dropsNothing().pistonBehavior(PistonBehavior.BLOCK).nonOpaque().luminance(state -> 12));
		}

		@Override
		@SuppressWarnings("deprecation") // javac -Xlint:deprecation 会告警（IDE 不开该开关才显示多余）
		public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
			return SHAPE;
		}
	}
}