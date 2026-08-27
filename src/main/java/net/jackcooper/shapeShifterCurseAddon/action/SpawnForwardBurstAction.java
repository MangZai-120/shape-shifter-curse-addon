package net.jackcooper.shapeShifterCurseAddon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * 向前喷射粒子 action（附属专用）。
 * 沿实体视线方向，从眼前向前一段距离内喷射粒子，每个粒子带"向前飞"的初速度，
 * 实现吐火/吐息那种向前喷涌的动力感（普通 particle 命令的 delta 是固定世界向量、
 * 无法跟随朝向，所以用 Java 取 lookVec 计算速度）。
 * 仅 red 吐火使用，不动共享的 my_addon:fire_breath，因此不影响 sp。
 */
public class SpawnForwardBurstAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) return;
        if (!(entity instanceof LivingEntity living)) return;

        ParticleEffect particleEffect = data.get("particle");
        boolean force = data.getBoolean("force");
        int count = Math.max(1, data.getInt("count"));
        double distance = data.getDouble("distance");
        double forwardSpeed = data.getDouble("forward_speed");
        double sideSpread = data.getDouble("side_spread");
        double verticalOffset = data.getDouble("vertical_offset");

        Vec3d look = living.getRotationVec(1.0F);
        Vec3d start = living.getEyePos().add(0, verticalOffset, 0);
        Random r = serverWorld.getRandom();

        for (int i = 0; i < count; i++) {
            // 沿前方随机距离取生成位置 + 少量侧向散布
            double dist = r.nextDouble() * distance;
            Vec3d pos = start.add(look.multiply(dist))
                    .add((r.nextDouble() - 0.5) * sideSpread,
                         (r.nextDouble() - 0.5) * sideSpread,
                         (r.nextDouble() - 0.5) * sideSpread);
            // 速度 = 朝向 * 向前速度 + 少量随机扰动，形成喷涌锥
            double vx = look.x * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            double vy = look.y * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            double vz = look.z * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                // count=0 时 (vx,vy,vz) 作为粒子速度方向，最后的 1.0 为速度倍率
                serverWorld.spawnParticles(player, particleEffect, force,
                        pos.x, pos.y, pos.z, 0, vx, vy, vz, 1.0);
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                new Identifier("my_addon", "spawn_forward_burst"),
                new SerializableData()
                        .add("particle", SerializableDataTypes.PARTICLE_EFFECT_OR_TYPE)
                        .add("count", SerializableDataTypes.INT, 20)
                        .add("force", SerializableDataTypes.BOOLEAN, false)
                        .add("distance", SerializableDataTypes.DOUBLE, 4.0D)
                        .add("forward_speed", SerializableDataTypes.DOUBLE, 0.5D)
                        .add("side_spread", SerializableDataTypes.DOUBLE, 0.4D)
                        .add("vertical_offset", SerializableDataTypes.DOUBLE, -0.2D),
                SpawnForwardBurstAction::action
        );
    }
}
