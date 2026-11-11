package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/** Scope transient ability mirrors to the current world and tracked entities. */
@Environment(EnvType.CLIENT)
public final class AbilityVisualLifecycle {
    private static ClientWorld world;
    private AbilityVisualLifecycle() {}

    public static void ensureWorld(ClientWorld next) {
        if (next == world) return;
        FrostSpikeChargeClientState.clearAll();
        UpgradeAxolotlSpearRenderState.clear();
        SpiderMoonWeaverSwingClient.clear();
        world = next;
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> ensureWorld(client.world));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, unloadedWorld) -> {
            if (MinecraftClient.getInstance().world != unloadedWorld) return;
            FrostSpikeChargeClientState.setCharging(entity.getUuid(), false);
            UpgradeAxolotlSpearRenderState.set(entity.getUuid(), false);
            SpiderMoonWeaverSwingClient.remove(entity.getUuid());
        });
    }
}
