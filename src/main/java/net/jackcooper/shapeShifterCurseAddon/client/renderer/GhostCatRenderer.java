package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.client.model.GhostCatModel;
import net.jackcooper.shapeShifterCurseAddon.entity.GhostCatEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 幽灵野猫渲染器 — 复用 {@code AxolotlShifterRenderer} 的程序化骨骼驱动模式。
 *
 * <p>野猫 geo（form_wild_cat_sp）为人形直立布局（bipedHead/bipedBody/tail_0~3/四肢/耳链，
 * 无 torso 骨骼），四足姿态靠 SSC 动画系统变换。此处按使魔渲染器验证过的做法：
 * bipedBody 前倾 90° 模拟四足 + 腿部后移 + 肢体 BipedEntityModel 等效公式摆动 +
 * 尾/耳链 SSC 拖拽公式。模型面朝 +Z，applyRotations 追加 Ry(180°)。</p>
 */
public class GhostCatRenderer extends GeoEntityRenderer<GhostCatEntity> {

	// 野猫形态实际缩放（RegPlayerForms feral_cat_sp: applyScale(0.75f, 0.6f) 的宽高取值）
	private static final float MODEL_SCALE = 0.75f;
	private static final float HALF_PI = (float) (Math.PI / 2);

	// ==================== 尾巴/耳朵链配置 ====================
	private static final String BODY_TAIL_CHAIN = "tail";
	private static final int BODY_TAIL_LENGTH = 4;   // tail_0 ~ tail_3
	private static final String[] EAR_CHAINS = {"ear_a", "ear_b"};
	// 每条耳链仅 _0 一节（首节直接处理，无需链长常量）

	// 尾巴动态常量（与SSC OriginFurModel一致）
	private static final float SWAY_RATE = 0.33333334f * 0.5f;
	private static final float SWAY_SCALE = 0.05f;

	// ==================== 每实体的尾巴拖拽状态 ====================
	// [0]tailDragAmount [1]tailDragAmountO
	// [2]tailDragAmountVertical [3]tailDragAmountVerticalO
	// [4]currentTailDragAmount(平滑后) [5]currentTailDragAmountVertical(平滑后)
	private static final Map<Integer, float[]> TAIL_STATES = new HashMap<>();
	// ==================== 每帧缓存 ====================
	private float cachedLimbAngle;
	private float cachedLimbDistance;
	private float cachedHeadYaw;
	private float cachedHeadPitch;
	private float cachedPartialTick;
	private float cachedAge;

