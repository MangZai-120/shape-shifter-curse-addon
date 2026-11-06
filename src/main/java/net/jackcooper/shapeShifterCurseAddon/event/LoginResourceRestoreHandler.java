package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Restore persistent resources at the end of SSC's form rebuild, before gameplay ticks. */
public final class LoginResourceRestoreHandler {
    private LoginResourceRestoreHandler() { }

    private record Pending(Map<Identifier, Integer> resources, double mana, String manaType) { }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final java.util.Set<Identifier> OTHER_PERSISTENT = java.util.Set.of(
            new Identifier("my_addon", "form_mancianima_resistance"),
            new Identifier("my_addon", "form_ocelot_nova_nine_lives"),
            new Identifier("my_addon", "form_snow_fox_sp_switch_state"));

    /** Only actual persistent resource bars participate; timers, charge and toggle states do not. */
    public static boolean isPersistentResource(Identifier id) {
        for (var bar : BarKeys.ALL) if (bar.id.equals(id)) return true;
        return OTHER_PERSISTENT.contains(id);
    }

    public static void recordSnapshot(UUID uuid, NbtCompound playerNbt) {
        PENDING.remove(uuid);
        NbtCompound components = playerNbt.getCompound("cardinal_components");
        if (components.isEmpty()) return;
        Map<Identifier, Integer> resources = new HashMap<>();
        NbtList powers = components.getCompound("apoli:powers").getList("Powers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < powers.size(); i++) {
            NbtCompound power = powers.getCompound(i);
            Identifier id = Identifier.tryParse(power.getString("Type"));
            if (id != null && isPersistentResource(id) && power.contains("Data", NbtElement.INT_TYPE)) {
                resources.put(id, power.getInt("Data"));
            }
        }
        NbtCompound mana = components.getCompound("shape-shifter-curse:mana");
        double savedMana = mana.contains("Mana", NbtElement.NUMBER_TYPE) ? mana.getDouble("Mana") : -1;
        if (!resources.isEmpty() || savedMana >= 0) {
            PENDING.put(uuid, new Pending(resources, savedMana,
                    mana.contains("ManaTypeID") ? mana.getString("ManaTypeID") : null));
        }
    }

    public static void restore(ServerPlayerEntity player) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null) return;
        for (var entry : pending.resources.entrySet()) {
            if (net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.hasPowerId(player, entry.getKey())) {
                PowerUtils.setResourceValueAndSync(player, entry.getKey(), entry.getValue());
            }
        }
        if (Double.isFinite(pending.mana) && pending.mana >= 0
                && pending.manaType != null
                && pending.manaType.equals(String.valueOf(ManaUtils.getPlayerManaTypeID(player)))) {
            ManaUtils.setPlayerMana(player, pending.mana);
        }
    }

    public static void register() {
        // OriginFormReloadMixin calls restore after _loadForm finishes. No listener-order
        // assumption or delayed writeback may overwrite the player's subsequent actions.
        PlayerSessionCleanup.register((handler, server) -> PENDING.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }
}
