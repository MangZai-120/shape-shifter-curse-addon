package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class PocketSpaceStorage {
	public static final String SCROLL_ID = "PocketSpaceId";
	private static final Logger LOGGER = LoggerFactory.getLogger("ssc_addon/pocket");
	private static final String REGISTRY_KEY = "ssc_addon_pocket_registry";

	private PocketSpaceStorage() {}

	private static PersistentStateManager states(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager();
	}

	public static Registry registry(MinecraftServer server) {
		return states(server).getOrCreate(Registry::read, Registry::new, REGISTRY_KEY);
	}

	/** 卷轴 UUID → 房间（未绑定或已消除返回 null）。 */
	public static Room findRoom(MinecraftServer server, UUID scrollId) {
		return scrollId == null ? null : registry(server).byScroll.get(scrollId);
	}

	public static Room roomAt(MinecraftServer server, int blockX, int blockZ) {
		int slot = PocketSpaceLayout.indexAt(blockX, blockZ);
		return slot < 0 ? null : registry(server).bySlot.get(slot);
	}

	/**
	 * 为卷轴绑定房间：已有绑定直接复用；否则占用离 (0,0) 最近的空闲槽位（被消除过的槽位优先复用），
	 * 卷轴 NBT 只写一个 UUID。返回 null 表示无法分配。
	 */
	public static UUID bind(MinecraftServer server, ItemStack scroll) {
		NbtCompound nbt = scroll.getOrCreateNbt();
		Registry registry = registry(server);
		if (nbt.containsUuid(SCROLL_ID)) {
			UUID existing = nbt.getUuid(SCROLL_ID);
			Room room = registry.byScroll.get(existing);
			if (room != null) return existing;
			// 卷轴曾绑定但房间已被消除（如丢弃超时后又被捡回）：按新卷轴重新分配
		}
		int slot = 0;
		while (slot < PocketSpaceLayout.MAX_ROOMS && registry.bySlot.containsKey(slot)) slot++;
		if (slot >= PocketSpaceLayout.MAX_ROOMS) return null;
		UUID scrollId = UUID.randomUUID();
		Room room = new Room(scrollId, new PocketSpaceLayout(slot, ScrollData.getLevel(scroll)), server.getOverworld().getTime());
		registry.bySlot.put(slot, room);
		registry.byScroll.put(scrollId, room);
		registry.markDirty();
		states(server).save();
		nbt.putUuid(SCROLL_ID, scrollId);
		LOGGER.info("卷轴绑定口袋空间：slot={} level={} scroll={}", slot, room.layout.level(), scrollId);
		return scrollId;
	}

	/** 消除房间登记（方块清空由 Manager 负责）；槽位释放后可被新卷轴复用。 */
	public static void unbind(MinecraftServer server, Room room) {
		Registry registry = registry(server);
		registry.bySlot.remove(room.layout.index());
		registry.byScroll.remove(room.scrollId);
		registry.markDirty();
		states(server).save();
		LOGGER.info("口袋空间已消除：slot={} scroll={}", room.layout.index(), room.scrollId);
	}

	public static Visit visit(ServerPlayerEntity player) {
		return states(player.getServer()).getOrCreate(Visit::read, Visit::new,
				"ssc_addon_pocket_visit_" + player.getUuid());
	}

	public static void save(MinecraftServer server) {
		states(server).save();
	}

	/** 全部房间登记（单一存档文件，避免分散小文件丢失导致的槽位重叠）。 */
	public static final class Registry extends PersistentState {
		final java.util.Map<Integer, Room> bySlot = new java.util.HashMap<>();
		final java.util.Map<UUID, Room> byScroll = new java.util.HashMap<>();

		public java.util.Collection<Room> rooms() {
			return java.util.Collections.unmodifiableCollection(bySlot.values());
		}

		static Registry read(NbtCompound nbt) {
			Registry registry = new Registry();
			net.minecraft.nbt.NbtList list = nbt.getList("Rooms", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound tag = list.getCompound(i);
				if (!tag.containsUuid("Scroll")) continue;
				try {
					Room room = new Room(tag.getUuid("Scroll"),
							new PocketSpaceLayout(tag.getInt("Slot"), tag.getInt("Level")), tag.getLong("LastSeen"));
					room.built = tag.getBoolean("Built");
					if (registry.bySlot.containsKey(room.layout.index())) continue;
					registry.bySlot.put(room.layout.index(), room);
					registry.byScroll.put(room.scrollId, room);
				} catch (IllegalArgumentException ignored) {
					// 跳过损坏条目，避免存档加载失败
				}
			}
			return registry;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
			for (Room room : bySlot.values()) {
				NbtCompound tag = new NbtCompound();
				tag.putUuid("Scroll", room.scrollId);
				tag.putInt("Slot", room.layout.index());
				tag.putInt("Level", room.layout.level());
				tag.putLong("LastSeen", room.lastSeen);
				tag.putBoolean("Built", room.built);
				list.add(tag);
			}
			nbt.put("Rooms", list);
			return nbt;
		}
	}

	public static final class Room {
		public final UUID scrollId;
		final PocketSpaceLayout layout;
		/** 卷轴最后一次被确认存在的世界时间（主世界 tick）。 */
		long lastSeen;
		boolean built;

		Room(UUID scrollId, PocketSpaceLayout layout, long lastSeen) {
			this.scrollId = scrollId;
			this.layout = layout;
			this.lastSeen = lastSeen;
		}

		public PocketSpaceLayout layout() { return layout; }
		public boolean initialized() { return built; }
		public long lastSeen() { return lastSeen; }

		public void finishGeneration() { built = true; }
		public void touch(long now) { if (now > lastSeen) lastSeen = now; }
	}

	public static final class Visit extends PersistentState {
		private UUID roomId;
		private RegistryKey<World> dimension;
		private Vec3d position;
		private float yaw;
		private float pitch;

		public UUID roomId() { return roomId; }
		public RegistryKey<World> dimension() { return dimension; }
		public Vec3d position() { return position; }
		public float yaw() { return yaw; }
		public float pitch() { return pitch; }
		public boolean active() { return roomId != null; }

		public void begin(UUID roomId, RegistryKey<World> dimension, Vec3d position, float yaw, float pitch) {
			this.roomId = roomId;
			this.dimension = dimension;
			this.position = position;
			this.yaw = yaw;
			this.pitch = pitch;
			markDirty();
		}

		public void clear() {
			roomId = null;
			dimension = null;
			position = null;
			markDirty();
		}

		static Visit read(NbtCompound nbt) {
			Visit visit = new Visit();
			Identifier dimensionId = Identifier.tryParse(nbt.getString("Dimension"));
			if (!nbt.containsUuid("Room") || dimensionId == null) return visit;
			Vec3d position = new Vec3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"));
			float yaw = nbt.getFloat("Yaw");
			float pitch = nbt.getFloat("Pitch");
			if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
					|| !Float.isFinite(yaw) || !Float.isFinite(pitch)
					|| Math.abs(position.x) > 30_000_000 || Math.abs(position.z) > 30_000_000
					|| Math.abs(position.y) > 20_000_000) return visit;
			visit.roomId = nbt.getUuid("Room");
			visit.dimension = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
			visit.position = position;
			visit.yaw = yaw;
			visit.pitch = pitch;
			return visit;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			if (!active()) return nbt;
			nbt.putUuid("Room", roomId);
			nbt.putString("Dimension", dimension.getValue().toString());
			nbt.putDouble("X", position.x);
			nbt.putDouble("Y", position.y);
			nbt.putDouble("Z", position.z);
			nbt.putFloat("Yaw", yaw);
			nbt.putFloat("Pitch", pitch);
			return nbt;
		}
	}
}