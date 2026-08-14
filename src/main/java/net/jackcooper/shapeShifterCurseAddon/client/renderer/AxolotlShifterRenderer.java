package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.jackcooper.shapeShifterCurseAddon.client.model.AxolotlShifterModel;
import net.jackcooper.shapeShifterCurseAddon.entity.AxolotlShifterEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 美西螈幻形者渲染器 — 程序化骨骼驱动，等效 SSC FurRenderFeature.ProcessModel()
 * <p>
 * 原版 form_axolotl_3.geo.json 为人形直立布局（bipedHead/bipedBody/tail_0~3/四肢，
 * 无 torso 骨骼、无耳链；头挂腮链 tail_head_a~f），因此这里直接用 BipedEntityModel
 * 等效公式驱动头/四肢摆动，尾链（身体尾 + 头部腮链）按 SSC 尾巴拖拽公式动态旋转。
 */
public class AxolotlShifterRenderer extends GeoEntityRenderer<AxolotlShifterEntity> {

	// 与原版注册一致：applyScaleFunc(NORMAL_SCALE_FUNC_BUILDER.apply(0.9f, 1.0f))
	private static final float MODEL_SCALE = 0.9f;

	// ==================== 尾巴/腮链配置 ====================
	// 身体尾链（bipedBody 子骨骼）
	private static final String BODY_TAIL_CHAIN = "tail";
	private static final int BODY_TAIL_LENGTH = 4;   // tail_0 ~ tail_3
	// 头部腮链（bipedHead 子骨骼）
	private static final String[] HEAD_TAIL_CHAINS = {"tail_head_a", "tail_head_b", "tail_head_c",
			"tail_head_d", "tail_head_e", "tail_head_f"};
	// 每条腮链仅 _0 一节（腮链旋转逻辑写死处理首节，故不单独抽常量）

	// 尾巴动态常量（与SSC OriginFurModel一致）
	private static final float SWAY_RATE = 0.33333334f * 0.5f;
	private static final float SWAY_SCALE = 0.05f;

	// ==================== 每实体的尾巴拖拽状态 ====================
	// [0]tailDragAmount [1]tailDragAmountO
	// [2]tailDragAmountVertical [3]tailDragAmountVerticalO
	// [4]currentTailDragAmount(平滑后) [5]currentTailDragAmountVertical(平滑后)
	private static final Map<Integer, float[]> TAIL_STATES = new HashMap<>();
	// ==================== 每实体的眨眼状态（复刻原版 FormEyeBlinkController） ====================
	private static final String EYE_ROOT_BONE = "eyeRoot";
	private static final float EYE_OPEN_SCALE = 1.0f;
	private static final float EYE_CLOSED_SCALE = 0.01f;
	private static final int BLINK_MIN_INTERVAL = 60;   // 原版默认 60~140 tick 随机间隔
	private static final int BLINK_MAX_INTERVAL = 140;
	private static final int BLINK_TICKS = 4;           // 原版默认 4 tick 眨眼时长
	private static final Map<Integer, int[]> BLINK_STATES = new HashMap<>();
	// [0]waitTicksRemaining [1]blinkTicksElapsed [2]blinking(1/0) [3]lastAge
	// ==================== 每帧缓存 ====================
	private float cachedLimbAngle;
	private float cachedLimbDistance;
	private float cachedHeadYaw;
	private float cachedHeadPitch;
	private float cachedPartialTick;
	private float cachedAge;

