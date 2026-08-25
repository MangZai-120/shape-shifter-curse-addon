package net.jackcooper.shapeShifterCurseAddon.energy;

/**
 * 能量网络成员（jackcooper）：能存储并导通能量的方块实体（能量汲取器、能量储罐）实现本接口。
 * <p>能量以「共享池」语义在相邻成员间流动：网络总能量 = 各成员之和，总上限 = 各成员上限之和。
 * 拓扑仅在成员放置/破坏时经 {@link EnergyNetwork#broadcastInvalidate} 事件驱动刷新，绝不每 tick 高频扫描。
 */
public interface EnergyNetworkMember {

	/** 当前存储的能量。 */
	int getStoredEnergy();

	/** 直接设置存储能量（实现方须自行 markDirty 持久化）。 */
	void setStoredEnergy(int value);

	/** 本成员的能量上限。 */
	int getEnergyCapacity();

	/** 标记网络缓存失效（下次访问时重建）。 */
	void markNetworkDirty();
}
