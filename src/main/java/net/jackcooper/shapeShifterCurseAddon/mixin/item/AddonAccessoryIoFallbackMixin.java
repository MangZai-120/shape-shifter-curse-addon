package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.jackcooper.shapeShifterCurseAddon.compat.AccessoryIoFallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSCA 饰品 IO「空壳 Curios」降级守卫（jackcooper）。
 *
 * <p><b>背景</b>：主包在 fabric.mod.json 声明 curios 优先级 2000 &gt; trinkets 1000。
 * 当环境装有 Kilt 转载的 Forge Curios 时 {@code isModLoaded("curios")} 恒真 →
 * {@code Active Accessory IO: curios}，但主包 Curios 侧实现（CurioUtils）是<b>空壳 stub</b>
 * （getEntitySlots 返回空表、setEntitySlot 无操作）→ 所有走 "auto" 的饰品查询
 * （附属 19 个饰品的效果判定、AddonAccessoryGuard 兜底卸下）全部失效。</p>
 *
 * <p><b>方案</b>：在 {@code reCalcAccessoryMod()} 完成后（RETURN）检查：若激活的 IO 是
 * curios 但它是「空壳」（实现类即主包 CurioUtils 匿名类，非第三方真实实现），且
 * trinkets IO 也可用（原生 Trinkets 或 tclayer 兼容层提供的 Trinkets API）→
 * 把 nowAccessoryMod 切换回 trinkets IO，恢复饰品查询链。</p>
 *
 * <p>仅修复「curios 空壳 + trinkets 可用」这一种组合；纯 Curios 环境（无 trinkets API）
 * 保持原状不动。require=0：主包类名/方法名漂移时静默失效，不崩游戏。</p>
 */
@Mixin(value = AccessoryUtils.class, remap = false)
public class AddonAccessoryIoFallbackMixin {

	@Inject(method = "reCalcAccessoryMod", at = @At("RETURN"), require = 0)
	private static void ssca$demoteStubCurios(CallbackInfo ci) {
		// 未激活 / 当前权威不是 curios：无需处理
		if (AccessoryUtils.nowAccessoryMod == null
				|| !"curios".equals(AccessoryUtils.nowAccessoryModID)) return;
		// 关键：curios 的 IO 实现类无论是空壳还是真实实现，都是主包 DefaultAccessory 的匿名内部类，
		// 不能靠类名判断。改用主包自家能力位 CurioUtils.isLoaded()（空壳返回 false，
		// 被 CurioUtilsImpl mixin 覆盖成真实实现后返回 true）。
		// 真实实现时绝不能降级：onPlayerEquip 会按 nowAccessoryModID 与 pluginID 比对，
		// 降级成 trinkets 后 curios 侧回调（pluginID="curios"）会被门槛直接 return，
		// 结果是「装在 Curios 槽的饰品全部不挂 power」（2026-10-23 实机 Bug）。
		// 第三方注册的真实 IO 也不能因 SSC 自带的 CurioUtils 仍为空壳而被降级。
		if (!AccessoryIoFallback.restoreTrinketsForStubCurios(curiosImplementationIsReal())) return;
		org.slf4j.LoggerFactory.getLogger("ssc-addon").info(
				"[SSCA] Demoted stub curios Accessory IO back to trinkets (Kilt/Connector environment)");
	}

	/**
	 * 主包的 curios 实现是否为<b>真实</b>实现（而非空壳 stub）。
	 *
	 * <p>用反射读 {@code CurioUtils.isLoaded()}：反编译确认空壳返回 {@code false}，
	 * 被 {@code mixin.forge.CurioUtilsImpl} 覆盖后返回 {@code true}。反射失败（旧版主包
	 * 没有该方法）时按<b>真实实现</b>处理——宁可回退主包原行为，也不误降级打断 Curios 槽。</p>
	 */
	private static boolean curiosImplementationIsReal() {
		try {
			Class<?> curioUtils = Class.forName("net.onixary.shapeShifterCurseFabric.util.Accessory.CurioUtils");
			Object loaded = curioUtils.getMethod("isLoaded").invoke(null);
			return !Boolean.FALSE.equals(loaded);
		} catch (Throwable t) {
			return true;
		}
	}
}
