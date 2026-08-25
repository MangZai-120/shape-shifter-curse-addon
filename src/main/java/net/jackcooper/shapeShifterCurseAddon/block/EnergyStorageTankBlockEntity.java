package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetworkMember;

import java.util.List;

/**
 * 能量储罐方块实体（jackcooper）：被动存储节点，作为 {@link EnergyNetworkMember} 加入能量网络。
 * <p>自身不产能、不消耗，仅提供 {@link #MAX_ENERGY} 的存储上限；相邻储罐/汲取器构成同一共享池，
 * 多个储罐相邻即可叠加网络总上限。能量由汲取器注入、被装瓶器抽取，储罐本身无需 tick。
 */
public class EnergyStorageTankBlockEntity extends BlockEntity implements EnergyNetworkMember {

	/** 单个储罐的能量上限。 */
	public static final int MAX_ENERGY = 1000;

	private int energy = 0;

	private List<EnergyNetworkMember> networkCache;
	private boolean networkDirty = true;

	public EnergyStorageTankBlockEntity(BlockPos pos, BlockState state) {
		super(RegAddonBlockEntities.ENERGY_STORAGE_TANK_BE, pos, state);
	}

	// ==================== 能量网络成员 ====================

	@Override
	public int getStoredEnergy() {
		return energy;
	}

	@Override
	public void setStoredEnergy(int value) {
		energy = Math.max(0, Math.min(MAX_ENERGY, value));
		markDirty();
	}

	@Override
	public int getEnergyCapacity() {
		return MAX_ENERGY;
	}

	@Override
	public void markNetworkDirty() {
		networkDirty = true;
	}

	/** 获取所在网络成员（缓存，脏时重建）。 */
	public List<EnergyNetworkMember> getNetwork() {
		if (networkDirty || networkCache == null) {
			networkCache = EnergyNetwork.collect(this.world, this.pos);
			networkDirty = false;
		}
		return networkCache;
	}

	// ==================== NBT 持久化 ====================

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt("Energy", energy);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		energy = nbt.getInt("Energy");
	}
}
