package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.model.WitchFamiliarModel;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.layer.WitchFamiliarEyesLayer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 女巫使魔渲染器 — 完全依据SSC FurRenderFeature.ProcessModel()的骨骼驱动方式
 * <p>
 * 与SSC原版的区别：
 * - SSC使用Rx(180°)+translates（FeatureRenderer叠加在玩家上），需要旋转反转和腿部左右互换
 * - 此处使用GeoEntityRenderer（独立实体），仅Ry(180°)修正朝向，无需反转/互换
 * - SSC从BipedEntityModel读取旋转值，此处用等效公式直接计算
 * - SSC使用static尾巴拖拽字段（单玩家），此处使用per-entity Map
 */
public class WitchFamiliarRenderer extends GeoEntityRenderer<WitchFamiliarEntity> {

	private static final float MODEL_SCALE = 0.45f;
	private static final float HALF_PI = (float) (Math.PI / 2); // 90°弧度

	// ==================== 尾巴/耳朵链配置 ====================
	private static final String[] TAIL_CHAINS = {"tail_r", "tail_l"};
	private static final int TAIL_CHAIN_LENGTH = 5; // tail_r_0 ~ tail_r_4
	private static final String[] EAR_CHAINS = {"ear_a", "ear_b"};
	private static final int EAR_CHAIN_LENGTH = 1; // ear_a_0, ear_b_0

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

