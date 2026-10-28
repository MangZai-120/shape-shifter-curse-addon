package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.compat.MoistureWaterRecovery;

/** Client-side half of the shallow-water correction so the HUD stops flickering. */
public final class MoistureWaterRecoveryClient {
    private MoistureWaterRecoveryClient() { }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> MoistureWaterRecovery.capture(client.player));
        ClientTickEvents.END_CLIENT_TICK.register(client -> MoistureWaterRecovery.restore(client.player));
    }
}
