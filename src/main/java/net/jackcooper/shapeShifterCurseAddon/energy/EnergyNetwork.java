package net.jackcooper.shapeShifterCurseAddon.energy;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 能量网络工具（jackcooper）：负责连通分量收集、共享池的注入/抽取、以及事件驱动的缓存失效广播。
 * <p>全部为静态方法；能量在相邻 {@link EnergyNetworkMember} 之间按「共享池」语义流动。
 * 拓扑遍历仅在成员放置/破坏时触发（见 {@link #broadcastInvalidate}），运行期不做每 tick 高频扫描。
 */
public final class EnergyNetwork {

	private EnergyNetwork() {}

	/** 单个网络最多遍历的成员数上限，防止超大网络造成卡顿。 */
	public static final int MAX_NETWORK = 256;

	/**
	 * 从 {@code start} 起经 6 邻接方向洪泛收集同一连通网络内的全部能量成员。
	 * 若 {@code start} 本身不是成员则返回空列表。
	 */
	public static List<EnergyNetworkMember> collect(World world, BlockPos start) {
		List<EnergyNetworkMember> members = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.toImmutable());
		visited.add(start.toImmutable());
		while (!queue.isEmpty() && members.size() < MAX_NETWORK) {
			BlockPos p = queue.poll();
			BlockEntity be = world.getBlockEntity(p);
			if (be instanceof EnergyNetworkMember member) {
				members.add(member);
				for (Direction dir : Direction.values()) {
					BlockPos np = p.offset(dir);
					if (visited.add(np)) {
						queue.add(np);
					}
				}
			}
		}
		return members;
	}

	/** 网络当前总能量。 */
	public static int getTotalEnergy(List<EnergyNetworkMember> members) {
		int sum = 0;
		for (EnergyNetworkMember m : members) {
			sum += m.getStoredEnergy();
		}
		return sum;
	}

	/** 网络总能量上限。 */
	public static int getTotalCapacity(List<EnergyNetworkMember> members) {
		int sum = 0;
		for (EnergyNetworkMember m : members) {
			sum += m.getEnergyCapacity();
		}
		return sum;
	}

	/** 向网络注入 {@code amount} 点能量（按成员顺序填满），返回实际接受量。 */
	public static int insert(List<EnergyNetworkMember> members, int amount) {
		int remaining = amount;
		for (EnergyNetworkMember m : members) {
			if (remaining <= 0) {
				break;
			}
			int space = m.getEnergyCapacity() - m.getStoredEnergy();
			if (space > 0) {
				int add = Math.min(space, remaining);
				m.setStoredEnergy(m.getStoredEnergy() + add);
				remaining -= add;
			}
		}
		return amount - remaining;
	}

	/** 从网络抽取 {@code amount} 点能量（按成员顺序排空），返回实际抽取量。 */
	public static int extract(List<EnergyNetworkMember> members, int amount) {
		int remaining = amount;
		for (EnergyNetworkMember m : members) {
			if (remaining <= 0) {
				break;
			}
			int avail = m.getStoredEnergy();
			if (avail > 0) {
				int take = Math.min(avail, remaining);
				m.setStoredEnergy(avail - take);
				remaining -= take;
			}
		}
		return amount - remaining;
	}

	/**
	 * 事件驱动的缓存失效广播：某能量成员在 {@code origin} 放置或破坏后调用。
	 * 从 {@code origin} 及其 6 邻接洪泛遍历所有仍存在的成员，逐个 {@link EnergyNetworkMember#markNetworkDirty()}；
	 * 同时把与这些成员相邻的能量消费者（{@link EnergyNetworkConsumer}，如装瓶器）一并标脏，使其重新定位能量源。
	 */
	public static void broadcastInvalidate(World world, BlockPos origin) {
		if (world.isClient) {
			return;
		}
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		// origin 自身也入队（放置成员时 origin 是成员，破坏时 origin 已空会被跳过）
		visited.add(origin.toImmutable());
		queue.add(origin.toImmutable());
		for (Direction dir : Direction.values()) {
			BlockPos np = origin.offset(dir);
			if (visited.add(np)) {
				queue.add(np);
			}
			// 显式标脏 origin 邻接的消费者：覆盖「消费者唯一相邻能量源被放置/移除」的场景
			BlockEntity nbe = world.getBlockEntity(np);
			if (nbe instanceof EnergyNetworkConsumer consumer) {
				consumer.markNetworkDirty();
			}
		}
		int guard = 0;
		int limit = MAX_NETWORK * 7;
		while (!queue.isEmpty() && guard++ < limit) {
			BlockPos p = queue.poll();
			BlockEntity be = world.getBlockEntity(p);
			if (be instanceof EnergyNetworkMember member) {
				member.markNetworkDirty();
				// 顺带标脏与该成员相邻的消费者（装瓶器）
				for (Direction dir : Direction.values()) {
					BlockPos np = p.offset(dir);
					BlockEntity nbe = world.getBlockEntity(np);
					if (nbe instanceof EnergyNetworkConsumer consumer) {
						consumer.markNetworkDirty();
					}
					if (visited.add(np)) {
						queue.add(np);
					}
				}
			}
		}
	}
}
