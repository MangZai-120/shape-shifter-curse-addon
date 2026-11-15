package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSync;
import net.minecraft.client.MinecraftClient;
import java.util.*;

public final class SkillCooldownClient {
    public record State(int phase, int remaining, int total, long anchor) {
        public int remainingNow(long now) { return (int)Math.max(0, remaining - Math.max(0, now - anchor)); }
    }
    private static Map<String, State> states = Map.of();
    public static State get(String skill) {
        var client = MinecraftClient.getInstance();
        AbilityVisualLifecycle.ensureWorld(client.world);
        return states.get(net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.domain(skill));
    }
    static void clear() { states = Map.of(); }
    public static long now() { return MinecraftClient.getInstance().world == null ? 0 : MinecraftClient.getInstance().world.getTime(); }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SkillCooldownSync.ID, (client, handler, buf, response) -> {
            Map<String, State> received = new HashMap<>();
            for (var view : net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownPacket.read(buf)) {
                received.put(view.skill(), new State(view.phase(), view.remaining(), view.total(), 0));
            }
            boolean scoped = buf.isReadable();
            int entityId = scoped ? buf.readVarInt() : -1;
            var dimension = scoped ? buf.readIdentifier() : null;
            AbilityVisualLifecycle.receiveOwner(client, handler, dimension, entityId, () -> {
                long time = now();
                Map<String, State> anchored = new HashMap<>();
                received.forEach((id, s) -> anchored.put(id, new State(s.phase(), s.remaining(), s.total(), time)));
                states = Map.copyOf(anchored);
            });
        });
    }
}
