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
}
