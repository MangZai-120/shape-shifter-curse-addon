package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.SpellbookStatusSync;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class SpellbookStatusClient {
    private static ClientWorld world;
    private static int entityId;
    private static long receivedAt, serverTime;
    private static SpellbookStatus status;

    private SpellbookStatusClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SpellbookStatusSync.STATE, (client, handler, buf, sender) -> {
            var incoming = SpellbookStatusSync.Snapshot.read(buf);
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null || client.player == null
                        || client.player.getId() != incoming.entityId()
                        || !client.world.getRegistryKey().getValue().equals(incoming.dimension())) return;
                world = client.world;
                entityId = incoming.entityId();
                receivedAt = world.getTime();
                serverTime = incoming.time();
                status = incoming.status();
            });
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static SpellbookStatus get() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (world == null || client.world != world || client.player == null || client.player.getId() != entityId
                || world.getTime() < receivedAt || world.getTime() - receivedAt > 150) {
            clear();
        }
        return status;
    }

    public static long now() {
        return world == null ? 0 : Math.max(serverTime, world.getTime());
    }

    private static void clear() { world = null; status = null; receivedAt = serverTime = 0; entityId = 0; }
}
