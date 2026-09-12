package net.jackcooper.shapeShifterCurseAddon.client.renderer.layer;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;

/**
 * 月灵发光 overlay 层（jackcooper）：与本体同 geo，采样三色变体的 *_overlay 发光贴图，
 * 蜘蛛眼同款 RenderLayer.getEyes()（无光照 + 加法透明 + 黑暗中可见）——
 * 月纹/翅脉自发光。照抄项目内 WitchFamiliarEyesLayer 已验证范式。
 */
public class LunarSpiritOverlayLayer extends GeoRenderLayer<LunarSpiritEntity> {

	private static final Identifier[] OVERLAY_TEXTURES = {
			new Identifier("my_addon", "textures/entity/moon_spirit_pink_overlay.png"),
			new Identifier("my_addon", "textures/entity/moon_spirit_cyan_overlay.png"),
			new Identifier("my_addon", "textures/entity/moon_spirit_green_overlay.png")
	};

	public LunarSpiritOverlayLayer(GeoEntityRenderer<LunarSpiritEntity> renderer) {
		super(renderer);
	}

	@Override
	public void render(MatrixStack poseStack, LunarSpiritEntity animatable,
	                   BakedGeoModel bakedModel, RenderLayer renderType,
	                   VertexConsumerProvider bufferSource, VertexConsumer buffer,
	                   float partialTick, int packedLight, int packedOverlay) {
		int variant = Math.max(0, Math.min(2, animatable.getVariant()));
		RenderLayer eyesRenderType = RenderLayer.getEyes(OVERLAY_TEXTURES[variant]);
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
