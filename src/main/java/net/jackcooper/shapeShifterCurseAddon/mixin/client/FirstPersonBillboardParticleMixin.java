package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.jackcooper.shapeShifterCurseAddon.client.particle.OwnedDecoration;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleAvoidance;
import net.jackcooper.shapeShifterCurseAddon.client.particle.AsyncParticleCompatibility;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/** Metadata and field access only: Sodium replaces buildGeometry, so never inject inside it. */
@Mixin(BillboardParticle.class)
public abstract class FirstPersonBillboardParticleMixin extends Particle implements OwnedDecoration {
    @Unique private UUID ssca$owner;

    protected FirstPersonBillboardParticleMixin(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Shadow public abstract float getSize(float tickDelta);

    @Override
    public void ssca$setDecorationOwner(UUID owner) {
        ssca$owner = owner;
        if (owner != null) AsyncParticleCompatibility.prepare(this);
    }

    @Override
    public UUID ssca$getDecorationOwner() { return ssca$owner; }

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
        if (ssca$owner == null) return 1;
        var client = MinecraftClient.getInstance();
        if (client.player == null || camera.getFocusedEntity() != client.player
                || !client.options.getPerspective().isFirstPerson() || camera.isThirdPerson()
                || !ssca$owner.equals(client.player.getUuid())) return 1;
        var strength = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig().firstPersonParticleAvoidance;
        if (strength == null || strength == ParticleAvoidance.Strength.OFF) return 1;
        var eye = camera.getPos();
        double dx = MathHelper.lerp(tickDelta, prevPosX, x) - eye.x;
        double dy = MathHelper.lerp(tickDelta, prevPosY, y) - eye.y;
        double dz = MathHelper.lerp(tickDelta, prevPosZ, z) - eye.z;
        double squaredDistance = dx * dx + dy * dy + dz * dz;
        if (squaredDistance >= (strength.radius() + 2.0) * (strength.radius() + 2.0)) return 1;
        double distance = Math.sqrt(squaredDistance);
        // 近距抽样区：< 1.5 格时抽 20% 显示（25% 影子透明度），其余 80% 隐藏
        if (distance < ParticleAvoidance.NEAR_ZONE) {
            return ParticleAvoidance.keepNearSample(this) ? ParticleAvoidance.NEAR_VISIBILITY : 0;
        }
        float visibility = ParticleAvoidance.visibility(strength, true, true, distance, getSize(tickDelta));
        if (visibility < 1 && distance > 1.0e-4) {
            // 准星 30° 锥内额外 -25%，防挡准星（方向向量 = 粒子位置 - 镜头）
            double inv = 1.0 / distance;
            visibility *= ParticleAvoidance.crosshairConeFactor(camera, distance, dx * inv, dy * inv, dz * inv);
        }
        return Math.max(0, Math.min(1, visibility));
    }
}
