package net.jackcooper.shapeShifterCurseAddon.energy;

/**
 * 能量网络消费者（jackcooper）：消耗能量但自身不存储/不导通的方块实体（能量装瓶器）实现本接口。
 * <p>消费者不参与能量网络的拓扑遍历，但当相邻网络发生放置/破坏变动时会被 {@link EnergyNetwork#broadcastInvalidate}
 * 标记缓存失效，从而重新定位可用的相邻能量源。
 */
public interface EnergyNetworkConsumer {

	/** 标记（相邻网络的）缓存失效，下次访问时重新定位。 */
	void markNetworkDirty();
}
