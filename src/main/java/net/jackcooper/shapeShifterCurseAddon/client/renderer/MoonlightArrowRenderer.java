package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellMoonlightArrowEntity;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.item.ItemStack;

/**
 * 月尘魔法·月光箭渲染器（jackcooper）。3D 光灵箭模型（{@code moonlight_arrow_render}
 * 隐藏渲染物品 → {@code moonlight_arrow_projectile} 纯 elements JSON，贴图复用原版
 * spectral_arrow），沿实体速度朝向摆正（箭头朝飞行方向）。与冰锥 L4 渲染同范式。
 *
 * <p>实体 tick 已按速度自算 yaw/pitch；模型箭头朝 +Z（网格 z≈14.5 端）、
 * 几何中心在标准 (8,8,8)，无需中心补偿。</p>
 */
@Environment(EnvType.CLIENT)
public class MoonlightArrowRenderer extends EntityRenderer<SpellMoonlightArrowEntity> {
	/** 渲染大小：全长约 0.85 格的显眼箭矢（大于雪球、小于寒棘狐强化锥，保持投射物视觉层级）。 */
	private static final float SCALE = 0.85f;
	/** 渲染用物品栈（静态复用，避免每帧 new）。 */
	private static ItemStack CACHED_STACK;

	private final ItemRenderer itemRenderer;

	public MoonlightArrowRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(SpellMoonlightArrowEntity entity, float yaw, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider vertexConsumers, int light) {
		if (CACHED_STACK == null) {
			CACHED_STACK = new ItemStack(SscAddon.MOONLIGHT_ARROW_RENDER);
		}
		matrices.push();
		matrices.scale(SCALE, SCALE, SCALE);
		// 模型箭头朝 +Z：绕 Y 转 -yaw 对准水平朝向；绕 X 转 pitch 使箭尖随俯仰起落
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getPitch()));
		this.itemRenderer.renderItem(CACHED_STACK, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(SpellMoonlightArrowEntity entity) {
		return new Identifier("minecraft", "textures/item/spectral_arrow.png");
	}
}


