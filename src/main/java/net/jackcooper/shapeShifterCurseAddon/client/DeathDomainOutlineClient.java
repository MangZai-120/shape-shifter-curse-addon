package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class DeathDomainOutlineClient {
    private static final Set<UUID> targets = new HashSet<>();

    private DeathDomainOutlineClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_DEATH_DOMAIN_OUTLINES,
                (client, handler, buf, sender) -> {
                    var dimension = buf.readIdentifier();
                    int ownerId = buf.readVarInt();
                    boolean add = buf.readBoolean();
                    var ids = buf.readList(PacketByteBuf::readUuid);
                    AbilityVisualLifecycle.receiveOwner(client, handler, dimension, ownerId, () -> {
                        if (add) targets.addAll(ids);
                        else targets.removeAll(ids);
                    });
                });
    }

    public static boolean highlights(Entity entity) {
        return entity.getWorld().isClient && targets.contains(entity.getUuid()) && entity.isAlive()
                && entity.getWorld() == MinecraftClient.getInstance().world && !DomainRenderer.blocksTargetingClient(entity);
    }

    static void clear() {
        targets.clear();
    }
}
