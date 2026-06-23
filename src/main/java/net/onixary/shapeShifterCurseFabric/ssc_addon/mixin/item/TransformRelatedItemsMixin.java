package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformRelatedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransformRelatedItems.class)
public class TransformRelatedItemsMixin {

	@Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true, remap = false)
	private static void onUseCure(PlayerEntity player, CallbackInfo ci) {
		IForm currentForm = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;

		// Block suppressor usage for SP form (special_form flag)
		if (currentForm.getFormFlag().contains("special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}

	@Inject(method = "OnUseCureFinal", at = @At("HEAD"), cancellable = true, remap = false)
	private static void onUseCureFinal(PlayerEntity player, CallbackInfo ci) {
		IForm currentForm = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;

		// Block suppressor usage for SP form (special_form flag)
		if (currentForm.getFormFlag().contains("special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}
}
