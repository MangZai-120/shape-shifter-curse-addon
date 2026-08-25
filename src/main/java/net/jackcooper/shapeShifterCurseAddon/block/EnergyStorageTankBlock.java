package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
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
 * 能量储罐方块（jackcooper）：正方体被动存储节点。
 * <p>相邻储罐/汲取器构成共享能量网络（见 {@link EnergyNetwork}），多个相邻即可叠加总上限。
 * 右键在动作栏显示所在网络能量；放置/破坏时广播刷新网络拓扑缓存（事件驱动，非高频扫描）。
 */
@SuppressWarnings("deprecation") // 覆写 vanilla @Deprecated 的 Block 交互/状态替换方法，统一抑制
public class EnergyStorageTankBlock extends BlockWithEntity {

	public EnergyStorageTankBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EnergyStorageTankBlockEntity(pos, state);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof EnergyStorageTankBlockEntity tank) {
				List<EnergyNetworkMember> net = tank.getNetwork();
				player.sendMessage(Text.translatable("message.ssc_addon.energy_network.status",
						EnergyNetwork.getTotalEnergy(net), EnergyNetwork.getTotalCapacity(net)), true);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && !state.isOf(oldState.getBlock())) {
			EnergyNetwork.broadcastInvalidate(world, pos);
		}
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			super.onStateReplaced(state, world, pos, newState, moved);
			if (!world.isClient) {
				EnergyNetwork.broadcastInvalidate(world, pos);
			}
		}
	}
}
