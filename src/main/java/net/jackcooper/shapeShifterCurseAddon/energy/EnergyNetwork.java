package net.jackcooper.shapeShifterCurseAddon.energy;

import net.minecraft.block.entity.BlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyExtractorBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyStorageTankBlockEntity;
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
		// 能量变化后：先把各储罐能量均分，再刷新档位显示（事件驱动）
		equalizeTanks(members);
		refreshTankDisplays(members);
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
		// 能量变化后：先把各储罐能量均分，再刷新档位显示（事件驱动）
		equalizeTanks(members);
		refreshTankDisplays(members);
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
		BlockPos firstMember = null;
		int guard = 0;
		int limit = MAX_NETWORK * 7;
		while (!queue.isEmpty() && guard++ < limit) {
			BlockPos p = queue.poll();
			BlockEntity be = world.getBlockEntity(p);
			if (be instanceof EnergyNetworkMember member) {
				if (firstMember == null) {
					firstMember = p;
				}
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
		// 拓扑变化后：重建幸存网络并均分储罐能量、刷新档位显示（放置/破坏任一成员都会走到这里）
		if (firstMember != null) {
			BlockEntity fbe = world.getBlockEntity(firstMember);
			List<EnergyNetworkMember> net = null;
			if (fbe instanceof EnergyStorageTankBlockEntity tank) {
				net = tank.getNetwork();
			} else if (fbe instanceof EnergyExtractorBlockEntity extractor) {
				net = extractor.getNetwork();
			}
			if (net != null) {
				equalizeTanks(net);
				refreshTankDisplays(net);
			}
		}
	}

	/**
	 * 事件驱动刷新网络内所有能量储罐的液面显示（仅服务端生效）。
	 * 所有储罐统一显示所在网络的总能量/总容量比例；比例变化超过阈值时经 BE 数据包同步客户端
	 * （见 {@link EnergyStorageTankBlockEntity#syncFillRatio}，BER 无级液面渲染）。
	 */
	public static void refreshTankDisplays(List<EnergyNetworkMember> members) {
		if (members == null || members.isEmpty()) {
			return;
		}
		int cap = getTotalCapacity(members);
		float ratio = cap > 0 ? (float) getTotalEnergy(members) / cap : 0f;
		for (EnergyNetworkMember m : members) {
			if (!(m instanceof EnergyStorageTankBlockEntity tank)) {
				continue;
			}
			World w = tank.getWorld();
			if (w == null || w.isClient) {
				continue;
			}
			tank.syncFillRatio(ratio);
		}
	}

	/** 收集成员列表中的全部储罐（汲取器缓冲不参与均分）。 */
	private static List<EnergyStorageTankBlockEntity> collectTanks(List<EnergyNetworkMember> members) {
		List<EnergyStorageTankBlockEntity> tanks = new ArrayList<>();
		for (EnergyNetworkMember m : members) {
			if (m instanceof EnergyStorageTankBlockEntity tank && tank.getWorld() != null && !tank.getWorld().isClient) {
				tanks.add(tank);
			}
		}
		return tanks;
	}

	/**
	 * 把网络内所有储罐的能量均分（仅服务端）。
	 * 总量取各储罐之和，按「每罐 total/n、前 rem 罐 +1」分配，结果相等或最多相差 1。
	 */
	public static void equalizeTanks(List<EnergyNetworkMember> members) {
		equalizeTanksWithBonus(members, 0);
	}

	/** 被破坏储罐的能量回归网络：并入储罐总量后整体均分（须存在至少一个储罐，否则能量丢失）。 */
	public static void donateBrokenTankEnergy(List<EnergyNetworkMember> members, int amount) {
		equalizeTanksWithBonus(members, amount);
	}

	/**
	 * 破坏储罐后调用：把 {@code amount} 能量就近交给任一相邻储罐所在网络均分并刷新显示；
	 * 周围没有任何相邻储罐时返回 false，能量按设计丢失。
	 */
	public static boolean transferBrokenTankEnergy(World world, BlockPos origin, int amount) {
		if (amount <= 0 || world.isClient) {
			return false;
		}
		for (Direction dir : Direction.values()) {
			BlockEntity be = world.getBlockEntity(origin.offset(dir));
			if (be instanceof EnergyStorageTankBlockEntity tank) {
				List<EnergyNetworkMember> net = tank.getNetwork();
				donateBrokenTankEnergy(net, amount);
				refreshTankDisplays(net);
				return true;
			}
		}
		return false;
	}

	private static void equalizeTanksWithBonus(List<EnergyNetworkMember> members, int bonus) {
		List<EnergyStorageTankBlockEntity> tanks = collectTanks(members);
		int n = tanks.size();
		if (n == 0 || (n == 1 && bonus == 0)) {
			return;
		}
		long total = bonus;
		for (EnergyStorageTankBlockEntity t : tanks) {
			total += t.getStoredEnergy();
		}
		int per = (int) (total / n);
		int rem = (int) (total % n);
		for (int i = 0; i < n; i++) {
			EnergyStorageTankBlockEntity t = tanks.get(i);
			// 钳制到单罐上限，防止异常超总量时溢出崩溃
			int target = Math.min(t.getEnergyCapacity(), per + (i < rem ? 1 : 0));
			if (t.getStoredEnergy() != target) {
				t.setStoredEnergy(target);
			}
		}
	}
}
