package net.jackcooper.shapeShifterCurseAddon.network;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.compat.MoistureWaterRecovery;
import net.jackcooper.shapeShifterCurseAddon.ability.AxolotlTailFlint;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.Map;

public final class AbyssalExtinguish {
    public static final Identifier REQUEST = new Identifier("ssc_addon", "abyssal_extinguish");
    private static final Map<Identifier, Identifier> FORM_POWERS = Map.of(
            FormIdentifiers.AXOLOTL_SP, new Identifier("ssc_addon", "form_axolotl_sp_fire_extinguish"),
            FormIdentifiers.AXOLOTL_FLUORESCENT, new Identifier("ssc_addon", "form_axolotl_fluorescent_fire_extinguish"),
            FormIdentifiers.AXOLOTL_ALING, new Identifier("ssc_addon", "form_axolotl_aling_fire_extinguish"));
    private static final int COST = 10;

    private AbyssalExtinguish() {}

    public static Identifier powerForForm(PlayerEntity player) {
        if (AxolotlTailFlint.STATE.get(player).suppressesExtinguish()) return null;
        var form = FormUtils.getCurrentForm(player);
        var id = form == null ? null : form.getFormID();
        return id == null ? null : FORM_POWERS.get(FormIdMigration.canonical(id));
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) ->
                ServerPacketExecutor.execute(server, player, handler, () -> extinguish(player)));
    }

    private static void extinguish(ServerPlayerEntity player) {
        if (!player.isAlive() || !player.isOnFire() || player.getAir() < COST) return;
        var powerId = powerForForm(player);
        if (powerId == null || !PowerTypeRegistry.contains(powerId)) return;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(powerId));
        if (power == null || !power.isActive()) return;

        player.extinguish();
        if (!player.isOnFire()) {
            player.setOnFire(false);
            MoistureWaterRecovery.consume(player, COST);
        }
    }
}
