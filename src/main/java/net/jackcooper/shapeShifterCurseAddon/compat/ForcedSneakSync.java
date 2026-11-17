package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.util.WeakEntityStateMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.networking.ModPackets;

/** Called once per online player from SSCA's existing end-of-server-tick handler. */
public final class ForcedSneakSync {
    private static final WeakEntityStateMap<ServerPlayerEntity, State> STATES = new WeakEntityStateMap<>();
    private record State(boolean forced, int sentAt, RegistryKey<World> world) {}
    private ForcedSneakSync() {}

    public static void tick(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ModPackets.SYNC_FORCE_SNEAK_STATE)) return;
        boolean forced = SscForceSneak.required(player);
        var world = player.getWorld().getRegistryKey();
        State last = STATES.getOrDefault(player, null);
        if (last == null || last.forced() != forced || last.world() != world || player.age - last.sentAt() >= 100) {
            var packet = PacketByteBufs.create();
            packet.writeBoolean(forced);
            ServerPlayNetworking.send(player, ModPackets.SYNC_FORCE_SNEAK_STATE, packet);
            STATES.put(player, new State(forced, player.age, world));
        }
    }

    public static void forget(ServerPlayerEntity player) { STATES.remove(player); }
    public static void clear() { STATES.clear(); }
}
