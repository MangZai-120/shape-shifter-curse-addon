package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

/**
 * 冰刺冰锥 3D 渲染器：用 {@code frost_thorn} 物品的自定义 3D item model 渲染（照 water_spear 式 CustomModelData 路线，零新依赖）。
 * 朝向随实体 yaw/pitch 摆正，使冰锥尖端朝飞行 / 面向方向；CustomModelData 随存在时间阶段（0/1/2）切换 3 阶段材质。
 */
@Environment(EnvType.CLIENT)
public class FrostThornEntityRenderer extends EntityRenderer<FrostThornEntity> {

	private final ItemRenderer itemRenderer;

	public FrostThornEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(FrostThornEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		// 凝棘强化冰锥飞出大小 = 法阵内合成时中央冰锥大小（0.25×(1+level)，所见即所得）；普通冰刺 level=0 保持 0.48
		float s = entity.getLevel() > 0 ? 0.25f * (1.0f + entity.getLevel()) : 0.48f;
		matrices.scale(s, s, s);
		// 模型尖朝 -Z（长腰尖在 z=-2 端）：绕 Y 转 -yaw+180 对准水平朝向；180° 翻转使局部 X 轴镜像，pitch 取反
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw() + 180.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-entity.getPitch()));
		// 模型中心补偿：新模型几何中心在网格 (1.5, 0.5, 3) 而非标准 (8,8,8)，渲染会整体偏向实体一侧；
		// 在旋转后的局部空间里平移，把模型中心对回实体锚点（与旋转无关，始终居中）
		matrices.translate((8.0 - 1.5) / 16.0, (8.0 - 0.5) / 16.0, (8.0 - 3.0) / 16.0);
		// CustomModelData = 存在阶段 + 1（1/2/3 → stage0/1/2 三阶段材质）
		ItemStack stack = new ItemStack(SscAddon.FROST_THORN);
		stack.getOrCreateNbt().putInt("CustomModelData", entity.getStage() + 1);
		this.itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(FrostThornEntity entity) {
		return new Identifier("textures/atlas/blocks.png");
	}
}
