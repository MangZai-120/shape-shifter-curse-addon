package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.layer;

import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

/**
 * 女巫使魔发光眼睛渲染层 — 使用与MC蜘蛛完全一致的 RenderLayer.getEyes()
 * 特性：无光照计算 + 加法透明 + 黑暗中可见
 */
@SuppressWarnings("deprecation")
public class WitchFamiliarEyesLayer extends GeoRenderLayer<WitchFamiliarEntity> {

    // 眼睛发光纹理（只包含眼睛像素，其余透明）
    private static final Identifier EYES_TEXTURE =
            new Identifier("ssc_addon", "textures/entity/witch_familiar_eyes.png");

    public WitchFamiliarEyesLayer(GeoEntityRenderer<WitchFamiliarEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(MatrixStack poseStack, WitchFamiliarEntity animatable,
                       BakedGeoModel bakedModel, RenderLayer renderType,
                       VertexConsumerProvider bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        // 使用蜘蛛眼睛的渲染类型（无光照 + 加法透明）
        RenderLayer eyesRenderType = RenderLayer.getEyes(EYES_TEXTURE);
        VertexConsumer eyesBuffer = bufferSource.getBuffer(eyesRenderType);

        getRenderer().reRender(
                bakedModel,
                poseStack,
                bufferSource,
                animatable,
                eyesRenderType,
                eyesBuffer,
                partialTick,
                15728640, // 全亮度（LightmapTextureManager.MAX_LIGHT_COORDINATE）
                LivingEntityRenderer.getOverlay(animatable, 0),
                1.0f, 1.0f, 1.0f, 1.0f
        );
    }
}
