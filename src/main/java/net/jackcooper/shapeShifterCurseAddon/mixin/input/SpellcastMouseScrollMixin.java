package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按住魔法书「切换键」时，鼠标滚轮切换当前选中魔法而非物品栏（jackcooper）。
 * 仅在佩戴魔法书且按住切换键时拦截，其余情况完全放行原版滚轮行为。
 */
@Mixin(Mouse.class)
public class SpellcastMouseScrollMixin {

	@Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void ssca$spellScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (vertical == 0) {
			return;
		}
		if (SpellcastClient.isSwitchKeyDown() && SpellcastClient.hasBookEquipped()) {
			// 上滚(vertical>0)=上一个魔法，下滚=下一个（首尾相连）
			SpellcastClient.cycleSelected(vertical > 0 ? -1 : 1);
			ci.cancel();
		}
	}
}
