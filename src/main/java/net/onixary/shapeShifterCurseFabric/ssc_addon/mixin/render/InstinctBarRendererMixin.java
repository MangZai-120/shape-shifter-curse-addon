package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InstinctBarRenderer.class)
public class InstinctBarRendererMixin {

	@ModifyVariable(method = "render", at = @At("STORE"), name = "showInstinctBar")
	private boolean hideInstinctBarForSP(boolean original) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			IForm curForm = mc.player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
			if (curForm != null && curForm.getFormFlag().contains("special_form")) {
				return false;
			}
		}
		return original;
	}
}
