package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/** Keep the world's entity insertion order when several beams are tracked for one player. */
public final class ClientLaserBeams {
    private static final Map<ClientWorld, Set<Integer>> BEAMS = new WeakHashMap<>();
    private ClientLaserBeams() {}

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LaserBeamEntity)
                BEAMS.computeIfAbsent(world, ignored -> new LinkedHashSet<>()).add(entity.getId());
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof LaserBeamEntity)) return;
            var ids = BEAMS.get(world);
            if (ids != null) {
                ids.remove(entity.getId());
                if (ids.isEmpty()) BEAMS.remove(world);
            }
        });
    }

    public static LaserBeamEntity find(ClientPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ClientWorld world)) return null;
        var ids = BEAMS.get(world);
        if (ids == null) return null;
        for (int id : ids) {
            // These tracked values can arrive after spawning or change while the beam is loaded.
            if (world.getEntityById(id) instanceof LaserBeamEntity beam && !beam.isRemoved()
                    && !beam.isEnhanced() && beam.getTrackedOwnerId() == player.getId()) return beam;
        }
        return null;
    }
}
