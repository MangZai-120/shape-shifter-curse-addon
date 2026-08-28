package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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
 * 创造能量储罐方块（jackcooper）：仅创造模式可获取的无限能量源。
 * <p>放置后作为能量网络成员接入相邻网络（继承储罐的放置/破坏拓扑广播），
 * 每 20 tick 把网络内所有储罐补满（见 {@link CreativeEnergyTankBlockEntity#tick}）。
 * 自身不参与能量统计（贡献 0/0），避免污染网络能量显示。
 * 物品带附魔光效（见 RegAddonBlocks 注册处的 glint BlockItem）。
 */
public class CreativeEnergyTankBlock extends EnergyStorageTankBlock {

	public CreativeEnergyTankBlock(Settings settings) {
		super(settings);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CreativeEnergyTankBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// 仅服务端 tick（补满判定服务端权威）
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.CREATIVE_ENERGY_TANK_BE, CreativeEnergyTankBlockEntity::tick);
	}

	/** 右键动作栏显示网络能量（同储罐交互习惯；BE 类型不同故在此覆写）。 */
	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			List<EnergyNetworkMember> net = EnergyNetwork.collect(world, pos);
			player.sendMessage(Text.translatable("message.ssc_addon.energy_network.status",
					EnergyNetwork.getTotalEnergy(net), EnergyNetwork.getTotalCapacity(net)), true);
		}
		return ActionResult.success(world.isClient);
	}
}
