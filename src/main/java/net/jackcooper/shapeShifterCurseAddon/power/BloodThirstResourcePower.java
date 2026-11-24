package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.ResourcePower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.jackcooper.shapeShifterCurseAddon.ability.BatDesmodusBloodThirst;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Blood keeps its original Apoli power ID and integer save/sync format. */
public final class BloodThirstResourcePower extends ResourcePower {
    private BloodThirstResourcePower(PowerType<?> type, LivingEntity owner) {
        super(type, owner, HudRender.DONT_RENDER, 0, 0, BatDesmodusBloodThirst.MAX_BLOOD, null, null);
    }

    public static PowerFactory<BloodThirstResourcePower> createFactory() {
        return new PowerFactory<>(new Identifier("ssc_addon", "blood_thirst_resource"),
                new SerializableData(), data -> BloodThirstResourcePower::new);
    }

    @Override
    public int getMax() {
        return BatDesmodusBloodThirst.maxBlood();
    }

    @Override
    public void fromTag(NbtElement tag) {
        super.fromTag(tag);
        // A client may receive the resource before the matching balance snapshot.
        if (!entity.getWorld().isClient) setValue(getValue());
    }

    /** Primalstinct force-rebuilds the current origin in the default end-reload phase. */
    public static void registerReloadPreservation() {
        if (!FabricLoader.getInstance().isModLoaded("ssc-primalstinct")) return;
        var event = ServerLifecycleEvents.END_DATA_PACK_RELOAD;
        var before = new Identifier("ssc_addon", "before_blood_rebuild");
        var after = new Identifier("ssc_addon", "after_blood_rebuild");
        event.addPhaseOrdering(before, Event.DEFAULT_PHASE);
        event.addPhaseOrdering(Event.DEFAULT_PHASE, after);
        var previous = new ConcurrentHashMap<MinecraftServer, Map<ServerPlayerEntity, BloodThirstResourcePower>>();
        event.register(before, (server, manager, success) -> {
            if (!success) return;
            Map<ServerPlayerEntity, BloodThirstResourcePower> online = new IdentityHashMap<>();
            for (var player : server.getPlayerManager().getPlayerList()) {
                var blood = currentBlood(player);
                if (blood != null) online.put(player, blood);
            }
            previous.put(server, online);
        });
        event.register(after, (server, manager, success) -> {
            var online = previous.remove(server);
            if (!success || online == null) return;
            for (var player : server.getPlayerManager().getPlayerList()) {
                var old = online.get(player);
                if (old == null) continue;
                var blood = currentBlood(player);
                if (blood != null && blood != old && blood.getValue() == 0 && old.getValue() > 0) {
                    blood.setValue(old.getValue());
                    PowerHolderComponent.syncPower(player, blood.getType());
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(previous::remove);
    }

    private static BloodThirstResourcePower currentBlood(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.BAT_DESMODUS)
                || !PowerTypeRegistry.contains(FormIdentifiers.BAT_BLOOD_RESOURCE)) return null;
        PowerType<?> type = PowerTypeRegistry.get(FormIdentifiers.BAT_BLOOD_RESOURCE);
        return PowerHolderComponent.KEY.get(player).getPower(type) instanceof BloodThirstResourcePower blood ? blood : null;
    }

    public static void clampOnlinePlayers(MinecraftServer server) {
        if (!PowerTypeRegistry.contains(FormIdentifiers.BAT_BLOOD_RESOURCE)) return;
        PowerType<?> type = PowerTypeRegistry.get(FormIdentifiers.BAT_BLOOD_RESOURCE);
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (PowerHolderComponent.KEY.get(player).getPower(type) instanceof BloodThirstResourcePower blood
                    && blood.getValue() > blood.getMax()) {
                blood.setValue(blood.getMax());
                PowerHolderComponent.syncPower(player, type);
            }
        }
    }
}
