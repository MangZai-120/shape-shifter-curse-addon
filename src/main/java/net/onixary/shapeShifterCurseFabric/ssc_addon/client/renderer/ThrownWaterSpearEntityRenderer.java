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
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ThrownWaterSpearEntity;

/**
 * 进化美西螈「投掷水矛」直线水矛的 3D 渲染器：渲染水矛投掷态 3D 模型（water_spear_throwing），
 * 沿飞行方向摆正，与手持/正常水矛投掷视觉一致。
 */
@Environment(EnvType.CLIENT)
public class ThrownWaterSpearEntityRenderer extends EntityRenderer<ThrownWaterSpearEntity> {
	private final ItemRenderer itemRenderer;

	public ThrownWaterSpearEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(ThrownWaterSpearEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.scale(1.4f, 1.4f, 1.4f);
		// 用同步的 velocity 计算朝向（yaw/pitch 对投射物常不同步）
		net.minecraft.util.math.Vec3d vel = entity.getVelocity();
		double horiz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		float vYaw = (float) (MathHelper.atan2(vel.x, vel.z) * (180.0 / Math.PI));
		float vPitch = (float) (MathHelper.atan2(vel.y, horiz) * (180.0 / Math.PI));
		// 与 WaterSpearEntityRenderer 一致的摆正：Y -90 + Z -90，使 3D 水矛尖端朝飞行方向
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(vYaw - 90.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(vPitch - 90.0F));

		// CustomModelData=1 触发 water_spear_throwing（3D 投掷态）模型
		ItemStack renderStack = new ItemStack(SscAddon.WATER_SPEAR);
		renderStack.getOrCreateNbt().putInt("CustomModelData", 1);
		this.itemRenderer.renderItem(renderStack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());

		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(ThrownWaterSpearEntity entity) {
		return new Identifier("textures/atlas/blocks.png");
	}
}
