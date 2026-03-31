package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.model.WitchFamiliarModel;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

/**
 * 女巫使魔渲染器
 * AzureLib GeoEntityRenderer 原生支持基岩版Geo模型，不需要额外坐标变换
 */
public class WitchFamiliarRenderer extends GeoEntityRenderer<WitchFamiliarEntity> {

    private static final float MODEL_SCALE = 0.45f;

    public WitchFamiliarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new WitchFamiliarModel());
        this.shadowRadius = 0.2f;
    }

    @Override
    public void preRender(MatrixStack poseStack, WitchFamiliarEntity animatable, BakedGeoModel model,
                          VertexConsumerProvider bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
