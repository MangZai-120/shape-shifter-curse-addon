package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.spell.WrathMark;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public final class WrathMarkClient {
    private static ClientPlayerEntity recipient;
    private static Set<UUID> targets = Set.of();
    private static long expires;

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientPlayNetworking.registerGlobalReceiver(WrathMark.OUTLINES,(client,handler,buf,sender)->{
            var dimension=buf.readIdentifier();
            var victim=buf.readUuid();
            int ticks=buf.readVarInt();
            var incoming=new HashSet<>(buf.readList(b->b.readUuid()));
            client.execute(()->{
                if (client.getNetworkHandler()!=handler || client.world==null || client.player==null
                        || !client.world.getRegistryKey().getValue().equals(dimension) || !client.player.getUuid().equals(victim)) return;
                if (ticks<=0) { clear(); return; }
                recipient=client.player;
                expires=client.world.getTime()+ticks;
                targets=incoming;
            });
        });
    }
    private static void clear() { recipient=null; targets=Set.of(); expires=0; }

    private static int remainingTicks() {
        if (recipient == null) return 0;
        var client = MinecraftClient.getInstance();
        if (client.player == recipient && client.world == recipient.getWorld() && recipient.isAlive()) {
            long ticks = expires - client.world.getTime();
            if (ticks > 0) return (int) ticks;
        }
        clear();
        return 0;
    }

    /** Vanilla blindness fades over its final twenty ticks. */
    public static float blindnessStrength(Entity viewer) {
        return viewer != null && viewer == recipient ? Math.min(1f, remainingTicks() / 20f) : 0f;
    }

    public static boolean hasOutline(Entity entity) {
        return remainingTicks() > 0 && entity.getWorld() == recipient.getWorld() && targets.contains(entity.getUuid());
    }
}
