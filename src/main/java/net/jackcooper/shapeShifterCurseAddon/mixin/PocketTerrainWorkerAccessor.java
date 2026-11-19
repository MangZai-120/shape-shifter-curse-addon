package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StorageIoWorker.class)
public interface PocketTerrainWorkerAccessor {
	@Accessor("storage")
	RegionBasedStorage sscAddon$getStorage();
}