	public GhostCatRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new GhostCatModel());
		this.shadowRadius = 0.3f;
	}

	@Override
	public void render(GhostCatEntity entity, float entityYaw, float partialTick,
	                   MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
		// 清理已移除实体的尾巴状态（防内存泄漏）
		if (entity.isRemoved()) {
			TAIL_STATES.remove(entity.getId());
			return;
		}

		// 缓存运动参数（与LivingEntityRenderer.render一致）
		cachedPartialTick = partialTick;
		cachedLimbDistance = entity.limbAnimator.getSpeed(partialTick);
		cachedLimbAngle = entity.limbAnimator.getPos(partialTick);
		if (cachedLimbDistance > 1.0f) cachedLimbDistance = 1.0f;

		float headYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevHeadYaw, entity.headYaw);
		float bodyYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
		cachedHeadYaw = headYaw - bodyYaw;
		cachedHeadPitch = MathHelper.lerp(partialTick, entity.prevPitch, entity.getPitch());
		cachedAge = entity.age + partialTick;

		// 插值并平滑尾巴拖拽量（在ProcessModel之前，与SSC render()一致）
		float[] state = TAIL_STATES.computeIfAbsent(entity.getId(), k -> new float[6]);
		float targetDrag = MathHelper.lerp(partialTick, state[1], state[0]);
		state[4] = MathHelper.lerp(0.04f, state[4], targetDrag);
		float targetVerticalDrag = MathHelper.lerp(partialTick, state[3], state[2]);
		state[5] = MathHelper.lerp(0.04f, state[5], targetVerticalDrag);

		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

		// 渲染完成后更新拖拽数据（下一帧使用，与SSC render()一致）
		updateTailDrag(entity, state);
	}

	@Override
	protected void applyRotations(GhostCatEntity animatable, MatrixStack poseStack,
	                              float ageInTicks, float rotationYaw, float partialTick) {
		super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
		// 基岩版模型面朝+Z，追加Ry(180°)修正朝向
		poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
	}

	@Override
	public void preRender(MatrixStack poseStack, GhostCatEntity animatable, BakedGeoModel model,
	                      VertexConsumerProvider bufferSource, VertexConsumer buffer,
	                      boolean isReRender, float partialTick, int packedLight,
	                      int packedOverlay, float red, float green, float blue, float alpha) {
		// reRender（overlay层）会再次调用preRender(isReRender=true)，
		// 此时poseStack已携带首次渲染的缩放和骨骼变换，不能重复应用
		if (!isReRender) {
			poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, red, green, blue, alpha);
		if (!isReRender) {
			// 在动画处理后覆写骨骼变换（等效SSC ProcessModel）
			processModel(animatable);
		}
	}

	// ==================== ProcessModel（等效SSC FurRenderFeature.ProcessModel） ====================

	/**
	 * 骨骼旋转处理——野猫 geo 无 torso，四足姿态按使魔渲染器验证方案：
	 * bipedBody 前倾 90° + 头部/四肢 BipedEntityModel 等效公式 + 尾/耳链拖拽公式。
	 */
	private void processModel(GhostCatEntity entity) {
		// ===== 1. 重置受控骨骼（与SSC resetBone一致）
		resetBone("bipedHead");
		resetBone("bipedBody");
		resetBone("bipedLeftArm");
		resetBone("bipedRightArm");
		resetBone("bipedLeftLeg");
		resetBone("bipedRightLeg");

		// ===== 2. 身体前倾 90°（四足姿态；所有子骨骼——尾链自动继承）
		setRotation("bipedBody", HALF_PI, 0, 0);
		// 位置补偿：前倾后身体重心前移，下移贴回腿部高度（对齐使魔 torso -12 的补偿思路）
		getGeoModel().getBone("bipedBody").ifPresent(bone -> bone.setPosY(-8f));

		// ===== 3. 头部旋转（BipedEntityModel 等效，0.5 系数；身体前倾后头需回正上仰）
		float headPitchRad = cachedHeadPitch * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		float headYawRad = cachedHeadYaw * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		setRotation("bipedHead", headPitchRad - HALF_PI, 0, headYawRad);

		// ===== 4. 前肢（手臂）摆动：前倾后向前伸
		float rightArmPitch = -HALF_PI + MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 2.0f * cachedLimbDistance * 0.5f;
		float leftArmPitch = -HALF_PI + MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 2.0f * cachedLimbDistance * 0.5f;

		// ===== 5. 攻击挥动（等效 BipedEntityModel animateArms）
		float handSwing = entity.getHandSwingProgress(cachedPartialTick);
		float bodyRotY = 0;
		if (handSwing > 0) {
			bodyRotY = MathHelper.sin(MathHelper.sqrt(handSwing) * (float) (Math.PI * 2)) * 0.2f;
			rightArmPitch -= MathHelper.sin(MathHelper.sqrt(handSwing) * (float) Math.PI) * 1.2f;
		}
		setRotation("bipedBody", HALF_PI, bodyRotY, 0);
		setRotation("bipedRightArm", rightArmPitch, 0, 0);
		setRotation("bipedLeftArm", leftArmPitch, 0, 0);

		// ===== 6. 后肢（腿）摆动：身体前倾后腿仍向下，位置后移贴身体后部
		float rightLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 1.4f * cachedLimbDistance;
		float leftLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 1.4f * cachedLimbDistance;
		setRotation("bipedRightLeg", rightLegPitch, 0, 0);
		setRotation("bipedLeftLeg", leftLegPitch, 0, 0);
		getGeoModel().getBone("bipedRightLeg").ifPresent(bone -> bone.setPosZ(-14f));
		getGeoModel().getBone("bipedLeftLeg").ifPresent(bone -> bone.setPosZ(-14f));

		// ===== 7. 尾巴/耳朵动态旋转
		float[] state = TAIL_STATES.getOrDefault(entity.getId(), new float[6]);
		setTailRotations(cachedLimbAngle, cachedLimbDistance, cachedAge, state[4], state[5]);
		setHeadTailRotations(cachedHeadYaw, cachedAge, state[4], state[5]);
	}

	// ==================== 尾巴动态系统（逐行对应SSC OriginFurModel Feral模式） ====================

	/**
	 * 身体尾链旋转（等效SSC setRotationForTailBones Feral模式——四足姿态用 rotZ 水平摆动）
	 */
	private void setTailRotations(float limbAngle, float limbDistance, float age,
	                              float tailDragAmount, float tailDragAmountVertical) {
		Optional<GeoBone> firstBoneOpt = getGeoModel().getBone(BODY_TAIL_CHAIN + "_0");
		if (firstBoneOpt.isEmpty()) return;
		GeoBone tail = firstBoneOpt.get();

		float tailSway = SWAY_SCALE * MathHelper.cos(age * SWAY_RATE + ((float) Math.PI / 3.0f) * 0.75f);
		float tailBalance = MathHelper.cos(limbAngle * 0.6662f) * 0.325f * limbDistance;
		// Feral模式：rotZ 水平摆动（四足姿态，Z轴=前后摆）
		tail.setRotZ(MathHelper.lerp(limbDistance, tailSway, tailBalance) + tailDragAmount * 0.75f);
		tail.setRotX(-tailDragAmountVertical * 0.75f);

		float offset = 0.0f;
		for (int i = 1; i < BODY_TAIL_LENGTH; i++) {
			Optional<GeoBone> nextBone = getGeoModel().getBone(BODY_TAIL_CHAIN + "_" + i);
			if (nextBone.isEmpty()) continue;
			GeoBone bone = nextBone.get();
			bone.setRotZ(MathHelper.lerp(limbDistance,
					SWAY_SCALE * MathHelper.cos(age * SWAY_RATE - ((float) Math.PI / 3.0f) * offset),
					0.0f) + tailDragAmount * 0.75f);
			bone.setRotX(-tailDragAmountVertical * 0.75f * (offset + 0.75f));
			offset += 0.75f;
		}
	}

	/**
	 * 耳朵链旋转（等效SSC setRotationForHeadTailBones；头是独立骨骼不受身体前倾影响）
	 */
	private void setHeadTailRotations(float headAngle, float age,
	                                  float tailDragAmount, float tailDragAmountVertical) {
		for (String prefix : EAR_CHAINS) {
			Optional<GeoBone> firstBone = getGeoModel().getBone(prefix + "_0");
			if (firstBone.isEmpty()) continue;
			GeoBone ear = firstBone.get();
			float tailSway = SWAY_SCALE * MathHelper.cos(age * SWAY_RATE
					+ ((float) Math.PI / 3.0f) * 0.75f);
			float tailBalance = MathHelper.cos(headAngle * 0.6662f) * 0.325f * 0.1f;
			ear.setRotY(-MathHelper.lerp(0.1f, tailSway, tailBalance) - tailDragAmount * 0.75f);
			ear.setRotX(-tailDragAmountVertical * 0.75f);
		}
	}

	// ==================== 尾巴物理更新（逐行对应SSC FurRenderFeature.render()尾部计算） ====================

	private void updateTailDrag(GhostCatEntity entity, float[] state) {
		// 水平拖拽：身体偏航变化驱动，0.75倍衰减
		state[1] = state[0];
		state[0] *= 0.75f;
		state[0] -= (float) (Math.toRadians(entity.bodyYaw - entity.prevBodyYaw) * 0.55f);
		state[0] = MathHelper.clamp(state[0], -1.6f, 1.6f);

		// 垂直拖拽：实体垂直速度驱动，0.8倍衰减
		float verticalSpeed = (float) entity.getVelocity().y;
		float targetVerticalDrag = MathHelper.clamp(verticalSpeed * 1.5f, -1.6f, 1.6f);
		state[3] = state[2];
		state[2] *= 0.8f;
		state[2] += targetVerticalDrag * 0.15f;
		state[2] = MathHelper.clamp(state[2], -1.6f, 1.6f);
	}

	// ==================== 骨骼工具方法（等效SSC OriginFurModel.resetBone/setRotationForBone） ====================

	private void resetBone(String boneName) {
		getGeoModel().getBone(boneName).ifPresent(bone -> {
			bone.setPosX(0);
			bone.setPosY(0);
			bone.setPosZ(0);
			bone.setRotX(0);
			bone.setRotY(0);
			bone.setRotZ(0);
			bone.setScaleX(1);
			bone.setScaleY(1);
			bone.setScaleZ(1);
		});
	}

	private void setRotation(String boneName, float rx, float ry, float rz) {
		getGeoModel().getBone(boneName).ifPresent(bone -> {
			bone.setRotX(rx);
			bone.setRotY(ry);
			bone.setRotZ(rz);
		});
	}
}
