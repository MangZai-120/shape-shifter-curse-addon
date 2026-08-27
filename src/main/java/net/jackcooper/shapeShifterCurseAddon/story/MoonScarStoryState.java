package net.jackcooper.shapeShifterCurseAddon.story;

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
 * 「月痕之力」剧情链的持久化存储（attach 到主世界 dat），跨重连 / 死亡重生 / 重启服务器均保留。
 * <ul>
 *     <li>storyRedPlayers：当前处于"剧情触发的 red 形态"的玩家，可用月髓环随时免费变回 sp 使魔。</li>
 *     <li>tippedPlayers：已收到过"月痕之力"低语提示的玩家（每名玩家仅提示一次）。</li>
 * </ul>
 */
public final class MoonScarStoryState extends PersistentState {
	public static final String KEY = "ssc_addon_moon_scar_story";

	public final Set<UUID> storyRedPlayers = new HashSet<>();
	public final Set<UUID> tippedPlayers = new HashSet<>();

	public static MoonScarStoryState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
				MoonScarStoryState::fromNbt,
				MoonScarStoryState::new,
				KEY);
	}

	public static MoonScarStoryState fromNbt(NbtCompound nbt) {
		MoonScarStoryState s = new MoonScarStoryState();
		readUuidList(nbt, "story_red", s.storyRedPlayers);
		readUuidList(nbt, "tipped", s.tippedPlayers);
		return s;
	}

	private static void readUuidList(NbtCompound nbt, String key, Set<UUID> out) {
		NbtList list = nbt.getList(key, NbtElement.STRING_TYPE);
		for (NbtElement e : list) {
			try {
				out.add(UUID.fromString(e.asString()));
			} catch (IllegalArgumentException ignored) {
				// 跳过非法 UUID 字符串，避免存档损坏导致加载失败
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.put("story_red", writeUuidList(storyRedPlayers));
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
