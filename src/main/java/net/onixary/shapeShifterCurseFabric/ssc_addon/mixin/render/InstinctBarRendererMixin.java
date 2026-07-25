package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSCA 本能值条门控：special_form / 荧光幼灵系形态下不渲染本能值条。
 *
 * <p>改用 render HEAD 直接 cancel（等价于原版 showInstinctBar=false，因 render 除条件渲染外无其它副作用），
 * 摆脱原先 {@code @ModifyVariable(STORE, name="showInstinctBar")} 对局部变量名的强耦合——
 * SSC 一旦重构 render 方法体（改局部量名/内联/丢 LVT）原写法会加载崩，HEAD 注入则对方法体改动免疫。
 * {@code require = 0}：即使 SSC 改了 render 签名致注入失效，也只是本能值条门控失能，不崩游戏。</p>
 */
@Mixin(InstinctBarRenderer.class)
public class InstinctBarRendererMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void ssc_addon$hideInstinctBarForSP(DrawContext context, float tickDelta, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		PlayerFormComponent comp = mc.player.getComponent(RegPlayerFormComponent.PLAYER_FORM);
		IForm curForm = comp.nowForm;
		// special_form 形态（各 SP）不显示本能值条
		if (curForm != null && curForm.getFormFlag().contains("special_form")) {
			ci.cancel();
			return;
		}
		// 兜底：按当前形态ID判断荧光幼灵系（荧光幼灵 / 阿澪），
		// 防客户端 form 对象 flag 因同步异常丢失导致本能值条误显示
		Identifier id = comp.nowFormID;
		if (id != null && (id.equals(FormIdentifiers.AXOLOTL_FLUORESCENT) || id.equals(FormIdentifiers.AXOLOTL_ALING))) {
			ci.cancel();
		}
	}
}
