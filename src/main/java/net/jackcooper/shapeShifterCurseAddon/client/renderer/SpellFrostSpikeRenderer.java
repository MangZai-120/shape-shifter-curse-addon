package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellFrostSpikeEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

/**
 * 月尘魔法·冰锥渲染器（jackcooper）。按魔法等级切换两种外观：
 * <ul>
 *   <li><b>L1-3</b>：维持现状 —— 委托 {@link FlyingItemEntityRenderer} 渲染雪球物品精灵；</li>
 *   <li><b>L4+</b>：寒棘狐「冰刺」同款 3D 冰锥模型（{@code frost_thorn_model}，CustomModelData=1 的
 *       stage0 材质）与同款大小（0.48 缩放），朝向/中心补偿逻辑照抄 {@link FrostThornEntityRenderer}。</li>
 * </ul>
 * 等级经 DataTracker 同步，多人客机自动一致；实体 tick 已按速度自算 yaw/pitch（尖朝速度方向）。
 */
@Environment(EnvType.CLIENT)
public class SpellFrostSpikeRenderer extends EntityRenderer<SpellFrostSpikeEntity> {
	// L1-3 委托的雪球渲染器（复用其逻辑，避免自己重写物品精灵渲染）
	private final FlyingItemEntityRenderer<SpellFrostSpikeEntity> snowballRenderer;
	// L4+ 渲染用（CustomModelData 恒 1，静态复用，避免每帧 new + NBT 写入）
	private static ItemStack CACHED_STACK;
	// 冰锥渲染大小：模型本体长约 0.6 格，0.9 缩放后全长约 0.55 格 —— 比雪球大一圈的显眼冰锥，
	// 仍小于寒棘狐凝棘满级强化锥（1.5），保持视觉层级：魔法书投射物 < SP 形态技能体
	private static final float SCALE = 0.9f;

	private final ItemRenderer itemRenderer;

	public SpellFrostSpikeRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.snowballRenderer = new FlyingItemEntityRenderer<>(ctx, 1.0F, true);
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(SpellFrostSpikeEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		if (entity.getSpellLevel() < 4) {
			// L1-3：维持现状（雪球物品精灵）
			snowballRenderer.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
			return;
		}
		// L4+：寒棘狐同款 3D 冰锥
		matrices.push();
		matrices.scale(SCALE, SCALE, SCALE);
		// 模型尖朝 -Z（长腰尖在 z=-2 端）：绕 Y 转 -yaw+180 对准水平朝向；180° 翻转使局部 X 轴镜像，pitch 取反
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw() + 180.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-entity.getPitch()));
		// 模型中心补偿：frost_thorn 模型几何中心在网格 (1.5, 0.5, 3) 而非标准 (8,8,8)，
		// 在旋转后的局部空间里平移，把模型中心对回实体锚点（与旋转无关，始终居中）
		matrices.translate((8.0 - 1.5) / 16.0, (8.0 - 0.5) / 16.0, (8.0 - 3.0) / 16.0);
		if (CACHED_STACK == null) {
			CACHED_STACK = new ItemStack(SscAddon.FROST_THORN);
			CACHED_STACK.getOrCreateNbt().putInt("CustomModelData", 1);
		}
		this.itemRenderer.renderItem(CACHED_STACK, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(SpellFrostSpikeEntity entity) {
		// L1-3 委托雪球渲染器（内部自带贴图）；此方法仅基类外部调用（如阴影），给安全默认值
		return snowballRenderer.getTexture(entity);
	}
}
