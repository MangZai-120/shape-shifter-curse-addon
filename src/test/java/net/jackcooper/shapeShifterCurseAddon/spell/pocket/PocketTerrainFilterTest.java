package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import net.jackcooper.shapeShifterCurseAddon.mixin.PocketTerrainWorkerAccessor;
import net.minecraft.datafixer.Schemas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.VersionedChunkStorage;

import java.nio.file.Files;
import java.util.Objects;

final class PocketTerrainFilterTest {
	static void verify() throws Exception {
		// Actual 1.20.1 empty full-chunk payload with coordinates and save time removed.
		NbtCompound clean;
		try (var stream = PocketTerrainFilterTest.class.getResourceAsStream("/pocket/empty-full.nbt")) {
			clean = NbtIo.readCompressed(Objects.requireNonNull(stream));
		}
		ChunkPos outside = new ChunkPos(4, -3);
		clean.putInt("xPos", outside.x);
		clean.putInt("zPos", outside.z);
		clean.putLong("LastUpdate", 13);
		check(PocketTerrainFilter.isRegenerable(outside, clean), "pristine outside chunk");
		var inside = clean.copy();
		inside.putInt("xPos", -2);
		inside.putInt("zPos", -2);
		check(!PocketTerrainFilter.isRegenerable(new ChunkPos(-2, -2), inside), "portal-side room corner");
		var inhabited = clean.copy();
		inhabited.putLong("InhabitedTime", 1);
		check(!PocketTerrainFilter.isRegenerable(outside, inhabited), "inhabited chunk");
		var light = clean.copy();
		light.getList("sections", NbtElement.COMPOUND_TYPE).getCompound(0).putByteArray("BlockLight", new byte[2048]);
		check(!PocketTerrainFilter.isRegenerable(outside, light), "non-template light");
		var modded = clean.copy();
		modded.putString("example:state", "persisted");
		check(!PocketTerrainFilter.isRegenerable(outside, modded), "unknown root tag");

		var temp = Files.createTempDirectory("ssca-pocket-region-");
		try (var storage = new VersionedChunkStorage(temp, Schemas.getFixer(), false)) {
			var worker = (PocketTerrainWorkerAccessor) storage.getWorker();
			((PocketTerrainFilter.TerrainStorage) (Object) worker.sscAddon$getStorage()).sscAddon$enablePocketTerrainFilter();
			storage.setNbt(outside, clean);
			storage.completeAll();
			check(storage.getNbt(outside).join().isEmpty(), "new empty record skipped");
			storage.setNbt(outside, modded);
			storage.completeAll();
			check(storage.getNbt(outside).join().orElseThrow().equals(modded), "outside mod data saved");
			storage.setNbt(outside, clean);
			storage.completeAll();
			check(storage.getNbt(outside).join().orElseThrow().equals(clean), "existing record updated after last mod tag removed");
		} finally {
			Files.deleteIfExists(temp.resolve("r.0.-1.mca"));
			Files.deleteIfExists(temp);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
