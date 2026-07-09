package net.onixary.shapeShifterCurseFabric.ssc_addon.story;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 「潮汐之灵」剧情链的持久化存储（attach 到主世界 dat），跨重连 / 死亡重生 / 重启服务器均保留。
 * <ul>
 *     <li>tippedPlayers：已收到过"潮汐之灵"低语提示的玩家（每名玩家仅提示一次）。</li>
 * </ul>
 * <p>阿澪变身后为永久形态（不自动变回），故无需跟踪"剧情态玩家"集合（与月痕之力 red 不同）。
 */
public final class TideSpiritStoryState extends PersistentState {
	public static final String KEY = "ssc_addon_tide_spirit_story";

	public final Set<UUID> tippedPlayers = new HashSet<>();

	public static TideSpiritStoryState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
				TideSpiritStoryState::fromNbt,
				TideSpiritStoryState::new,
				KEY);
	}

	public static TideSpiritStoryState fromNbt(NbtCompound nbt) {
		TideSpiritStoryState s = new TideSpiritStoryState();
		readUuidList(nbt, "tipped", s.tippedPlayers);
		return s;
	}

	private static void readUuidList(NbtCompound nbt, String key, Set<UUID> out) {
		NbtList list = nbt.getList(key, NbtElement.STRING_TYPE);
		for (NbtElement e : list) {
			try {
				out.add(UUID.fromString(e.asString()));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.put("tipped", writeUuidList(tippedPlayers));
		return nbt;
	}

	private static NbtList writeUuidList(Set<UUID> set) {
		NbtList list = new NbtList();
		for (UUID id : set) {
			list.add(NbtString.of(id.toString()));
		}
		return list;
	}
}
