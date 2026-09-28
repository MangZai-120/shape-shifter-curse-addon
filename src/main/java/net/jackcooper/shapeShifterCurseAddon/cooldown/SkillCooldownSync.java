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
    private static final Map<UUID, List<Stamp>> SENT = new HashMap<>();
    private record Stamp(String skill, int phase, long end, int total) {}
    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getOverworld() == null) return;
            if (server.getTicks() % 20 == 0) SkillCooldowns.sweep(server);
            var mgr = SkillCastManager.get(server.getOverworld());
            long now = server.getOverworld().getTime();
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(player, ID)) continue;
                var views = mgr.snapshot(player.getUuid(), now);
                var stamps = views.stream().map(v -> new Stamp(v.skill(), v.phase(),
                        v.remaining() > 0 ? now + v.remaining() : 0, v.total())).toList();
                if (stamps.equals(SENT.get(player.getUuid())) && server.getTicks() % 20 != 0) continue;
                SENT.put(player.getUuid(), stamps);
                var buf = PacketByteBufs.create();
                SkillCooldownPacket.write(buf, views);
                ServerPlayNetworking.send(player, ID, buf);
            }
        });
    }
}
