package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 食梦魔「恐惧」光影兼容补丁 —— 仅 SSCA 内实现，磁盘光影文件分毫不动。
 *
 * <p><b>三条设计约束（用户定稿）：</b></p>
 * <ul>
 *   <li><b>不强依赖 Iris</b>：{@code @Pseudo} + 字符串 targets（编译期零 Iris 依赖）；
 *       所有注入 {@code require = 0} —— Iris 未装或升级改签名都只会静默失效，绝不崩溃。</li>
 *   <li><b>只影响 SSCA</b>：GLSL 替换体是「条件分支」——仅当 fogColor 精确匹配 SSCA 恐惧粉
 *       (1.0, 0.4314, 0.7804 ± 0.02) 时走「粉色 12→16 格」分支；否则逐字执行光影原版
 *       失明公式（正常玩家/其它 mod 的失明观感完全不变）。粉色标记由 {@code FearFogMixin}
 *       仅在恐惧激活时写入，天然隔离。</li>
 *   <li><b>兼容性高</b>：注入点选 {@code getFragmentSource()}（public 只读 getter，
 *       Iris 编译 shader 前必经），用最标准的 {@code @Inject RETURN + setReturnValue} 惯用法；
 *       正则匹配「混向 vec3(0.0) 的 DoBlindnessFog」模式（Complementary 系通用），
 *       不匹配则源码原样返回（对无此函数的光影零影响）。</li>
 * </ul>
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public class IrisShaderPackMixin {

	/** 匹配常见失明雾写法（Complementary 系）：函数体把颜色混向纯黑 vec3(0.0)。 */
	private static final Pattern BLINDNESS_FOG = Pattern.compile(
			"void\\s+DoBlindnessFog\\s*\\(inout\\s+vec3\\s+color\\s*,\\s*float\\s+lViewPos\\s*\\)\\s*\\{[^}]*mix\\s*\\(\\s*color\\s*,\\s*vec3\\s*\\(\\s*0\\.0\\s*\\)\\s*,\\s*fog\\s*\\)\\s*;[^}]*\\}",
			Pattern.DOTALL);

	/** 条件版失明雾：SSCA 恐惧粉标记在场 → 粉色 12→16 格；否则逐字原版公式（观感不变）。 */
	private static final String CONDITIONAL_FOG_REPLACEMENT =
			"void DoBlindnessFog(inout vec3 color, float lViewPos) {\n" +
			"    // [SSCA Fear] pink branch ONLY while SSCA fear sets fogColor to exact pink; else vanilla-identical\n" +
			"    bool sscaFear = abs(fogColor.r - 1.0) < 0.02 && abs(fogColor.g - 0.4314) < 0.02 && abs(fogColor.b - 0.7804) < 0.02;\n" +
			"    if (sscaFear) {\n" +
			"        float fog = smoothstep(12.0, 16.0, lViewPos) * blindness;\n" +
			"        fog = clamp(fog, 0.0, 1.0);\n" +
			"        color = mix(color, fogColor, fog);\n" +
			"    } else {\n" +
			"        float fog = lViewPos * 0.3 * blindness;\n" +
			"        fog *= fog;\n" +
			"        fog = 1.0 - exp(-fog);\n" +
			"        fog = clamp(fog, 0.0, 1.0);\n" +
			"        color = mix(color, vec3(0.0), fog);\n" +
			"    }\n" +
			"}";

	@Inject(method = "getFragmentSource", at = @At("RETURN"), cancellable = true, require = 0)
	private void ssca$patchFragmentSource(CallbackInfoReturnable<Optional<String>> cir) {
		Optional<String> src = cir.getReturnValue();
		if (src == null || src.isEmpty()) return;
		String original = src.get();
		String patched = patch(original);
		if (patched != original) {
			cir.setReturnValue(Optional.of(patched));
		}
	}

	private static String patch(String src) {
		if (!src.contains("DoBlindnessFog")) return src;
		return BLINDNESS_FOG.matcher(src).replaceAll(CONDITIONAL_FOG_REPLACEMENT);
	}
}
