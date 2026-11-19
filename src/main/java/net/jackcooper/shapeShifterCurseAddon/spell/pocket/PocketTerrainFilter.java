package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.jackcooper.shapeShifterCurseAddon.mixin.PocketTerrainWorkerAccessor;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;

import java.util.Set;

/** Only the 1.20.1 empty flat pocket generator can reproduce these terrain records. */
public final class PocketTerrainFilter {
	private static final int DATA_VERSION = 3465;
	private static final Set<String> HEIGHTMAPS = Set.of("WORLD_SURFACE_WG", "OCEAN_FLOOR_WG",
			"WORLD_SURFACE", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES");

	// Build the expected payloads once. Comparing complete compounds also protects unknown mod tags.
	private static final NbtCompound EARLY = emptyPayload("minecraft:plains", false);
	private static final NbtCompound GENERATED = emptyPayload("minecraft:the_void", false);
	private static final NbtCompound FULL = emptyPayload("minecraft:the_void", true);

	private PocketTerrainFilter() {}

	public interface TerrainStorage {
		void sscAddon$enablePocketTerrainFilter();
	}

	public static void onWorldLoad(ServerWorld world) {
		if (!world.getRegistryKey().equals(PocketSpaceManager.WORLD_KEY) || world.getBottomY() != 0 || world.getHeight() != 256) return;
		var manager = world.getChunkManager();
		if (!(manager.getChunkGenerator() instanceof FlatChunkGenerator flat) || flat.getClass() != FlatChunkGenerator.class) return;
		var ops = RegistryOps.of(JsonOps.INSTANCE, world.getRegistryManager());
		var settings = FlatChunkGeneratorConfig.CODEC.encodeStart(ops, flat.getConfig()).result();
		if (settings.isEmpty() || !(settings.get() instanceof JsonObject config)) return;
		var expected = new JsonObject();
		expected.addProperty("biome", "minecraft:the_void");
		expected.add("layers", new JsonArray());
		expected.add("structure_overrides", new JsonArray());
		// The codec may omit either default value.
		if (config.has("features")) expected.addProperty("features", false);
		if (config.has("lakes")) expected.addProperty("lakes", false);
		if (!expected.equals(config)) return;

		var worker = (PocketTerrainWorkerAccessor) manager.threadedAnvilChunkStorage.getWorker();
		((TerrainStorage) (Object) worker.sscAddon$getStorage()).sscAddon$enablePocketTerrainFilter();
	}

	public static boolean isRegenerable(ChunkPos pos, NbtCompound data) {
		int slot = PocketSpaceLayout.indexAt(pos.getStartX(), pos.getStartZ());
		if (slot >= 0 && pos.getEndX() >= PocketSpaceLayout.slotBoundsMinX(slot)
				&& pos.getStartX() <= PocketSpaceLayout.slotBoundsMaxX(slot)
				&& pos.getEndZ() >= PocketSpaceLayout.slotBoundsMinZ(slot)
				&& pos.getStartZ() <= PocketSpaceLayout.slotBoundsMaxZ(slot)) return false;

		NbtCompound expected = switch (data.getString("Status")) {
			case "minecraft:structure_starts", "minecraft:structure_references" -> EARLY;
			case "minecraft:biomes", "minecraft:noise", "minecraft:surface", "minecraft:carvers",
					"minecraft:features", "minecraft:initialize_light", "minecraft:light", "minecraft:spawn" -> GENERATED;
			case "minecraft:full" -> FULL;
			default -> null;
		};
		// Five fields vary independently of the immutable payload: Status, xPos, zPos, LastUpdate and Heightmaps.
		if (expected == null || data.getSize() != expected.getSize() + 5
				|| !data.contains("xPos", NbtElement.INT_TYPE) || data.getInt("xPos") != pos.x
				|| !data.contains("zPos", NbtElement.INT_TYPE) || data.getInt("zPos") != pos.z
				|| !data.contains("LastUpdate", NbtElement.LONG_TYPE)
				|| !(data.get("Heightmaps") instanceof NbtCompound maps) || !emptyHeightmaps(maps)) return false;
		for (String key : expected.getKeys()) {
			if (!expected.get(key).equals(data.get(key))) return false;
		}
		return true;
	}

	private static boolean emptyHeightmaps(NbtCompound maps) {
		for (String name : maps.getKeys()) {
			if (!HEIGHTMAPS.contains(name) || !(maps.get(name) instanceof NbtLongArray values) || values.size() != 37) return false;
			for (long word : values.getLongArray()) if (word != 0) return false;
		}
		return true;
	}

	private static NbtCompound emptyPayload(String biome, boolean full) {
		NbtCompound tag = new NbtCompound();
		tag.putInt("DataVersion", DATA_VERSION);
		tag.putInt("yPos", 0);
		tag.putLong("InhabitedTime", 0);
		NbtCompound structures = new NbtCompound();
		structures.put("starts", new NbtCompound());
		structures.put("References", new NbtCompound());
		tag.put("structures", structures);
		tag.put("block_entities", new NbtList());
		tag.put("block_ticks", new NbtList());
		tag.put("fluid_ticks", new NbtList());
		if (full) {
			tag.putBoolean("isLightOn", true);
		} else {
			tag.put("entities", new NbtList());
			tag.put("CarvingMasks", new NbtCompound());
		}
		NbtCompound air = new NbtCompound();
		air.putString("Name", "minecraft:air");
		NbtList sections = new NbtList();
		NbtList pending = new NbtList();
		for (int y = 0; y < 16; y++) {
			NbtCompound section = new NbtCompound();
			section.putByte("Y", (byte) y);
			section.put("biomes", palette(NbtString.of(biome)));
			section.put("block_states", palette(air));
			sections.add(section);
			pending.add(new NbtList());
		}
		tag.put("sections", sections);
		tag.put("PostProcessing", pending);
		return tag;
	}

	private static NbtCompound palette(NbtElement onlyEntry) {
		NbtList entries = new NbtList();
		entries.add(onlyEntry);
		NbtCompound container = new NbtCompound();
		container.put("palette", entries);
		return container;
	}
}
