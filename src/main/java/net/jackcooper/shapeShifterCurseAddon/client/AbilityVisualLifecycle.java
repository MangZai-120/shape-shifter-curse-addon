package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

/** Scope transient ability mirrors to the current world and tracked entities. */
@Environment(EnvType.CLIENT)
public final class AbilityVisualLifecycle {
    private static ClientWorld world;
    private static net.minecraft.client.network.ClientPlayerEntity player;
    private AbilityVisualLifecycle() {}

    public static void ensureWorld(ClientWorld next) {
        var nextPlayer = MinecraftClient.getInstance().player;
        if (next != world) {
            FrostSpikeChargeClientState.clearAll();
            UpgradeAxolotlSpearRenderState.clear();
            SpiderMoonWeaverSwingClient.clear();
        }
        if (next != world || nextPlayer != player) {
            DeathDomainOutlineClient.clear();
            ClawClientState.reset();
            DashClientState.reset();
            NovaLeapClientState.clear();
            FallenAllayVexClientState.clear();
            SkillCooldownClient.clear();
        }
        world = next;
        player = nextPlayer;
    }

    public static void register() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.getNetworkHandler() == null || client.getNetworkHandler() == handler) clear();
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> ensureWorld(client.world));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, unloadedWorld) -> {
            if (MinecraftClient.getInstance().world != unloadedWorld) return;
            FrostSpikeChargeClientState.setCharging(entity.getUuid(), false);
            UpgradeAxolotlSpearRenderState.set(entity.getUuid(), false);
            SpiderMoonWeaverSwingClient.remove(entity.getUuid());
        });
    }

    /** New packets carry a dimension; legacy packets are tied to their received world object. */
    public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler,
                               Identifier dimension, Runnable apply) {
        receive(client, handler, dimension, false, -1, apply);
    }

    public static void receiveOwner(MinecraftClient client, ClientPlayNetworkHandler handler,
                                    Identifier dimension, int entityId, Runnable apply) {
        receive(client, handler, dimension, true, entityId, apply);
    }

    private static void receive(MinecraftClient client, ClientPlayNetworkHandler handler,
                                Identifier dimension, boolean ownerOnly, int entityId, Runnable apply) {
        var receivedWorld = client.world;
        var receivedPlayer = client.player;
        client.execute(() -> {
            if (client.getNetworkHandler() != handler || client.world == null) return;
            if (dimension == null ? client.world != receivedWorld
                    : !dimension.equals(client.world.getRegistryKey().getValue())) return;
            if (ownerOnly && (client.player == null || (entityId >= 0 ? client.player.getId() != entityId
                    : client.player != receivedPlayer))) return;
            ensureWorld(client.world);
            apply.run();
        });
    }

    private static void clear() {
        DeathDomainOutlineClient.clear();
        FrostSpikeChargeClientState.clearAll();
        UpgradeAxolotlSpearRenderState.clear();
        SpiderMoonWeaverSwingClient.clear();
        ClawClientState.reset(); DashClientState.reset();
        NovaLeapClientState.clear(); FallenAllayVexClientState.clear(); SkillCooldownClient.clear();
        world = null; player = null;
    }
}
