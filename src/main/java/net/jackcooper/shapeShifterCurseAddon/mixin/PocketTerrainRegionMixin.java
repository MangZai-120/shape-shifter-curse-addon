package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketTerrainFilter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegionBasedStorage.class)
public abstract class PocketTerrainRegionMixin implements PocketTerrainFilter.TerrainStorage {
	@Unique private volatile boolean sscAddon$filterPocketTerrain;

	@Override
	public void sscAddon$enablePocketTerrainFilter() {
		sscAddon$filterPocketTerrain = true;
	}

	@Inject(method = "write", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/storage/RegionFile;getChunkOutputStream(Lnet/minecraft/util/math/ChunkPos;)Ljava/io/DataOutputStream;"),
			cancellable = true)
	private void sscAddon$skipNewVoid(ChunkPos pos, NbtCompound data, CallbackInfo ci, @Local RegionFile region) {
		// The terrain I/O worker serializes writes. Its open region header includes all earlier writes.
		if (sscAddon$filterPocketTerrain && !region.hasChunk(pos) && PocketTerrainFilter.isRegenerable(pos, data)) {
			ci.cancel();
		}
	}
}