	public WitchFamiliarRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new WitchFamiliarModel());
		this.shadowRadius = 0.2f;
		// 蜘蛛式发光眼睛渲染层
		addRenderLayer(new WitchFamiliarEyesLayer(this));
	}

	@Override
	public void render(WitchFamiliarEntity entity, float entityYaw, float partialTick,
	                   MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
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
	protected void applyRotations(WitchFamiliarEntity animatable, MatrixStack poseStack,
	                              float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
		// 基岩版模型面朝+Z，追加Ry(180°)修正朝向
		poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
	}

	@Override
	public void preRender(MatrixStack poseStack, WitchFamiliarEntity animatable, BakedGeoModel model,
	                      VertexConsumerProvider bufferSource, VertexConsumer buffer,
	                      boolean isReRender, float partialTick, int packedLight,
	                      int packedOverlay, float red, float green, float blue, float alpha) {
		// reRender（发光眼睛层等overlay）会再次调用preRender(isReRender=true)，
		// 此时poseStack已携带首次渲染的缩放和骨骼变换，不能重复应用
		if (!isReRender) {
			poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, red, green, blue, alpha);
		if (!isReRender) {
			// 在AzureLib动画处理后覆写骨骼变换（等效SSC ProcessModel）
			processModel(animatable);
		}
	}

	// ==================== ProcessModel（等效SSC FurRenderFeature.ProcessModel） ====================

	/**
	 * 骨骼旋转处理——等效SSC ProcessModel()
	 * 由于GeoEntityRenderer无Rx(180°)坐标翻转：
	 * 1. 不需要invertRotForPart反转Y/Z
	 * 2. 不需要腿部左右互换
	 * 3. 不需要从玩家BipedEntityModel抽取位置偏移
	 * 4. 使用BipedEntityModel.setAngles()等效公式直接计算旋转
	 */
	private void processModel(WitchFamiliarEntity entity) {
		// ===== 1. 重置所有受控骨骼（与SSC resetBone一致：pos=0, rot=0, scale=1） =====
		resetBone("body");
		resetBone("torso");
		resetBone("bipedHead");
		resetBone("bipedBody");
		resetBone("bipedLeftArm");
		resetBone("bipedRightArm");
		resetBone("bipedLeftLeg");
		resetBone("bipedRightLeg");

		// ===== 1.5 torso整体前倾90°（所有子骨骼自动继承） =====
		setRotation("torso", HALF_PI, 0, 0);
		// 位置补偿：pivot在Y=24，前倾后上半身浮空，向下移12单位使身体回到腿部高度
		getGeoModel().getBone("torso").ifPresent(bone -> bone.setPosY(-12f));

		// ===== 2. 头部旋转 =====
		// torso Rx(+90°)后，头部局部的rotY在世界空间变成了绕Z轴（侧向滚转）
		// 正确做法：yaw放在rotZ实现世界空间的左右看，pitch仍在rotX
		float headPitchRad = cachedHeadPitch * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		float headYawRad = cachedHeadYaw * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		setRotation("bipedHead", headPitchRad - HALF_PI, 0, headYawRad);

		// ===== 3. 手臂摆动（从torso继承水平姿态后，再前旋90°使前肢朝下） =====
		float rightArmPitch = -HALF_PI + MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 2.0f * cachedLimbDistance * 0.5f;
		float leftArmPitch = -HALF_PI + MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 2.0f * cachedLimbDistance * 0.5f;

		// ===== 4. 攻击挥动（等效 BipedEntityModel animateArms） =====
		float handSwing = entity.getHandSwingProgress(cachedPartialTick);
		float bodyRotY = 0;
		if (handSwing > 0) {
			// 躯干扭转（vanilla: body.yaw = sin(sqrt(f) * 2π) * 0.2）
			bodyRotY = MathHelper.sin(MathHelper.sqrt(handSwing) * (float) (Math.PI * 2)) * 0.2f;

			// 右手臂挥击（简化版vanilla攻击动画）
			rightArmPitch -= MathHelper.sin(MathHelper.sqrt(handSwing) * (float) Math.PI) * 1.2f;
		}
		// bipedBody只处理攻击扭转（前倾已由torso继承）
		setRotation("bipedBody", 0, bodyRotY, 0);

		setRotation("bipedRightArm", rightArmPitch, 0, 0);
		setRotation("bipedLeftArm", leftArmPitch, 0, 0);

		// ===== 5. 腿部摆动（等效 BipedEntityModel.setAngles 腿部公式） =====
		float rightLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 1.4f * cachedLimbDistance;
		float leftLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 1.4f * cachedLimbDistance;

		setRotation("bipedRightLeg", rightLegPitch, 0, 0);
		setRotation("bipedLeftLeg", leftLegPitch, 0, 0);
		// 后肢位置补偿：腿是body的子骨骼（不受torso旋转影响），往后推使其位于身体后部
		getGeoModel().getBone("bipedRightLeg").ifPresent(bone -> bone.setPosZ(-16f));
		getGeoModel().getBone("bipedLeftLeg").ifPresent(bone -> bone.setPosZ(-16f));

		// ===== 6. 尾巴动态旋转（等效 SSC setRotationForTailBones 非Feral模式） =====
		float[] state = TAIL_STATES.getOrDefault(entity.getId(), new float[6]);
		setTailRotations(cachedLimbAngle, cachedLimbDistance, cachedAge,
				state[4], state[2]);

		// ===== 7. 耳朵动态旋转（等效 SSC setRotationForHeadTailBones） =====
		setHeadTailRotations(cachedHeadYaw, cachedAge,
				state[4], state[2]);
	}

	// ==================== 尾巴动态系统（逐行对应SSC OriginFurModel） ====================

	/**
	 * 身体尾巴链旋转（等效SSC setRotationForTailBones 非Feral模式）
	 * 非Feral：使用rotY做水平摆动（模型直立，Y轴=垂直轴）
	 */
	private void setTailRotations(float limbAngle, float limbDistance, float age,
	                              float tailDragAmount, float tailDragAmountVertical) {
		for (String prefix : TAIL_CHAINS) {
			Optional<GeoBone> firstBone = getGeoModel().getBone(prefix + "_0");
			if (firstBone.isEmpty()) continue;

			GeoBone tail = firstBone.get();
			float tailSway = SWAY_SCALE * MathHelper.cos(age * SWAY_RATE
					+ ((float) Math.PI / 3.0f) * 0.75f);
			float tailBalance = MathHelper.cos(limbAngle * 0.6662f) * 0.325f * limbDistance;

			// 非Feral模式：rotY水平摆动（与SSC一致）
			tail.setRotY(-MathHelper.lerp(limbDistance, tailSway, tailBalance)
					- tailDragAmount * 0.75f);
			tail.setRotX(-tailDragAmountVertical * 0.75f);

			// 后续尾段：逐级衰减摆动 + 累积拖拽（与SSC一致）
			float offset = 0.0f;
			for (int i = 1; i < TAIL_CHAIN_LENGTH; i++) {
				Optional<GeoBone> nextBone = getGeoModel().getBone(prefix + "_" + i);
				if (nextBone.isEmpty()) continue;

				GeoBone bone = nextBone.get();
				bone.setRotY(-MathHelper.lerp(limbDistance,
						SWAY_SCALE * MathHelper.cos(age * SWAY_RATE
								- ((float) Math.PI / 3.0f) * offset),
						0.0f) - tailDragAmount * 0.75f);
				bone.setRotX(-tailDragAmountVertical * 0.75f * (offset + 0.75f));
				offset += 0.75f;
			}
		}
	}

	/**
	 * 头部附属链旋转（等效SSC setRotationForHeadTailBones，用于耳朵等）
	 * headAngle替代limbAngle，影响权重降低为0.1f
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

			// 与SSC setRotationForHeadTailBones一致：limbDistance固定为0.1f
			ear.setRotY(-MathHelper.lerp(0.1f, tailSway, tailBalance)
					- tailDragAmount * 0.75f);
			ear.setRotX(-tailDragAmountVertical * 0.75f);

			float offset = 0.0f;
			for (int i = 1; i < EAR_CHAIN_LENGTH; i++) {
				Optional<GeoBone> nextBone = getGeoModel().getBone(prefix + "_" + i);
				if (nextBone.isEmpty()) continue;

				GeoBone bone = nextBone.get();
				bone.setRotY(-MathHelper.lerp(0.1f,
						SWAY_SCALE * MathHelper.cos(age * SWAY_RATE
								- ((float) Math.PI / 3.0f) * offset),
						0.0f) - tailDragAmount * 0.75f);
				bone.setRotX(-tailDragAmountVertical * 0.75f * (offset + 0.75f));
				offset += 0.75f;
			}
		}
	}

	// ==================== 尾巴物理更新（逐行对应SSC FurRenderFeature.render()尾部计算） ====================

	/**
	 * 更新尾巴拖拽状态（每帧渲染后调用，与SSC render()尾部计算完全一致）
	 */
	private void updateTailDrag(WitchFamiliarEntity entity, float[] state) {
		// 水平拖拽：身体偏航变化驱动，0.75倍衰减
		state[1] = state[0]; // tailDragAmountO = tailDragAmount
		state[0] *= 0.75f;
		state[0] -= (float) (Math.toRadians(entity.bodyYaw - entity.prevBodyYaw) * 0.55f);
		state[0] = MathHelper.clamp(state[0], -1.6f, 1.6f);

		// 垂直拖拽：实体垂直速度驱动，0.8倍衰减
		float verticalSpeed = (float) entity.getVelocity().y;
		float targetVerticalDrag = MathHelper.clamp(verticalSpeed * 1.5f, -1.6f, 1.6f);
		state[3] = state[2]; // tailDragAmountVerticalO = tailDragAmountVertical
		state[2] *= 0.8f;
		state[2] += targetVerticalDrag * 0.15f;
		state[2] = MathHelper.clamp(state[2], -1.6f, 1.6f);
	}

	// ==================== 骨骼工具方法（等效SSC OriginFurModel.resetBone/setRotationForBone） ====================

	/**
	 * 重置骨骼（与SSC resetBone一致：pos=0, rot=0, scale=1, modelPos=0）
	 */
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

	/**
	 * 设置骨骼旋转（弧度制，与SSC setRotationForBone一致）
	 */
	private void setRotation(String boneName, float rx, float ry, float rz) {
		getGeoModel().getBone(boneName).ifPresent(bone -> {
			bone.setRotX(rx);
			bone.setRotY(ry);
			bone.setRotZ(rz);
		});
	}
}
