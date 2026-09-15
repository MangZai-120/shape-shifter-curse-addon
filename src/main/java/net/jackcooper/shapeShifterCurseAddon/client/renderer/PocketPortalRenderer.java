package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceBlocks;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceLayout;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public final class PocketPortalRenderer implements BlockEntityRenderer<PocketSpaceBlocks.PortalBlockEntity> {
	private static final Identifier EMISSION = new Identifier("ssc_addon", "textures/block/pocket_space_portal_emission.png");

	public PocketPortalRenderer(BlockEntityRendererFactory.Context context) {}

	public static void register() {
		BlockEntityRendererFactories.register(PocketSpaceBlocks.PortalBlockEntity.TYPE, PocketPortalRenderer::new);
	}

	@Override
	public void render(PocketSpaceBlocks.PortalBlockEntity entity, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider consumers, int light, int overlay) {
		int rotation = switch (entity.getCachedState().get(PocketSpaceBlocks.PORTAL_FACING)) {
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> 0;
		};
		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotation));
		matrices.translate(-0.5, 0, -0.5);
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(EMISSION));
		MatrixStack.Entry entry = matrices.peek();
		float inset = (float) PocketSpaceLayout.PORTAL_INSET;
		vertex(buffer, entry, inset, inset, inset, inset);
		vertex(buffer, entry, inset, 1, inset, 1);
		vertex(buffer, entry, 1, 1, 1, 1);
		vertex(buffer, entry, 1, inset, 1, inset);
		matrices.pop();
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float horizontal, float depth,
	                           float textureU, float textureV) {
		buffer.vertex(entry.getPositionMatrix(), horizontal, (float) PocketSpaceLayout.PORTAL_TOP_HEIGHT + 0.001f, depth)
				.color(255, 255, 255, 255)
				.texture(textureU, textureV).overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry.getNormalMatrix(), 0, 1, 0).next();
	}
}