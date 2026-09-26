package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.jackcooper.shapeShifterCurseAddon.client.particle.OwnedDecoration;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleAvoidance;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Metadata and field access only: Sodium replaces buildGeometry, so never inject inside it. */
@Mixin(BillboardParticle.class)
public abstract class FirstPersonBillboardParticleMixin extends Particle implements OwnedDecoration {

    protected FirstPersonBillboardParticleMixin(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Shadow public abstract float getSize(float tickDelta);

    @Override
    public double ssca$cameraDistance(Camera camera, float tickDelta) {
        var eye = camera.getPos();
        double dx = MathHelper.lerp(tickDelta, prevPosX, x) - eye.x;
        double dy = MathHelper.lerp(tickDelta, prevPosY, y) - eye.y;
        double dz = MathHelper.lerp(tickDelta, prevPosZ, z) - eye.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public float ssca$getRenderAlpha() { return alpha; }

    @Override
    public void ssca$setRenderAlpha(float value) { alpha = value; }

    @Override
    public float ssca$cameraVisibility(Camera camera, float tickDelta) {
        var owner = ssca$getDecorationOwner();
        if (owner == null) return 1;
        var client = MinecraftClient.getInstance();
        if (client.player == null || camera.getFocusedEntity() != client.player
                || !client.options.getPerspective().isFirstPerson() || camera.isThirdPerson()
                || !owner.equals(client.player.getUuid())) return 1;
        var strength = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig().firstPersonParticleAvoidance;
        if (strength == null || strength == ParticleAvoidance.Strength.OFF) return 1;
        var eye = camera.getPos();
        double dx = MathHelper.lerp(tickDelta, prevPosX, x) - eye.x;
        double dy = MathHelper.lerp(tickDelta, prevPosY, y) - eye.y;
        double dz = MathHelper.lerp(tickDelta, prevPosZ, z) - eye.z;
        double squaredDistance = dx * dx + dy * dy + dz * dz;
        double distance = Math.sqrt(squaredDistance);
        // 近距抽样区：< 1.5 格时抽 20% 显示（25% 影子透明度），其余 80% 隐藏
        if (distance < ParticleAvoidance.NEAR_ZONE && !ParticleAvoidance.keepNearSample(this)) return 0;
        float visibility = ParticleAvoidance.visibility(strength, true, true, distance, getSize(tickDelta));
        boolean projectile = ssca$isProjectileDecoration();
        // 距离门控前置：恢复带外锥/距离都无衰减，跳过三角函数计算（远距粒子每帧 2-3 次调用白省）
        if (distance > 1.0e-4 && distance < strength.radius() + ParticleAvoidance.RECOVERY) {
            double inv = 1.0 / distance;
            double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
            double dot = (-Math.sin(yaw) * Math.cos(pitch) * dx - Math.sin(pitch) * dy
                    + Math.cos(yaw) * Math.cos(pitch) * dz) * inv;
            // 传 this 作抽样键：轻度 40% / 中度 20% 粒子豁免锥压制（用户 2026-09-26 定稿）
            visibility = ParticleAvoidance.applyAngular(visibility, distance, strength, dot, projectile, this);
        } else if (distance <= 1.0e-4) {
            visibility = ParticleAvoidance.applyAngular(visibility, distance, strength, 1, projectile, this);
        }
        return Math.max(0, Math.min(1, visibility));
    }
}
