package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 寒棘狐「冰刺」环绕冰锥的持久化存储（attach 到主世界 dat）：
 * 玩家退出时记录身上各槽位冰锥的已存在 tick 数，重进后按记录重建冰锥并延续存在时间。
 * 飞行中的冰锥不持久化（退出即清空）。
 */
public final class FrostSpikeState extends PersistentState {
	public static final String KEY = "ssc_addon_frost_spike";

	/** 玩家 UUID → 各槽位（0-4）已存在 tick 数（-1 = 该槽无冰锥）。 */
	public final Map<UUID, int[]> thorns = new HashMap<>();

	public static FrostSpikeState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
				FrostSpikeState::fromNbt,
				FrostSpikeState::new,
				KEY);
	}

	public static FrostSpikeState fromNbt(NbtCompound nbt) {
		FrostSpikeState s = new FrostSpikeState();
		NbtList players = nbt.getList("players", NbtElement.COMPOUND_TYPE);
		for (NbtElement e : players) {
			NbtCompound p = (NbtCompound) e;
			try {
				UUID uuid = UUID.fromString(p.getString("uuid"));
				int[] ticks = new int[5];
				for (int i = 0; i < 5; i++) {
					ticks[i] = p.contains("t" + i) ? p.getInt("t" + i) : -1;
				}
				s.thorns.put(uuid, ticks);
			} catch (IllegalArgumentException ignored) {
				// 跳过非法 UUID，避免存档损坏导致加载失败
			}
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList players = new NbtList();
		for (Map.Entry<UUID, int[]> en : thorns.entrySet()) {
			NbtCompound p = new NbtCompound();
			p.putString("uuid", en.getKey().toString());
			int[] ticks = en.getValue();
			for (int i = 0; i < 5 && i < ticks.length; i++) {
				if (ticks[i] >= 0) p.putInt("t" + i, ticks[i]);
			}
			players.add(p);
		}
		nbt.put("players", players);
		return nbt;
	}
}
