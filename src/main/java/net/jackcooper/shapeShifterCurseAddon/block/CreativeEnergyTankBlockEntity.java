package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;

import java.util.List;

/**
 * 创造能量储罐方块实体（jackcooper）：每 {@link #FILL_INTERVAL} tick 把所在网络内
 * 所有储罐补满（含汲取器缓冲）。自身计入网络连通（拓扑遍历用）但不参与能量统计
 * （{@code getStoredEnergy/getEnergyCapacity} 恒 0/0，避免污染动作栏与装瓶器读数）。
 * 补满后调用 {@link EnergyNetwork#refreshTankDisplays} 刷新液面显示。
 */
public class CreativeEnergyTankBlockEntity extends BlockEntity implements EnergyNetworkMember {

	/** 补满节拍（tick）。 */
	private static final int FILL_INTERVAL = 20;

	public CreativeEnergyTankBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.CREATIVE_ENERGY_TANK_BE, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, CreativeEnergyTankBlockEntity be) {
		if (world.isClient || world.getTime() % FILL_INTERVAL != 0) {
			return;
		}
		List<EnergyNetworkMember> net = EnergyNetwork.collect(world, pos);
		boolean filled = false;
		for (EnergyNetworkMember m : net) {
			if (m == be) {
				continue;
			}
			if (m.getStoredEnergy() < m.getEnergyCapacity()) {
				m.setStoredEnergy(m.getEnergyCapacity());
				filled = true;
			}
		}
		if (filled) {
			// 补满后刷新全网储罐液面显示（仅服务端同步变化过的）
			EnergyNetwork.refreshTankDisplays(net);
		}
	}

	// ==================== 能量网络成员（统计贡献 0，仅参与连通） ====================

	@Override
	public int getStoredEnergy() {
		return 0;
	}

	@Override
	public void setStoredEnergy(int value) {
		// 创造储罐不可被写入（补满循环里会跳过自身，防御性空实现）
	}

	@Override
	public int getEnergyCapacity() {
		return 0;
	}

	@Override
	public void markNetworkDirty() {
		// 拓扑缓存由各成员自持，无需处理
	}
}
