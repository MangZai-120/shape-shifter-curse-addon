package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Restores only gravity temporarily owned by an SSCA movement skill. Server thread only. */
public final class PlayerGravityLease {
    private static final Map<UUID, Lease> ACTIVE = new HashMap<>();
    private record Lease(ServerPlayerEntity player, boolean previous, Set<String> owners) {}

    private PlayerGravityLease() {}

    public static void acquire(ServerPlayerEntity player, String owner) {
        Lease lease = ACTIVE.get(player.getUuid());
        if (lease == null || lease.player != player) {
            lease = new Lease(player, player.hasNoGravity(), new HashSet<>());
            ACTIVE.put(player.getUuid(), lease);
        }
        lease.owners.add(owner);
        player.setNoGravity(true);
    }

    public static void release(ServerPlayerEntity player, String owner) {
        Lease lease = ACTIVE.get(player.getUuid());
        if (lease == null || lease.player != player || !lease.owners.remove(owner)) return;
        if (!lease.owners.isEmpty()) return;
        player.setNoGravity(lease.previous);
        ACTIVE.remove(player.getUuid());
    }

    /** Autosaving an in-flight player must not persist a temporary movement flag. */
    public static void writePersistentGravity(ServerPlayerEntity player, NbtCompound nbt) {
        Lease lease = ACTIVE.get(player.getUuid());
        if (lease != null && lease.player == player) {
            if (lease.previous) nbt.putBoolean("NoGravity", true);
            else nbt.remove("NoGravity");
        }
    }
}
