package net.jackcooper.shapeShifterCurseAddon.action;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * 旋转圆环粒子 action（附属专用）。
 * 在实体周围的水平圆环上生成粒子，并随实体存在时间 (age) 持续旋转。
 * 逻辑仿主模组 shape-shifter-curse:spawn_particles_in_circle，额外新增 rotation_speed
 * （每 tick 旋转弧度，默认 0 即不旋转）。
 * <p>
 * 单独建附属 action 而非 mixin 主模组公共 action，避免影响悦灵净化/尖啸/共鸣核心等
 * 其它同样调用 spawn_particles_in_circle 的形态。旋转相位用服务端权威的 entity.age 计算，
 * 多人/服务器环境下所有客户端表现一致。
 */
public class SpawnRotatingCircleAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        // 仅在服务器世界生成粒子
        if (!(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        ParticleEffect particleEffect = data.get("particle");
        Predicate<Pair<Entity, Entity>> biEntityCondition = data.get("bientity_condition");

        boolean force = data.getBoolean("force");
        float speed = data.getFloat("speed");
        int count = Math.max(0, data.getInt("count"));

        Vec3d spread = data.<Vec3d>get("spread");
        double offsetX = data.getDouble("offset_x");
        double offsetY = data.getDouble("offset_y");
        double offsetZ = data.getDouble("offset_z");

        double radius = data.getDouble("radius");
        int sampleCount = Math.max(1, data.getInt("sample_count"));
        double rotationSpeed = data.getDouble("rotation_speed");
        boolean randomAngle = data.getBoolean("random_angle");

        // 旋转相位：随实体存在 tick 数推进；age 由服务端统一推进，多人环境一致
        double rotationPhase = entity.age * rotationSpeed;

        Vec3d basePos = entity.getPos().add(offsetX, offsetY, offsetZ);
        // 扩散向量按实体尺寸缩放（与主模组 spawn_particles_in_circle 保持一致）
        Vec3d delta = spread.multiply(entity.getWidth(), entity.getEyeHeight(entity.getPose()), entity.getWidth());

        for (int i = 0; i < sampleCount; i++) {
            // random_angle=true 时每个采样点在圆环上随机取角（不再固定点位）；否则均匀采样 + 旋转相位
            double angle = randomAngle
                    ? serverWorld.getRandom().nextDouble() * 2 * Math.PI
                    : 2 * Math.PI * i / sampleCount + rotationPhase;
            double xOffset = radius * Math.cos(angle);
            double zOffset = radius * Math.sin(angle);
            Vec3d particlePos = basePos.add(xOffset, 0, zOffset);

            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (biEntityCondition == null || biEntityCondition.test(new Pair<>(entity, player))) {
                    serverWorld.spawnParticles(
                            player,
                            particleEffect,
                            force,
                            particlePos.getX(),
                            particlePos.getY(),
                            particlePos.getZ(),
                            count,
                            delta.getX(),
                            delta.getY(),
                            delta.getZ(),
                            speed
                    );
                }
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                new Identifier("my_addon", "spawn_rotating_circle"),
                new SerializableData()
                        .add("particle", SerializableDataTypes.PARTICLE_EFFECT_OR_TYPE)
                        .add("bientity_condition", ApoliDataTypes.BIENTITY_CONDITION, null)
                        .add("count", SerializableDataTypes.INT, 1)
                        .add("speed", SerializableDataTypes.FLOAT, 0.0F)
                        .add("force", SerializableDataTypes.BOOLEAN, false)
                        .add("spread", SerializableDataTypes.VECTOR, new Vec3d(0.5, 0.5, 0.5))
                        .add("offset_x", SerializableDataTypes.DOUBLE, 0.0D)
                        .add("offset_y", SerializableDataTypes.DOUBLE, 0.5D)
                        .add("offset_z", SerializableDataTypes.DOUBLE, 0.0D)
                        .add("radius", SerializableDataTypes.DOUBLE, 1.0D)
                        .add("sample_count", SerializableDataTypes.INT, 8)
                        .add("rotation_speed", SerializableDataTypes.DOUBLE, 0.0D)
                        .add("random_angle", SerializableDataTypes.BOOLEAN, false),
                SpawnRotatingCircleAction::action
        );
    }
}
