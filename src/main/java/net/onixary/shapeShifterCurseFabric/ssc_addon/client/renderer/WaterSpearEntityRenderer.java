package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearEntity;

@Environment(EnvType.CLIENT)
public class WaterSpearEntityRenderer extends EntityRenderer<WaterSpearEntity> {
	private final ItemRenderer itemRenderer;

	public WaterSpearEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(WaterSpearEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		// Fixed rotation: -90.0F to match vanilla direction
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch()) - 90.0F));

		ItemStack stack = entity.getWeaponStack();
		if (stack != null && !stack.isEmpty()) {
			// Use a copy of the stack to avoid modifying the actual item NBT (which would persist when picked up)
			ItemStack renderStack = stack.copy();
			renderStack.getOrCreateNbt().putInt("CustomModelData", 1);
			this.itemRenderer.renderItem(renderStack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), entity.getId());
		}

		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(WaterSpearEntity entity) {
		return new Identifier("textures/atlas/blocks.png");
	}
}
