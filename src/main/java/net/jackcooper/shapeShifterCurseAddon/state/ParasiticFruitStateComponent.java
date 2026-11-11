package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.jackcooper.shapeShifterCurseAddon.ability.fruit.FruitSelection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Only selection persists. Charging is a server-owned, transient connection/world state. */
public final class ParasiticFruitStateComponent implements AutoSyncedComponent {
    private final PlayerEntity player;
    private FruitSelection selection = FruitSelection.AUTO;
    private boolean charging;
    private int chargeTicks;
    private long chargeAnchor;
    private Identifier observedForm;
    private boolean formObserved;
    public ParasiticFruitStateComponent(PlayerEntity player) { this.player = player; }
    public FruitSelection selection() { return selection; }
    public void selection(FruitSelection value) { selection = value == null ? FruitSelection.AUTO : value; }
    public void charge(boolean active, int ticks) {
        charging = active; chargeTicks = Math.max(0, Math.min(30, ticks));
        chargeAnchor = player.getWorld().getTime();
    }
    public boolean charging() { return charging; }
    public double chargeFraction() {
        if (!charging) return -1;
        long elapsed = player.getWorld().isClient ? Math.max(0, player.getWorld().getTime() - chargeAnchor) : 0;
        return Math.min(1, (chargeTicks + elapsed) / 30.0);
    }
    public boolean observeForm(Identifier current) {
        boolean changed = formObserved && !java.util.Objects.equals(observedForm, current);
        observedForm = current; formObserved = true; return changed;
    }
    @Override public void readFromNbt(NbtCompound nbt) {
        selection = FruitSelection.fromId(nbt.getInt("selection"));
        charging = false; chargeTicks = 0; formObserved = false; observedForm = null;
    }
    @Override public void writeToNbt(NbtCompound nbt) { nbt.putInt("selection", selection.ordinal()); }
    @Override public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(selection.ordinal()); buf.writeBoolean(charging); buf.writeVarInt(chargeTicks);
    }
    @Override public void applySyncPacket(PacketByteBuf buf) {
        selection = FruitSelection.fromId(buf.readVarInt()); charge(buf.readBoolean(), buf.readVarInt());
    }
}
