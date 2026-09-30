package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.SheepFormClientHooks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 变羊渲染替换（羊了个羊，客户端，jackcooper，2026-09-29）：
 * 带 {@code SHEEP_FORM} 效果的活体在 HEAD 处取消原渲染（含玩家原模型、SSC 形态渲染、
 * 装备等全部特征渲染——cancelled 后全链不执行），改用<b>原版羊模型 + 原版羊贴图</b>重画。
 *
 * <p><b>mixin 铁律</b>：本类只保留注入处理器（private），模型缓存/贴图常量/姿势公式全部
 * 放在普通客户端类 {@link SheepFormClientHooks}——mixin 类里出现任何非 private 普通方法
 * 会被 Mixin 框架尝试合并进目标类（LivingEntityRenderer）并在 APPLY 阶段直接崩游戏
 * （2026-09-29 实机崩溃实证：non-private static method → class_922 转换失败 → 连环崩）。</p>
 *
 * <p>模型来源：SHEEP / SHEEP_FUR 两个 baked layer，由 {@code SheepFormClientHooks.init(ctx)}
 * 在咩弹渲染器工厂首次构造时初始化（世界渲染初始化时机，ModelLoader 已 bake）。</p>
 *
 * <p>玩家变羊：主玩家第一人称由 vanilla 相机渲染，不经过实体渲染，天然不挡自己视野；
 * 其它玩家/客机视角正常显示羊。cancelled HEAD 拦截后 nametag 不再绘制——
 * 变羊期间看不到玩家名牌（「认不出这是谁」的语义，可接受）。</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SheepFormRenderMixin<T extends LivingEntity> {

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void ssca$renderAsSheep(T entity, float yaw, float tickDelta, MatrixStack matrices,
	                                VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		// 死亡放行（2026-09-29 用户定稿「倒地的模型应该是原来的」）：deathTime>0 后回落原版
		// 渲染链——玩家 SSC 原形态模型/mob 原模型执行原版倒地旋转动画；羊模型只覆盖存活期。
		if (!entity.hasStatusEffect(SscAddon.SHEEP_FORM) || entity.isInvisible() || entity.isSpectator()
				|| entity.deathTime > 0) {
			return;
		}
		// 渲染主体在 SheepFormClientHooks（普通类，可自由用客户端 API）；
		// 返回 false = 模型未就绪（初始化钩子未跑），回落原渲染不拦截。
		if (SheepFormClientHooks.renderAsSheep(entity, yaw, tickDelta, matrices, vertexConsumers, light)) {
			ci.cancel(); // 取消原渲染全链（玩家模型/SSC 形态/装备/发光轮廓特征全部跳过）
		}
	}
}
