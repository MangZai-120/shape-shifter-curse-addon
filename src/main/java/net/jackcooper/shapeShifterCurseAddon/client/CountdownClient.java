package net.jackcooper.shapeShifterCurseAddon.client;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.CountdownSync;
import net.jackcooper.shapeShifterCurseAddon.network.CountdownValue;
import net.jackcooper.shapeShifterCurseAddon.util.ClientResourceCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtInt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class CountdownClient {
    private record Prediction(LivingEntity owner, VariableIntPower power, CountdownValue clock) {}
    private static final Map<CountdownSync.Key, Prediction> VALUES = new HashMap<>();
    private static final Map<CountdownSync.Key, CountdownSync.State> PENDING = new HashMap<>();
    private static ClientWorld world;
    private static long received;
    private static boolean applying;
    private CountdownClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CountdownSync.STATE, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier();
            long time = buf.readLong();
            int count = buf.readVarInt();
            var incoming = new ArrayList<CountdownSync.State>();
            for (int i = 0; i < count; i++) incoming.add(CountdownSync.State.read(buf, time));
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null
                        || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                if (world != client.world) clear();
                // Freeze the previous predictions at their current values before replacing the snapshot.
                VALUES.values().forEach(CountdownClient::materialize);
                VALUES.clear();
                PENDING.clear();
                world = client.world;
                received = Math.max(time, world.getTime());
                incoming.forEach(state -> PENDING.put(state.key(), state));
                bindPending();
                ClientResourceCache.invalidate();
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != world) { clear(); return; }
            if (world == null) return;
            bindPending();
            VALUES.values().removeIf(p -> p.owner.isRemoved() || p.owner.getWorld() != world
                    || PowerHolderComponent.KEY.get(p.owner).getPower(p.power.getType()) != p.power);
            if (world.getTime() - received > 60) {
                VALUES.values().forEach(CountdownClient::materialize);
                VALUES.clear();
                PENDING.clear();
                ClientResourceCache.invalidate();
            }
        });
    }

    /** Hooking getValue keeps both custom HUDs and native Apoli resource conditions consistent. */
    public static Integer predicted(LivingEntity entity, VariableIntPower power) {
        if (VALUES.isEmpty() || !entity.getWorld().isClient || entity.getWorld() != world) return null;
        Prediction prediction = VALUES.get(new CountdownSync.Key(entity.getUuid(), power.getType().getIdentifier()));
        return prediction != null && prediction.owner == entity && prediction.power == power
                ? prediction.clock.at(world.getTime()) : null;
    }

    /** Resets/consumption still arrive via native Apoli packets; apply them immediately, never overwrite them. */
    public static void nativeSync(LivingEntity entity, VariableIntPower power, int value) {
        if (applying || !entity.getWorld().isClient) return;
        var key = new CountdownSync.Key(entity.getUuid(), power.getType().getIdentifier());
        Prediction old = VALUES.get(key);
        if (old != null && old.owner == entity && entity.getWorld() == world) {
            VALUES.put(key, new Prediction(entity, power,
                    new CountdownValue(value, world.getTime(), power.getMin(), old.clock.running() && value > power.getMin())));
        }
        ClientResourceCache.invalidate();
    }

    private static void materialize(Prediction prediction) {
        applying = true;
        try {
            // fromTag mirrors native sync and does not execute ResourcePower min_action/max_action on the client.
            prediction.power.fromTag(NbtInt.of(prediction.clock.at(prediction.owner.getWorld().getTime())));
        } finally { applying = false; }
    }

    private static void bindPending() {
        var iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next();
            var entity = world.getEntityById(state.entityId());
            if (!(entity instanceof LivingEntity living) || !entity.getUuid().equals(state.key().owner())) continue;
            if (!PowerTypeRegistry.contains(state.key().power())) continue;
            io.github.apace100.apoli.power.PowerType<?> type = PowerTypeRegistry.get(state.key().power());
            var power = PowerHolderComponent.KEY.get(living).getPower(type);
            if (!(power instanceof VariableIntPower resource)) continue;
            Prediction prediction = new Prediction(living, resource, state.clock());
            materialize(prediction);
            VALUES.put(state.key(), prediction);
            iterator.remove();
            ClientResourceCache.invalidate();
        }
    }
    private static void clear() { VALUES.clear(); PENDING.clear(); world = null; ClientResourceCache.invalidate(); }
}
