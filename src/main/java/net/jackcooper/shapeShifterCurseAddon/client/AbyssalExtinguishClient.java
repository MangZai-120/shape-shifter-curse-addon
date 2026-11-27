package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.network.AbyssalExtinguish;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
final class AbyssalExtinguishClient {
    private static final int FIRST_DELAY = 3;
    private static final int RETRY_DELAY = 20;
    private static final int MAX_ATTEMPTS = 3;
    private static ClientPlayerEntity burningPlayer;
    private static ClientWorld burningWorld;
    private static int ticksUntilRequest;
    private static int attemptsLeft;

    private AbyssalExtinguishClient() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AbyssalExtinguishClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.isPaused()) return;
        var player = client.player;
        if (player == null || !player.isOnFire() || !player.isAlive()) {
            burningPlayer = null;
            burningWorld = null;
            return;
        }
        if (player != burningPlayer || client.world != burningWorld) {
            burningPlayer = player;
            burningWorld = client.world;
            attemptsLeft = MAX_ATTEMPTS;
            ticksUntilRequest = 0;
        }
        // Keep an exhausted burn latched until the fire clears, even across form changes.
        if (attemptsLeft == 0) return;
        if (!FormUtils.isAxolotlSP(player)) {
            ticksUntilRequest = 0;
            return;
        }
        if (ticksUntilRequest == 0) {
            ticksUntilRequest = FIRST_DELAY;
            return;
        }
        if (--ticksUntilRequest == 0) {
            attemptsLeft--;
            if (ClientPlayNetworking.canSend(AbyssalExtinguish.REQUEST)) {
                ClientPlayNetworking.send(AbyssalExtinguish.REQUEST, PacketByteBufs.empty());
            }
            ticksUntilRequest = RETRY_DELAY;
        }
    }
}
