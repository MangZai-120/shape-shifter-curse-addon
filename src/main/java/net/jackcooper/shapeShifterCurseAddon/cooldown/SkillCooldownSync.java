package net.jackcooper.shapeShifterCurseAddon.cooldown;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;
import java.util.*;

/** Owner-only snapshots: state transitions immediately, running clocks every second. */
public final class SkillCooldownSync {
    public static final Identifier ID = new Identifier("my_addon", "skill_cooldowns_v1");
    private static final Map<UUID, Snapshot> SENT = new HashMap<>();
    private record Stamp(String skill, int phase, long end, int total) {}
    private record Snapshot(Object player, Object world, Object connection, List<Stamp> stamps) {}
    public static void init() {
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) -> server.execute(() -> {
            var online = server.getPlayerManager().getPlayer(handler.player.getUuid());
            if (online == null || online.networkHandler == handler) SENT.remove(handler.player.getUuid());
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SENT.clear();
            SkillCastManager.clearServerInstance();
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> SENT.remove(player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getOverworld() == null) return;
            if (server.getTicks() % 20 == 0) SkillCooldowns.sweep(server);
            var mgr = SkillCastManager.get(server.getOverworld());
            long now = server.getOverworld().getTime();
            for (var player : server.getPlayerManager().getPlayerList()) {
                // An already connected player must not retain a paused clock, even before pressing a key.
                if (server.getTicks() % 20 == 0) recoverOnlineClock(mgr, player, now);
                if (!ServerPlayNetworking.canSend(player, ID)) continue;
                var views = mgr.snapshot(player.getUuid(), now);
                var stamps = views.stream().map(v -> new Stamp(v.skill(), v.phase(),
                        v.remaining() > 0 ? now + v.remaining() : 0, v.total())).toList();
                var previous = SENT.get(player.getUuid());
                boolean unchanged = previous != null && previous.player == player && previous.world == player.getWorld()
                        && previous.connection == player.networkHandler && stamps.equals(previous.stamps);
                // Empty lists have no clock to calibrate. Still send the transition to empty once,
                // and always establish a fresh baseline after reconnect, respawn, dimension or reload.
                if (unchanged && (stamps.isEmpty() || server.getTicks() % 20 != 0)) continue;
                SENT.put(player.getUuid(), new Snapshot(player, player.getWorld(), player.networkHandler, stamps));
                var buf = PacketByteBufs.create();
                SkillCooldownPacket.write(buf, views);
                ServerPlayNetworking.send(player, ID, buf);
            }
        });
    }
    private static void recoverOnlineClock(SkillCastManager manager,
                                            net.minecraft.server.network.ServerPlayerEntity player, long now) {
        if (player.networkHandler != null && player.networkHandler.isConnectionOpen()
                && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player) {
            manager.onPlayerJoined(player.getUuid(), now);
        }
    }
}
