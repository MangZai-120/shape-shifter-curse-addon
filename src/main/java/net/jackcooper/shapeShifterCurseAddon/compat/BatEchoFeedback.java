package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkManager;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.world.event.EntityPositionSource;

/** 复用夜间探测本来的目标遍历，声效和振动只发给施术者，每轮反馈数量有上限。 */
public final class BatEchoFeedback {
    private record Burst(long tick, int sent) {}
    private static final Map<UUID, Burst> BURSTS = new HashMap<>();
    private BatEchoFeedback() {}

    public static boolean play(Entity actor, Entity target, int limit, float volume) {
        if (!(actor instanceof ServerPlayerEntity player) || actor == target
                || !(target instanceof LivingEntity) || !target.isAlive() || actor.getWorld() != target.getWorld()) return false;
        int cap = Math.min(32, Math.max(0, limit));
        long tick = player.getServer().getTicks();
        var previous = BURSTS.get(player.getUuid());
        int sent = previous != null && previous.tick == tick ? previous.sent : 0;
        if (sent >= cap) return false;
        if (sent == 0 && volume > 0) {
            float loudness = Math.min(1, volume);
            MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, loudness, 1);
            MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), loudness, 1);
        }
        var pos = target.getBoundingBox().getCenter();
        var particle = new VibrationParticleEffect(new EntityPositionSource(player, player.getStandingEyeHeight()), 10);
        player.getServerWorld().spawnParticles(player, particle, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        BURSTS.put(player.getUuid(), new Burst(tick, sent + 1));
        return true;
    }

    public static ActionFactory<Pair<Entity, Entity>> factory() {
        return new ActionFactory<>(new Identifier("ssc_addon", "bat_echo_feedback"),
                new SerializableData().add("max_targets", SerializableDataTypes.INT, 8)
                        .add("volume", SerializableDataTypes.FLOAT, .35f),
                (data, pair) -> play(pair.getLeft(), pair.getRight(), data.getInt("max_targets"), data.getFloat("volume")));
    }

    public static void registerLifecycle() {
        PlayerSessionCleanup.register((handler, server) -> BURSTS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BURSTS.clear());
    }
}
