package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.TidalOrbEntity;

/**
 * 荧光幼灵潮汐球渲染器。
 *
 * <p>非拴人期：完全沿用 {@link FlyingItemEntityRenderer}（潮涌方块物品作发光核心，外观与原来一致）。
 * <p>拴人（激活）期：把核心换成「激活态潮涌核心」——开壳 cage + 旋转 wind 风纹 + 朝相机发光 open_eye，
 * 与原版激活潮涌一致。激活状态由实体 {@code DataTracker} 同步，多人一致。
 */
@Environment(EnvType.CLIENT)
public class TidalOrbRenderer extends FlyingItemEntityRenderer<TidalOrbEntity> {

    // 直接指向原版潮涌贴图文件（不走图集，避免图集常量在版本间的不确定性）
    private static final Identifier WIND_TEX = new Identifier("textures/entity/conduit/wind.png");
    private static final Identifier WIND_VERTICAL_TEX = new Identifier("textures/entity/conduit/wind_vertical.png");
    private static final Identifier OPEN_EYE_TEX = new Identifier("textures/entity/conduit/open_eye.png");

    private final ModelPart eye;
    private final ModelPart wind;

    public TidalOrbRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, 1.0F, false); // 与原注册一致：scale 1.0、非自发光
        this.eye = ctx.getPart(EntityModelLayers.CONDUIT_EYE);
        this.wind = ctx.getPart(EntityModelLayers.CONDUIT_WIND);
    }

    @Override
    public void render(TidalOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        // 悬停粒子客户端自绘（零网络包）：三色环绕球 + 边界公转球 + 核心发光 + 水滴。
        // 几何全部由「实体位置（已同步）+ 世界时间（已同步）」决定，多人天然一致。
        spawnHoverParticlesClient(entity, tickDelta);
        if (!entity.isTetherActive()) {
            // 非拴人期：完全沿用原版飞行物品渲染，外观零变化
            super.render(entity, yaw, tickDelta, matrices, vcp, light);
            return;
        }
        // 拴人期：渲染「激活态潮涌核心」（旋转风纹 + 朝相机发光眼）
        long time = entity.getWorld().getTime();
        int fullBright = 0xF000F0;

        matrices.push();
        matrices.translate(0.0, 0.15, 0.0);   // 微抬到球体中心
        matrices.scale(0.75f, 0.75f, 0.75f);    // 整体尺寸（较初版放大 50%）

        // wind（风纹，三向循环 + 自转，营造激活漩涡）
        int frame = (int) (time / 22) % 3;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(((float) time + tickDelta) * 2.5f));
        if (frame == 1) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        } else if (frame == 2) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f));
        }
        Identifier windTex = (frame == 0) ? WIND_TEX : WIND_VERTICAL_TEX;
        this.wind.render(matrices,
                vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(windTex)),
                fullBright, OverlayTexture.DEFAULT_UV);
        matrices.pop();

        // open_eye（朝相机、发光）
        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());
        matrices.scale(0.5f, 0.5f, 0.5f);
        this.eye.render(matrices,
                vcp.getBuffer(RenderLayer.getEntityTranslucent(OPEN_EYE_TEX)),
                fullBright, OverlayTexture.DEFAULT_UV);
        matrices.pop();

        matrices.pop();
        // 拴人期不调用 super：中央只显示激活态潮涌核心
    }

    // ===== 悬停粒子配色（与服务端原实现一致的三色 dust） =====
    private static final org.joml.Vector3f C_CYAN = new org.joml.Vector3f(0.20f, 0.78f, 0.92f);
    private static final org.joml.Vector3f C_BLUE = new org.joml.Vector3f(0.25f, 0.45f, 0.95f);
    private static final org.joml.Vector3f C_LIGHT = new org.joml.Vector3f(0.55f, 0.80f, 1.0f);

    /**
     * 悬停粒子客户端自绘（原服务端逐 tick 广播已移除，网络包归零）。
     * 用 MinecraftClient 粒子管理器直接添加（addParticle → ParticleManager.addParticle 路径），
     * 保持原 ParticleS2CPacket force=true「无视客户端粒子设置」的视觉行为。
     * 每 render 帧调用一次（帧率 60+ 时相当于每 tick 1-3 次，粒子寿命 ~1s 自然衔接）。
     */
    private void spawnHoverParticlesClient(TidalOrbEntity entity, float tickDelta) {
        net.minecraft.client.particle.ParticleManager pm = net.minecraft.client.MinecraftClient.getInstance().particleManager;
        java.util.Random rnd = new java.util.Random();
        long t = entity.age; // 实体年龄（客户端已知，替代原服务端 ticksAlive）
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        // 每帧粒子上限（防高帧率下过量）：环绕 3 球×2 粒 + 边界 6 球×2 粒 + 核心 1 + 水滴 1
        // 三个小粒子球绕锚点旋转（120° 均布，各用一种水系配色）
        double rot = t * 0.12;
        for (int k = 0; k < 3; k++) {
            double a = rot + k * (Math.PI * 2 / 3);
            double ox = x + Math.cos(a) * 1.1, oz = z + Math.sin(a) * 1.1;
            double oy = y + Math.sin(t * 0.15 + k * 2.0) * 0.25;
            org.joml.Vector3f col = (k == 0) ? C_CYAN : (k == 1) ? C_BLUE : C_LIGHT;
            for (int i = 0; i < 2; i++) {
                double px = ox + (rnd.nextDouble() - 0.5) * 0.44, py = oy + (rnd.nextDouble() - 0.5) * 0.44, pz = oz + (rnd.nextDouble() - 0.5) * 0.44;
                pm.addParticle(new net.minecraft.particle.DustParticleEffect(col, 1.5f), px, py, pz, 0, 0, 0);
            }
        }
        // 核心发光
        pm.addParticle(net.minecraft.particle.ParticleTypes.END_ROD, x, y, z, 0.08, 0.1, 0.08);
        // 边界 6 格公转提示球（慢速公转 + 上下起伏）
        double rot2 = t * 0.06;
        for (int k = 0; k < 6; k++) {
            double a = rot2 + k * (Math.PI * 2 / 6);
            double ox = x + Math.cos(a) * TidalOrbEntity.tetherSoftRadius();
            double oz = z + Math.sin(a) * TidalOrbEntity.tetherSoftRadius();
            double oy = y + Math.sin(t * 0.12 + k) * 0.3;
            org.joml.Vector3f col = (k % 3 == 0) ? C_CYAN : (k % 3 == 1) ? C_BLUE : C_LIGHT;
            for (int i = 0; i < 2; i++) {
                double px = ox + (rnd.nextDouble() - 0.5) * 0.4, py = oy + (rnd.nextDouble() - 0.5) * 0.4, pz = oz + (rnd.nextDouble() - 0.5) * 0.4;
                pm.addParticle(new net.minecraft.particle.DustParticleEffect(col, 1.5f), px, py, pz, 0, 0, 0);
            }
        }
        // 中间生成、随重力慢慢下落的水滴
        pm.addParticle(net.minecraft.particle.ParticleTypes.FALLING_WATER,
                x + (rnd.nextDouble() - 0.5) * 0.5, y + 0.15, z + (rnd.nextDouble() - 0.5) * 0.5, 0, 0, 0);
    }
}
