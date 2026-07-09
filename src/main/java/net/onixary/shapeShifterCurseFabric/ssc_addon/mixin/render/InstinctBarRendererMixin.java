package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InstinctBarRenderer.class)
public class InstinctBarRendererMixin {

	@ModifyVariable(method = "render", at = @At("STORE"), name = "showInstinctBar")
	private boolean hideInstinctBarForSP(boolean original) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			PlayerFormComponent comp = mc.player.getComponent(RegPlayerFormComponent.PLAYER_FORM);
			IForm curForm = comp.nowForm;
			if (curForm != null && curForm.getFormFlag().contains("special_form")) {
				return false;
			}
			// 兜底：直接按当前形态ID判断荧光幼灵系（荧光幼灵 / 阿澪），
			// 防客户端 form 对象 flag 因同步异常丢失导致本能值条误显示
			Identifier id = comp.nowFormID;
			if (id != null && (id.equals(FormIdentifiers.AXOLOTL_FLUORESCENT) || id.equals(FormIdentifiers.AXOLOTL_ALING))) {
				return false;
			}
		}
		return original;
	}
}
