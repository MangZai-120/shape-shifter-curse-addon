package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils.AccessoryIO;
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
		// 未激活 / 已是 trinkets：无需处理
		if (AccessoryUtils.nowAccessoryMod == null
				|| "trinkets".equals(AccessoryUtils.nowAccessoryModID)) return;
		// 仅处理主包空壳 curios（实现类是主包 DefaultAccessory 的匿名内部类）
		Class<?> implClass = AccessoryUtils.nowAccessoryMod.getClass();
		if (!implClass.getName().contains("DefaultAccessory")) return;
		// trinkets IO 已注册且可加载 → 切回 trinkets（原生 Trinkets 或 tclayer 兼容层）
		AccessoryIO trinketsIo = AccessoryUtils.activeAccessoryModInterfaces.get("trinkets");
		if (trinketsIo == null) return;
		AccessoryUtils.nowAccessoryMod = trinketsIo;
		AccessoryUtils.nowAccessoryModID = "trinkets";
		org.slf4j.LoggerFactory.getLogger("ssc-addon").info(
				"[SSCA] Demoted stub curios Accessory IO back to trinkets (Kilt/Connector environment)");
	}
}
