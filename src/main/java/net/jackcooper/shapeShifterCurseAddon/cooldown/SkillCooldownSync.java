package net.jackcooper.shapeShifterCurseAddon.cooldown;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;
import java.util.*;

/** Owner-only snapshots: state transitions immediately, running clocks every second. */
public final class SkillCooldownSync {
    public static final Identifier ID = new Identifier("ssc_addon", "skill_cooldowns_v1");
    private static final Map<UUID, Audience> SENT = new HashMap<>();
    private record Stamp(String skill, int phase, long end, int total) {}
    private record Audience(Object player, Object world, Object connection, ClockSnapshot clock) {}

    static final class ClockSnapshot {
        private List<Stamp> stamps;
        private long revision, sampledAt, nextChange;
        private boolean running;

        /** Returns the existing wire-format rows only when the client needs an update. */
        List<SkillCastManager.View> update(SkillCastManager manager, UUID owner, long now, boolean heartbeat) {
            long currentRevision = manager.snapshotRevision(owner);
            if (stamps != null && currentRevision == revision && now >= sampledAt && now < nextChange
                    && !(heartbeat && running)) return null;

            var views = manager.snapshot(owner, now);
            List<Stamp> current = new ArrayList<>(views.size());
            nextChange = Long.MAX_VALUE;
            running = false;
            for (var view : views) {
                long end = view.remaining() > 0 ? now + view.remaining() : 0;
                current.add(new Stamp(view.skill(), view.phase(), end, view.total()));
                if (view.remaining() > 0) {
                    // A paused or int-clamped clock has a moving wire deadline until normal timing resumes.
                    boolean fixedRemaining = manager.cooldown(owner, view.skill()).pausedRemaining >= 0
                            || view.remaining() == Integer.MAX_VALUE;
                    nextChange = Math.min(nextChange, fixedRemaining ? now + 1 : end);
                }
                if (end > 0 || view.phase() != SkillCastManager.PHASE_IDLE) running = true;
            }
            boolean send = !current.equals(stamps) || heartbeat && running;
            stamps = current;
            revision = currentRevision;
            sampledAt = now;
            return send ? views : null;
        }
    }

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
            boolean heartbeat = server.getTicks() % 20 == 0;
            for (var player : server.getPlayerManager().getPlayerList()) {
                // An already connected player must not retain a paused clock, even before pressing a key.
                if (heartbeat) recoverOnlineClock(mgr, player, now);
                if (!ServerPlayNetworking.canSend(player, ID)) continue;
                UUID owner = player.getUuid();
                Audience audience = SENT.get(owner);
                if (audience == null || audience.player != player || audience.world != player.getWorld()
                        || audience.connection != player.networkHandler) {
                    audience = new Audience(player, player.getWorld(), player.networkHandler, new ClockSnapshot());
                    SENT.put(owner, audience);
                }
                var views = audience.clock.update(mgr, owner, now, heartbeat);
                if (views == null) continue;
                var buf = PacketByteBufs.create();
                SkillCooldownPacket.write(buf, views);
                // Optional context trailer: old clients consume the original list.
                buf.writeVarInt(player.getId());
                buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
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
