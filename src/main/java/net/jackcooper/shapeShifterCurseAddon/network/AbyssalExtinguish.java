package net.jackcooper.shapeShifterCurseAddon.network;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.compat.MoistureWaterRecovery;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class AbyssalExtinguish {
    public static final Identifier REQUEST = new Identifier("ssc_addon", "abyssal_extinguish");
    private static final Identifier POWER = new Identifier("my_addon", "form_axolotl_sp_fire_extinguish");
    private static final int COST = 10;

    private AbyssalExtinguish() {}

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) ->
                ServerPacketExecutor.execute(server, player, handler, () -> extinguish(player)));
    }

    private static void extinguish(ServerPlayerEntity player) {
        if (!player.isAlive() || !player.isOnFire() || player.getAir() < COST
                || !FormUtils.isAxolotlSP(player) || !PowerTypeRegistry.contains(POWER)) return;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(POWER));
        if (power == null || !power.isActive()) return;

        player.extinguish();
        if (!player.isOnFire()) {
            player.setOnFire(false);
            MoistureWaterRecovery.consume(player, COST);
        }
    }
}
