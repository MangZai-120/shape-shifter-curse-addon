package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;

/** 道具固定的独立来源；不复用、更不清除 SSC 的本能锁。 */
public final class InstinctAnchorComponent implements AutoSyncedComponent {
    private boolean locked;

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    @Override
    public void readFromNbt(NbtCompound nbt) { locked = nbt.getBoolean("locked"); }

    @Override
    public void writeToNbt(NbtCompound nbt) { nbt.putBoolean("locked", locked); }
}
