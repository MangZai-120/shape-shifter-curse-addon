package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 能量转变器方块（jackcooper）：SSCA 能量 → Team Reborn Energy(E) 的单向转换枢纽。
 * <p>右键切换 {@link #ACTIVE} 激活态（激活时微发光，方块状态持久化）；激活后由
 * {@link EnergyConverterBlockEntity#tick} 驱动：从相邻能量网络抽 SSCA 能量，
 * 按 1:4 转成 E 主动推送给相邻的工业能量方块（TechReborn 电缆/机器等，push-based）。
 * <p>右键除切换激活外，同时动作栏显示所在网络能量（同储罐/汲取器交互习惯）。
 */
@SuppressWarnings("deprecation") // 覆写 vanilla @Deprecated 的 Block 交互/状态方法，统一抑制
public class EnergyConverterBlock extends BlockWithEntity {

	/** 是否激活（激活后才开始转换输出）。 */
	public static final BooleanProperty ACTIVE = Properties.LIT;

	public EnergyConverterBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	/** 激活时微发光（核心运转的视觉反馈，luminance 注册于 RegAddonBlocks 的 Settings）。 */
	public static int luminanceOf(BlockState state) {
		return state.get(ACTIVE) ? 7 : 0;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyConverterBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// 仅服务端 tick（能量转换判定与推送全部服务端权威）
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.ENERGY_CONVERTER_BE, EnergyConverterBlockEntity::tick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			boolean active = !state.get(ACTIVE);
			world.setBlockState(pos, state.with(ACTIVE, active), Block.NOTIFY_ALL);
			world.playSound(null, pos,
					active ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
					SoundCategory.BLOCKS, 0.6f, active ? 1.4f : 1.0f);
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyConverterBlockEntity conv) {
				// 无缓冲实时结算架构：关闭即断供（extract 守卫激活态），无需清退动作
				List<EnergyNetworkMember> net = conv.getEnergyNetwork();
				player.sendMessage(Text.translatable(
						active ? "message.ssc_addon.energy_converter.on" : "message.ssc_addon.energy_converter.off",
						EnergyNetwork.getTotalEnergy(net), EnergyNetwork.getTotalCapacity(net)), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	/** 邻居方块更新：激活态下网络/邻接变化时唤醒（转换 tick 自轮询，这里无需额外处理）。 */
	@Override
	public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
		super.neighborUpdate(state, world, pos, block, fromPos, notify);
		if (!world.isClient && state.get(ACTIVE)) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyConverterBlockEntity conv) {
				conv.markNetworkDirty();
			}
		}
	}

	/** 破坏：无缓冲实时结算架构下无残留能量可退，直接移除。 */
	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
