package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformRelatedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.jackcooper.shapeShifterCurseAddon.compat.ssc192.Compat1_9_2;

@Mixin(TransformRelatedItems.class)
public class TransformRelatedItemsMixin {

	// 原版 1.10.0 起 OnUseCure / OnUseCureFinal 等方法签名新增 @Nullable ItemStack stack 参数，
	// @Inject handler 必须同步加 ItemStack 形参，否则 mixin 应用失败导致 TransformRelatedItems 整类崩溃
	// （表现为吃催化剂/抑制剂即崩 Catalyst.finishUsing -> TransformRelatedItems）。
	@Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void onUseCure(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		PlayerFormBase currentForm = Compat1_9_2.nowForm(player);

		// Block suppressor usage for SP form (special_form flag)
		if (Compat1_9_2.hasFlag(currentForm, "special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}

	@Inject(method = "OnUseCureFinal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void onUseCureFinal(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		PlayerFormBase currentForm = Compat1_9_2.nowForm(player);

		// Block suppressor usage for SP form (special_form flag)
		if (Compat1_9_2.hasFlag(currentForm, "special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}
}
