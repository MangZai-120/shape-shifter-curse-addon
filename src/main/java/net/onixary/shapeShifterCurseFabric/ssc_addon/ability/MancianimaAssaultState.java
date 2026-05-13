package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 契灵敲钟袭击的"每自然天 1 次"冷却的持久化存储（attach 到主世界 dat）。
 * 退出存档/重启服务器后仍然生效。
 */
public final class MancianimaAssaultState extends PersistentState {
	public static final String KEY = "ssc_addon_mancianima_assault";
	public final Map<UUID, Long> lastRoll = new HashMap<>();

	public static MancianimaAssaultState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
				MancianimaAssaultState::fromNbt,
				MancianimaAssaultState::new,
				KEY);
	}

	public static MancianimaAssaultState fromNbt(NbtCompound nbt) {
		MancianimaAssaultState s = new MancianimaAssaultState();
		NbtList list = nbt.getList("last_roll", NbtElement.COMPOUND_TYPE);
		for (NbtElement e : list) {
			NbtCompound c = (NbtCompound) e;
			s.lastRoll.put(c.getUuid("uuid"), c.getLong("time"));
		}
		return s;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> e : lastRoll.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putUuid("uuid", e.getKey());
			c.putLong("time", e.getValue());
			list.add(c);
		}
		nbt.put("last_roll", list);
		return nbt;
	}
}
