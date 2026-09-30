package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * 变羊渲染的客户端主体（羊了个羊，jackcooper，2026-09-29 成年羊 geo 双层模型定稿）。
 *
 * <p><b>渲染架构（2026-09-29 用户定稿换成年羊 geo 双层模型）</b>：用户提供的原版成年羊
 * 拆分 geo——本体（{@code form_sheep_spell_form.geo.json}，无 inflate）+ 羊毛外层
 * （{@code form_sheep_spell_form_wool.geo.json}，body inflate 1.75 / head 0.6 / 腿 0.5，
 * inflate 已烘进 geo 无需运行时放大）。两份 geo 骨骼同名（body/rotation/head/leg1~4），
 * 姿势用同一组公式分别驱动。贴图：本体 {@code sheep.png} + 羊毛 {@code sheep_wool.png}
 *（{@code sheep_wool_undercoat.png} 是 1.20.2+ 剪毛绒层贴图，1.20.1 不用）。</p>
 *
 * <p><b>实现方式</b>：复用 SSC 原版 {@link FormRenderer}（GeoObjectRenderer 子类）双实例
 * ——先渲染本体、再渲染羊毛（inflate 外层包裹本体，遮挡顺序天然正确）。渲染链照抄 SSC
 * FormRenderFeature.renderGeoBone 的已验证模式（含 preRender 的 -0.5/-0.51/-0.5 平移
 * 补偿约定）。姿势驱动：头按视角转、四腿按 limbAnimator 摆动（等价原版 QuadrupedEntityModel 公式）。</p>
 *
 * <p>由 {@code SheepFormRenderMixin}（HEAD cancellable 注入 LivingEntityRenderer.render）
 * 调用——mixin 类只留注入器，共享状态放本普通类（Mixin APPLY 合并冲突教训：
 * mixin 内 public static 方法 → non-private method 合并失败崩服）。</p>
 */
@Environment(EnvType.CLIENT)
public final class SheepFormClientHooks {

	private SheepFormClientHooks() {}

	// ===== 资源常量（my_addon 命名空间）=====
	private static final Identifier GEO_BODY = new Identifier("my_addon", "geo/form_sheep_spell_form.geo.json");
	private static final Identifier GEO_WOOL = new Identifier("my_addon", "geo/form_sheep_spell_form_wool.geo.json");
	private static final Identifier TEX_BODY = new Identifier("my_addon", "textures/form_sheep_spell_form/sheep.png");
	private static final Identifier TEX_WOOL = new Identifier("my_addon", "textures/form_sheep_spell_form/sheep_wool.png");

	/** 双层渲染器（初始化一次静态复用；渲染线程只读）。 */
	private static FormRenderer bodyRenderer;
	private static FormRenderer woolRenderer;

	/** 客户端初始化入口（幂等；由咩弹渲染器工厂首次构造时调用，见 SscAddonClient）。 */
	public static void init(EntityRendererFactory.Context ctx) {
		if (bodyRenderer == null) {
			bodyRenderer = buildRenderer(GEO_BODY, TEX_BODY);
		}
		if (woolRenderer == null) {
			woolRenderer = buildRenderer(GEO_WOOL, TEX_WOOL);
		}
	}

	/** 按 SSC ssc_form_model JSON 约定构建 FormRenderer（最小字段：model + texture + hidden）。 */
	private static FormRenderer buildRenderer(Identifier geo, Identifier texture) {
		JsonObject json = new JsonObject();
		json.addProperty("model", geo.toString());
		json.addProperty("texture", texture.toString());
		// 隐藏玩家原模型全部部件（geo 形态渲染在玩家模型坐标系内，原皮必须全隐）
		json.add("hidden", com.google.gson.JsonParser.parseString(
				"[\"leftArm\",\"rightArm\",\"leftLeg\",\"rightLeg\",\"leftSleeve\",\"rightSleeve\","
						+ "\"rightPants\",\"leftPants\",\"body\",\"jacket\",\"head\",\"hat\"]").getAsJsonArray());
		return new FormRenderer(json);
	}