	public AxolotlShifterRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new AxolotlShifterModel());
		this.shadowRadius = 0.35f;
	}

	@Override
	public void render(AxolotlShifterEntity entity, float entityYaw, float partialTick,
	                   MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
		// 清理已移除实体的尾巴/眨眼状态（防止内存泄漏）
		if (entity.isRemoved()) {
			TAIL_STATES.remove(entity.getId());
			BLINK_STATES.remove(entity.getId());
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
	protected void applyRotations(AxolotlShifterEntity animatable, MatrixStack poseStack,
	                              float ageInTicks, float rotationYaw, float partialTick) {
		super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
		// 基岩版模型面朝+Z，追加Ry(180°)修正朝向
		poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
	}

	@Override
	public void preRender(MatrixStack poseStack, AxolotlShifterEntity animatable, BakedGeoModel model,
	                      VertexConsumerProvider bufferSource, VertexConsumer buffer,
	                      boolean isReRender, float partialTick, int packedLight,
	                      int packedOverlay, float red, float green, float blue, float alpha) {
		// reRender（发光层等overlay）会再次调用preRender(isReRender=true)，
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
	 * 骨骼旋转处理——人形直立布局，模型坐标即玩家姿态坐标：
	 * 直接用 BipedEntityModel.setAngles() 等效公式驱动头/四肢，
	 * 尾链按 SSC 尾巴拖拽公式动态旋转。
	 */
	private void processModel(AxolotlShifterEntity entity) {
		// ===== 1. 重置受控骨骼（与SSC resetBone一致） =====
		resetBone("bipedHead");
		resetBone("bipedBody");
		resetBone("bipedLeftArm");
		resetBone("bipedRightArm");
		resetBone("bipedLeftLeg");
		resetBone("bipedRightLeg");

		// ===== 2. 头部旋转（BipedEntityModel 等效，0.5 系数与女巫使魔渲染器一致） =====
		float headPitchRad = cachedHeadPitch * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		float headYawRad = cachedHeadYaw * MathHelper.RADIANS_PER_DEGREE * 0.5f;
		setRotation("bipedHead", headPitchRad, headYawRad, 0);

		// ===== 3. 手臂摆动 =====
		float rightArmPitch = MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 2.0f * cachedLimbDistance * 0.5f;
		float leftArmPitch = MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 2.0f * cachedLimbDistance * 0.5f;

		// ===== 4. 攻击挥动（等效 BipedEntityModel animateArms） =====
		float handSwing = entity.getHandSwingProgress(cachedPartialTick);
		float bodyRotY = 0;
		if (handSwing > 0) {
			// 躯干扭转（vanilla: body.yaw = sin(sqrt(f) * 2π) * 0.2）
			bodyRotY = MathHelper.sin(MathHelper.sqrt(handSwing) * (float) (Math.PI * 2)) * 0.2f;
			// 右手臂挥击
			rightArmPitch -= MathHelper.sin(MathHelper.sqrt(handSwing) * (float) Math.PI) * 1.2f;
		}
		setRotation("bipedBody", 0, bodyRotY, 0);

		setRotation("bipedRightArm", rightArmPitch, 0, 0);
		setRotation("bipedLeftArm", leftArmPitch, 0, 0);

		// ===== 5. 腿部摆动（BipedEntityModel.setAngles 腿部公式） =====
		float rightLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f)
				* 1.4f * cachedLimbDistance;
		float leftLegPitch = MathHelper.cos(cachedLimbAngle * 0.6662f + (float) Math.PI)
				* 1.4f * cachedLimbDistance;
		setRotation("bipedRightLeg", rightLegPitch, 0, 0);
		setRotation("bipedLeftLeg", leftLegPitch, 0, 0);

		// ===== 6. 尾巴/腮链动态旋转 =====
		float[] state = TAIL_STATES.getOrDefault(entity.getId(), new float[6]);
		setTailRotations(cachedLimbAngle, cachedLimbDistance, cachedAge,
				state[4], state[5]);
		setHeadTailRotations(cachedHeadYaw, cachedAge, state[4], state[5]);

		// ===== 7. 眨眼（复刻原版 FormEyeBlinkController，纯客户端渲染侧状态机） =====
		updateEyeBlink(entity);
	}

	// ==================== 眨眼系统（逐行对应原版 FormEyeBlinkController） ====================

	/**
	 * 随机间隔眨眼：60~140 tick 等待 → 4 tick 闭眼（Y 缩放 1.0→0.01→1.0）。
	 * 原版由 ClientConfig 配置间隔，怪物实体无配置入口，用原版默认值。
	 */
	private void updateEyeBlink(AxolotlShifterEntity entity) {
		Optional<GeoBone> eyeRootOpt = getGeoModel().getBone(EYE_ROOT_BONE);
		if (eyeRootOpt.isEmpty()) return;
		GeoBone eyeRoot = eyeRootOpt.get();

		int[] blink = BLINK_STATES.computeIfAbsent(entity.getId(), k -> {
			Random r = new Random();
			return new int[]{BLINK_MIN_INTERVAL + r.nextInt(BLINK_MAX_INTERVAL - BLINK_MIN_INTERVAL + 1), 0, 0, -1};
		});

		// 按实体 age 差值推进（对齐原版按 player.age 推进，帧率无关）
		if (blink[3] == -1 || entity.age != blink[3]) {
			int elapsed = MathHelper.clamp(entity.age - blink[3], 1, 100);
			if (blink[3] == -1) elapsed = 1;
			blink[3] = entity.age;
			for (int i = 0; i < elapsed; i++) {
				if (blink[2] == 1) {
					blink[1]++;
					if (blink[1] >= BLINK_TICKS) {
						blink[2] = 0;
						blink[1] = 0;
						Random r = new Random();
						blink[0] = BLINK_MIN_INTERVAL + r.nextInt(BLINK_MAX_INTERVAL - BLINK_MIN_INTERVAL + 1);
					}
				} else {
					if (blink[0] > 0) blink[0]--;
					if (blink[0] <= 0) {
						blink[2] = 1;
						blink[1] = 0;
					}
				}
			}
		}

		// 计算当前 Y 缩放（原版 calculateScaleY：三角波开闭）
		float scaleY = EYE_OPEN_SCALE;
		if (blink[2] == 1) {
			float progress = MathHelper.clamp(blink[1] / (float) BLINK_TICKS, 0.0f, 1.0f);
			float closeAmount = progress <= 0.5f ? progress * 2.0f : (1.0f - progress) * 2.0f;
			scaleY = MathHelper.lerp(closeAmount, EYE_OPEN_SCALE, EYE_CLOSED_SCALE);
		}
		eyeRoot.setScaleY(scaleY);
	}

	// ==================== 尾巴动态系统（逐行对应SSC OriginFurModel） ====================

	/**
	 * 身体尾链旋转（等效SSC setRotationForTailBones 非Feral模式）
	 * 非Feral：rotY水平摆动（模型直立，Y轴=垂直轴）
	 */
	private void setTailRotations(float limbAngle, float limbDistance, float age,
	                              float tailDragAmount, float tailDragAmountVertical) {
		Optional<GeoBone> firstBone = getGeoModel().getBone(BODY_TAIL_CHAIN + "_0");
		if (firstBone.isEmpty()) return;

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
		for (int i = 1; i < BODY_TAIL_LENGTH; i++) {
			Optional<GeoBone> nextBone = getGeoModel().getBone(BODY_TAIL_CHAIN + "_" + i);
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

	/**
	 * 头部腮链旋转（等效SSC setRotationForHeadTailBones）
	 * headAngle替代limbAngle，影响权重降低为0.1f
	 */
	private void setHeadTailRotations(float headAngle, float age,
	                                  float tailDragAmount, float tailDragAmountVertical) {
		for (String prefix : HEAD_TAIL_CHAINS) {
			Optional<GeoBone> firstBone = getGeoModel().getBone(prefix + "_0");
			if (firstBone.isEmpty()) continue;

			GeoBone gill = firstBone.get();
			float tailSway = SWAY_SCALE * MathHelper.cos(age * SWAY_RATE
					+ ((float) Math.PI / 3.0f) * 0.75f);
			float tailBalance = MathHelper.cos(headAngle * 0.6662f) * 0.325f * 0.1f;

			// 与SSC setRotationForHeadTailBones一致：limbDistance固定为0.1f
			gill.setRotY(-MathHelper.lerp(0.1f, tailSway, tailBalance)
					- tailDragAmount * 0.75f);
			gill.setRotX(-tailDragAmountVertical * 0.75f);
		}
	}

	// ==================== 尾巴物理更新（逐行对应SSC FurRenderFeature.render()尾部计算） ====================

	/**
	 * 更新尾巴拖拽状态（每帧渲染后调用，与SSC render()尾部计算完全一致）
	 */
	private void updateTailDrag(AxolotlShifterEntity entity, float[] state) {
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
	 * 重置骨骼（与SSC resetBone一致：pos=0, rot=0, scale=1）
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
