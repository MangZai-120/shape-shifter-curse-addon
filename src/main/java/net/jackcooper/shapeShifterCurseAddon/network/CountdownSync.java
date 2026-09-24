package net.jackcooper.shapeShifterCurseAddon.network;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Batches explicitly opted-in Apoli countdowns. No prediction is inferred for mana or arbitrary resources. */
public final class CountdownSync {
    public static final Identifier STATE = new Identifier("ssc_addon", "countdowns_v1");
    public record Key(UUID owner, Identifier power) {}
    public record State(Key key, int entityId, CountdownValue clock) {
        public void write(PacketByteBuf buf) {
            buf.writeUuid(key.owner); buf.writeIdentifier(key.power); buf.writeVarInt(entityId);
            buf.writeVarInt(clock.value()); buf.writeVarInt(clock.minimum()); buf.writeBoolean(clock.running());
        }
        public static State read(PacketByteBuf buf, long time) {
            Key key = new Key(buf.readUuid(), buf.readIdentifier());
            int entityId = buf.readVarInt(), value = buf.readVarInt(), min = buf.readVarInt();
            return new State(key, entityId, new CountdownValue(value, time, min, buf.readBoolean()));
        }
    }
    private static final class Entry {
        final ServerPlayerEntity owner;
        final ServerWorld world;
        final VariableIntPower power;
        int touched;
        Entry(ServerPlayerEntity owner, VariableIntPower power) {
            this.owner = owner; this.world = owner.getServerWorld(); this.power = power;
        }
    }
    private record Audience(ServerPlayerEntity player, ServerWorld world, int sent, List<State> states) {}
    private static final Map<Key, Entry> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();
    private CountdownSync() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CountdownSync::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AUDIENCES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ACTIVE.clear(); AUDIENCES.clear(); });
    }

    /** Equivalent to change_resource(add,-1), including server-side min_action/max_action. */
    public static void decrement(Entity entity, PowerType<?> type) {
        if (!(entity instanceof LivingEntity) || entity.getWorld().isClient) return;
        var power = PowerHolderComponent.KEY.get(entity).getPower(type);
        // Preserve change_resource semantics if a datapack overrides this target with a CooldownPower.
        if (power instanceof io.github.apace100.apoli.power.CooldownPower cooldown) {
            cooldown.modify(-1);
            PowerHolderComponent.syncPower(entity, type);
            return;
        }
        if (!(power instanceof VariableIntPower resource)) return;
        int before = resource.getValue();
        resource.setValue(before - 1);
        if (!(entity instanceof ServerPlayerEntity owner)) {
            PowerHolderComponent.syncPower(entity, type);
            return;
        }
        // Older clients retain the original authoritative path, including tracking observers.
        boolean legacy = !ServerPlayNetworking.canSend(owner, STATE)
                || PlayerLookup.tracking(owner).stream().anyMatch(p -> !ServerPlayNetworking.canSend(p, STATE));
        if (legacy) PowerHolderComponent.syncPower(owner, resource.getType());
        // Already at the floor and never announced: nothing to show, still sync once for non-player owners.
        if (before == resource.getValue() && before <= resource.getMin() && !ACTIVE.containsKey(key(owner, resource))) {
            PowerHolderComponent.syncPower(entity, type);
            return;
        }
        markInternal(owner, resource);
    }

    /** Java 侧每 tick 倒计时登记入口（2026-09-24，补 GPT 未完成的 HUD/CD 资源接入）：
     *  调用方已用 PowerUtils.setResourceValue 写值，这里只登记锚点，由 flush() 批量或变化即时发包。
     *  语义约束：仅限「每 tick 恰好 -1 的单调递减倒计时」资源；状态标志/非单调资源不得走此通道
     *  （仍用原 setResourceValueAndSync 立即同步，客户端无法预测任意跳变）。 */
    public static void mark(ServerPlayerEntity owner, Identifier resourceId) {
        if (owner == null || owner.getWorld().isClient) return;
        io.github.apace100.apoli.power.PowerType<?> type =
                io.github.apace100.apoli.power.PowerTypeRegistry.get(resourceId);
        var power = PowerHolderComponent.KEY.get(owner).getPower(type);
        if (power instanceof io.github.apace100.apoli.power.CooldownPower) {
            PowerHolderComponent.syncPower(owner, power.getType());
            return;
        }
        if (!(power instanceof VariableIntPower resource)) return;
        markInternal(owner, resource);
    }

    private static Key key(ServerPlayerEntity owner, VariableIntPower resource) {
        return new Key(owner.getUuid(), resource.getType().getIdentifier());
    }

    private static void markInternal(ServerPlayerEntity owner, VariableIntPower resource) {
        // Older clients retain the original authoritative path, including tracking observers.
        boolean legacy = !ServerPlayNetworking.canSend(owner, STATE)
                || PlayerLookup.tracking(owner).stream().anyMatch(p -> !ServerPlayNetworking.canSend(p, STATE));
        if (legacy) PowerHolderComponent.syncPower(owner, resource.getType());
        Key key = key(owner, resource);
        Entry entry = ACTIVE.get(key);
        if (entry == null || entry.owner != owner || entry.power != resource || entry.world != owner.getWorld()) {
            entry = new Entry(owner, resource);
            ACTIVE.put(key, entry);
        }
        entry.touched = owner.getServer().getTicks();
    }

    private static void flush(MinecraftServer server) {
        int tick = server.getTicks();
        ACTIVE.values().removeIf(e -> e.owner.isRemoved() || e.owner.getWorld() != e.world
                || PowerHolderComponent.KEY.get(e.owner).getPower(e.power.getType()) != e.power);
        if (ACTIVE.isEmpty() && AUDIENCES.isEmpty()) return;
        Map<Key, State> states = new LinkedHashMap<>();
        Map<UUID, Collection<ServerPlayerEntity>> tracking = new HashMap<>();
        ACTIVE.forEach((key, entry) -> {
            int value = entry.power.getValue();
            boolean running = entry.touched == tick && value > entry.power.getMin();
            states.put(key, new State(key, entry.owner.getId(),
                    new CountdownValue(value, entry.world.getTime(), entry.power.getMin(), running)));
            tracking.computeIfAbsent(key.owner, ignored -> PlayerLookup.tracking(entry.owner));
        });
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, STATE)) continue;
            List<State> visible = new ArrayList<>();
            states.forEach((key, state) -> {
                Entry entry = ACTIVE.get(key);
                if (entry.world == player.getWorld() && (entry.owner == player || tracking.get(key.owner).contains(player)))
                    visible.add(state);
            });
            Audience previous = AUDIENCES.get(player.getUuid());
            if (visible.isEmpty() && previous == null) continue;
            boolean changed = previous == null || previous.player != player || previous.world != player.getWorld()
                    || !sameState(previous.states, visible);
            if (!changed && tick - previous.sent < 20) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
            buf.writeLong(player.getWorld().getTime()); buf.writeVarInt(visible.size());
            visible.forEach(state -> state.write(buf));
            ServerPlayNetworking.send(player, STATE, buf);
            if (visible.isEmpty()) AUDIENCES.remove(player.getUuid());
            else AUDIENCES.put(player.getUuid(), new Audience(player, player.getServerWorld(), tick, visible));
        }
        // A final non-running anchor was just delivered. Native Apoli sync handles later manual changes.
        ACTIVE.entrySet().removeIf(e -> !states.get(e.getKey()).clock.running());
    }

    static boolean sameState(List<State> previous, List<State> current) {
        if (previous.size() != current.size()) return false;
        for (int i = 0; i < previous.size(); i++) {
            State a = previous.get(i), b = current.get(i);
            if (!a.key.equals(b.key) || a.entityId != b.entityId || !a.clock.agreesWith(b.clock)) return false;
        }
        return true;
    }
}
