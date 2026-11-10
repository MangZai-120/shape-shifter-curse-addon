package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/** Placement deadlines survive both stale scheduled ticks and chunk/server reloads. */
final class WebMembraneLifetimes extends PersistentState {
    static final String ID = "ssc_addon_web_membrane_lifetimes";
    private final Map<Long, Long> deadlines = new HashMap<>();

    static WebMembraneLifetimes get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(WebMembraneLifetimes::read,
                WebMembraneLifetimes::new, ID);
    }

    static void placed(ServerWorld world, BlockPos pos, int lifetime) {
        var state = get(world);
        state.deadlines.put(pos.asLong(), world.getTime() + lifetime);
        state.markDirty();
    }

    static long remaining(ServerWorld world, BlockPos pos) {
        return Math.max(0L, get(world).deadlines.getOrDefault(pos.asLong(), 0L) - world.getTime());
    }

    static void removed(ServerWorld world, BlockPos pos) {
        var state = get(world);
        if (state.deadlines.remove(pos.asLong()) != null) state.markDirty();
    }

    static WebMembraneLifetimes read(NbtCompound nbt) {
        var state = new WebMembraneLifetimes();
        var entries = nbt.getList("Entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            state.deadlines.put(entry.getLong("Pos"), entry.getLong("Until"));
        }
        return state;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        var entries = new NbtList();
        deadlines.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(deadline -> {
            var entry = new NbtCompound();
            entry.putLong("Pos", deadline.getKey());
            entry.putLong("Until", deadline.getValue());
            entries.add(entry);
        });
        nbt.put("Entries", entries);
        return nbt;
    }
}
