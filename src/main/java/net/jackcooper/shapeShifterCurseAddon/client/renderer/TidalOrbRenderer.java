package net.jackcooper.shapeShifterCurseAddon.client.renderer;

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
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.entity.TidalOrbEntity;

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
        // 悬停粒子客户端自绘（零网络包；时机/频率/几何全部照抄服务端原版，见方法注释）
        spawnHoverParticlesClient(entity);
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

    // ===== 悬停粒子配色（与服务端 CYAN/BLUE/LIGHT_BLUE_DUST 常量逐项一致） =====
    private static final org.joml.Vector3f C_CYAN = new org.joml.Vector3f(0.20f, 0.78f, 0.92f);
    private static final org.joml.Vector3f C_BLUE = new org.joml.Vector3f(0.25f, 0.45f, 0.95f);
    private static final org.joml.Vector3f C_LIGHT = new org.joml.Vector3f(0.55f, 0.80f, 1.0f);

    /**
     * 悬停粒子客户端自绘：<b>逐行照抄服务端 spawnHoverParticles + spawnBoundaryOrbs</b>（网络包归零）。
     *
     * <p>与服务端一致的三条保证：
     * <ul>
     *   <li><b>时机</b>：仅拴人激活期（{@code isTetherActive()}，DataTracker 同步，恰好覆盖服务端
     *       ATTRACTING 全期 + 普通球 DELAY 期；增强球爆炸即置 false 停发——与服务端判定完全等价）；</li>
     *   <li><b>频率</b>：每实体 tick 一次（{@code clientParticleGate} 门控，render 每帧调用不超频）；</li>
     *   <li><b>几何/数量/颜色</b>：全部参数原样照抄（3 球×4 粒、气泡 40% 概率、核心光点、水滴隔 tick、
     *       边界 6 球×3 粒），随机分布用与服务端同公式的 randomInSphere。</li>
     * </ul></p>
     */
    private void spawnHoverParticlesClient(TidalOrbEntity entity) {
        // 门控 + 时机：同 tick 只发一次；非拴人期不发（与服务端 tickAttracting/tickDelay 的调用条件一致）
        if (entity.clientParticleGate == entity.age) return;
        entity.clientParticleGate = entity.age;
        if (!entity.isTetherActive()) return;

        net.minecraft.client.particle.ParticleManager pm = net.minecraft.client.MinecraftClient.getInstance().particleManager;
        java.util.Random rnd = new java.util.Random();
        int t = entity.age; // 客户端实体年龄，每 tick +1，与服务端 ticksAlive 同速率
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();

        // —— 以下逐行照抄服务端 spawnHoverParticles ——
        // 三个小粒子球绕锚点旋转（120° 均布，各用一种水系配色）
        double rot = t * 0.12;
        double orbitR = 1.1;
        for (int k = 0; k < 3; k++) {
            double a = rot + k * (Math.PI * 2 / 3);
            double ox = x + Math.cos(a) * orbitR;
            double oz = z + Math.sin(a) * orbitR;
            double oy = y + Math.sin(t * 0.15 + k * 2.0) * 0.25; // 上下轻微起伏
            org.joml.Vector3f col = (k == 0) ? C_CYAN : (k == 1) ? C_BLUE : C_LIGHT;
            for (int i = 0; i < 4; i++) {  // 服务端原值 4（上次误改 2，已纠正）
                Vec3d p = randomInSphere(0.22, rnd);
                pm.addParticle(new net.minecraft.particle.DustParticleEffect(col, 1.5f), ox + p.x, oy + p.y, oz + p.z, 0, 0, 0);
            }
            // 服务端原有 40% 概率 BUBBLE——已删：白色小点上浮观感差（用户主诉），保留环绕 dust 即可
        }
        // 核心发光 END_ROD——已删：白色小光点每 tick 上飘（用户主诉的「停止时中间快速上飘白点」就是它；
        // 拴人期中央已有渲染器画的激活态潮涌核心，不需要粒子层再叠光点）
        // 悬停粒子里的 40% 概率 BUBBLE 同步保留删除（同为上浮白点，与主诉同视觉）
        // 中间生成、随重力慢慢下落的水滴
        if (t % 2 == 0) {
            double dx = (rnd.nextDouble() - 0.5) * 0.5;
            double dz = (rnd.nextDouble() - 0.5) * 0.5;
            pm.addParticle(net.minecraft.particle.ParticleTypes.FALLING_WATER, x + dx, y + 0.15, z + dz, 0, 0, 0);
        }
        // —— 以下逐行照抄服务端 spawnBoundaryOrbs ——
        // 6 格范围提示：公转粒子球（与中央三球同一套视觉语言）
        double rot2 = t * 0.06;   // 慢速公转
        int n = 6;                // 6 个球均布在 6 格边界
        double softR = TidalOrbEntity.tetherSoftRadius();
        for (int k = 0; k < n; k++) {
            double a = rot2 + k * (Math.PI * 2 / n);
            double ox = x + Math.cos(a) * softR;
            double oz = z + Math.sin(a) * softR;
            double oy = y + Math.sin(t * 0.12 + k) * 0.3; // 上下起伏
            org.joml.Vector3f col = (k % 3 == 0) ? C_CYAN : (k % 3 == 1) ? C_BLUE : C_LIGHT;
            for (int i = 0; i < 3; i++) {  // 服务端原值 3（上次误改 2，已纠正）
                Vec3d p = randomInSphere(0.2, rnd);
                pm.addParticle(new net.minecraft.particle.DustParticleEffect(col, 1.5f), ox + p.x, oy + p.y, oz + p.z, 0, 0, 0);
            }
        }
    }

    /** 与服务端 TidalOrbEntity.randomInSphere 同公式（球内均匀分布）。 */
    private static Vec3d randomInSphere(double r, java.util.Random rnd) {
        double rr = r * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * 2 * Math.PI;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vec3d(rr * sinPhi * Math.cos(theta), rr * Math.cos(phi), rr * sinPhi * Math.sin(theta));
    }
}