	/**
	 * 变羊渲染主体：把带 SHEEP_FORM 的活体画成原版成年羊（本体+羊毛双层）。
	 *
	 * @return true = 已接管绘制（调用方取消原渲染）；false = 渲染器未就绪，回落原渲染。
	 */
	public static boolean renderAsSheep(LivingEntity entity, float yaw, float tickDelta,
	                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		if (bodyRenderer == null || woolRenderer == null) {
			return false; // 初始化钩子未跑（世界渲染开始后必已初始化）安全回落
		}

		float limbDistance = Math.min(entity.limbAnimator.getSpeed(tickDelta), 1.0F);
		float limbAngle = entity.limbAnimator.getPos(tickDelta) * 1.3F;
		float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw)
				- MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		float headPitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());

		matrices.push();
		// 实体朝向对齐（geo 模型面朝 -Z，绕 Y 转 180°-yaw 后与实体视线一致）
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		// 体型（2026-09-29 修「太扁」）：geo 像素按 1/16 格绝对映射——用户 geo 本体头顶 22px、
		// 腿底 0px ≈ 1.375 格高，羊毛 inflate 后 ≈1.44 格。目标是成年羊 1.3 格：
		// 缩放 = 1.3 / 1.375 ≈ 0.945（此前误按玩家 32px 模型基准乘 0.677 导致整体压扁）。
		float scale = 1.3F / 1.375F;
		matrices.scale(scale, scale, scale);

		// 双层渲染：先本体后羊毛
		renderLayer(bodyRenderer, GEO_BODY, TEX_BODY, headYaw, headPitch, limbAngle, limbDistance,
				matrices, vertexConsumers, light, tickDelta);
		renderLayer(woolRenderer, GEO_WOOL, TEX_WOOL, headYaw, headPitch, limbAngle, limbDistance,
				matrices, vertexConsumers, light, tickDelta);

		matrices.pop();
		return true;
	}

	/** 单层渲染：姿势驱动 + SSC FormRenderFeature.renderGeoBone 已验证的渲染链。 */
	private static void renderLayer(FormRenderer renderer, Identifier geoId, Identifier texId,
	                                float headYaw, float headPitch, float limbAngle, float limbDistance,
	                                MatrixStack matrices, VertexConsumerProvider vertexConsumers,
	                                int light, float tickDelta) {
		var animatable = renderer.realAnimatable;
		if (animatable == null) return;
		if (renderer.realModel == null) return;

		// 姿势驱动（两份 geo 骨骼同名，同一组公式分别套用）
		BakedGeoModel baked = renderer.realModel.getBakedModel(geoId);
		if (baked == null) return;
		setQuadrupedPose(baked, headYaw, headPitch, limbAngle, limbDistance);

		RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texId);
		var buffer = vertexConsumers.getBuffer(layer);
		int packedOverlay = OverlayTexture.DEFAULT_UV;
		// SSC FormRenderFeature.renderGeoBone 同款链（含 preRender 平移补偿约定）。
		// actuallyRender 接整只 BakedGeoModel（内部递归全部骨骼），比逐 bone renderRecursively 简洁。
		matrices.push();
		matrices.translate(-0.5, -0.51, -0.5);
		renderer.preRender(matrices, animatable, baked, vertexConsumers, buffer, false,
				tickDelta, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
		if (renderer.firePreRenderEvent(matrices, baked, vertexConsumers, tickDelta, light)) {
			renderer.actuallyRender(matrices, animatable, baked, layer, vertexConsumers, buffer, false,
					tickDelta, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
			renderer.postRender(matrices, animatable, baked, vertexConsumers, buffer, false,
					tickDelta, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
			renderer.firePostRenderEvent(matrices, baked, vertexConsumers, tickDelta, light);
		}
		matrices.pop();
	}

	/** 四足姿势驱动（等效原版 QuadrupedEntityModel.setAngles 的头/四腿公式；骨骼名取自用户 geo）。
	 *  头部角度取负号（2026-09-29 实测「头朝向与鼠标相反」修复）：GeckoLib bone 经 Blockbench
	 *  bake 轴变换后，rotY/rotX 正方向与 vanilla ModelPart.yaw/pitch 相反——yaw 正值在 vanilla
	 *  顺时针、在 geo 逆时针；pitch 正值 vanilla=低头、geo=抬头。矩阵层的 180-yaw 已把身体对齐
	 *  （身体朝向正确），头骨相对偏转必须取反才与鼠标指向一致。 */
	private static void setQuadrupedPose(BakedGeoModel baked, float headYaw, float headPitch,
	                                      float limbAngle, float limbDistance) {
		baked.getBone("head").ifPresent(bone -> {
			bone.setRotY(-headYaw * MathHelper.RADIANS_PER_DEGREE);
			bone.setRotX(-headPitch * MathHelper.RADIANS_PER_DEGREE);
		});
		baked.getBone("leg1").ifPresent(b -> b.setRotX(MathHelper.cos(limbAngle) * 1.4F * limbDistance));
		baked.getBone("leg2").ifPresent(b -> b.setRotX(MathHelper.cos(limbAngle + MathHelper.PI) * 1.4F * limbDistance));
		baked.getBone("leg3").ifPresent(b -> b.setRotX(MathHelper.cos(limbAngle + MathHelper.PI) * 1.4F * limbDistance));
		baked.getBone("leg4").ifPresent(b -> b.setRotX(MathHelper.cos(limbAngle) * 1.4F * limbDistance));
	}
}
