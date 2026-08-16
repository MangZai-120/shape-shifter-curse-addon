package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jackcooper.shapeShifterCurseAddon.client.NightmareFearClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 食梦魔「恐惧」粉雾（客户端）：恐惧目标本人的视野限制——16 格半径粉色雾墙，
 * 淡入约 2 秒「慢慢出现」（{@link NightmareFearClient#getFogStrength}），
 * 效果上类似放大的致盲但保留近距离可见。仅恐惧目标本地生效，他人无感。
 *
 * <p>实现：applyFog RETURN 后直接用 RenderSystem 读写雾参数（方法尾部经
 * setShaderFogStart/End/Shape 写入，静态可直接调用，无需 @Shadow）。
 * 仅 FOG 类型（世界雾）处理；熔岩/水/粉末雪浸没雾不覆盖（保留原版触觉提示）。
 * 颜色：粉 0xFF6EC7 → (1.0, 0.4314, 0.7804)，按强度与原天空色插值（保留一点环境光层次）。</p>
 */
@Mixin(BackgroundRenderer.class)
public class FearFogMixin {

	@Inject(method = "applyFog", at = @At("RETURN"), require = 0)
	private static void ssca$fearFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
	                                 boolean thickFog, float tickDelta, CallbackInfo ci) {
		float strength = NightmareFearClient.getFogStrength(tickDelta);
		if (strength <= 0.0F) return;
		// 天空雾（FOG_SKY）与地形雾（FOG_TERRAIN）都覆盖（仿 custom-fog：不压天空雾则地平线漏景）。
		// 浸没状态（水/熔岩/雪）保留原版雾。
		if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
		// 16 格视野限制（无光影管线）：12 格清晰 → 16 格完全遮死（实心粉墙），
		// 强度渐进淡入。粉色 0xFF6EC7 → (1.0, 0.4314, 0.7804)。
		float targetStart = fogType == BackgroundRenderer.FogType.FOG_SKY ? 0.0F : 12.0F;
		float targetEnd = 16.0F;
		float curStart = RenderSystem.getShaderFogStart();
		float curEnd = RenderSystem.getShaderFogEnd();
		RenderSystem.setShaderFogStart(lerp(curStart, targetStart, strength));
		RenderSystem.setShaderFogEnd(lerp(curEnd, targetEnd, strength));
		RenderSystem.setShaderFogShape(FogShape.SPHERE); // 球形遮罩（此前误设 CYLINDER 呈圆筒状）
		// 颜色：恐惧时写「精确标记粉」(1.0, 0.4314, 0.7804)——光影侧 IrisShaderPackMixin 的
		// GLSL 条件分支按此精确匹配（±0.02）识别 SSCA 恐惧并切粉雾分支；透明度用 alpha
		// 控制淡入（颜色恒定，避免淡入期标记色漂移导致光影分支误判）。
		RenderSystem.setShaderFogColor(1.0F, 0.4314F, 0.7804F, strength);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
