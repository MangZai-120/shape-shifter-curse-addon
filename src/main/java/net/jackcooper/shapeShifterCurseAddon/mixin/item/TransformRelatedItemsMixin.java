package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.player.PlayerEntity;
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

	// 适配说明：SSC 1.9.2 官方 jar 中 OnUseCure / OnUseCureFinal 均为 (PlayerEntity) 单参数
	// （ItemStack 参数是 1.10.0 起才加入的，官方提交 f1fb7fea）。本分支固定面向 1.9.2，
	// handler 必须省略 ItemStack 形参；否则 mixin 应用失败导致 TransformRelatedItems 整类崩溃
	// （表现为吃任何食物即崩 ItemStack.finishUsing -> SSC mixin -> 类加载失败）。
	// （main 分支面向 1.10.0+，handler 保留 ItemStack 形参，两分支互不影响。）
	@Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void onUseCure(PlayerEntity player, CallbackInfo ci) {
		PlayerFormBase currentForm = Compat1_9_2.nowForm(player);

		// Block suppressor usage for SP form (special_form flag)
		if (Compat1_9_2.hasFlag(currentForm, "special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}

	@Inject(method = "OnUseCureFinal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void onUseCureFinal(PlayerEntity player, CallbackInfo ci) {
		PlayerFormBase currentForm = Compat1_9_2.nowForm(player);

		// Block suppressor usage for SP form (special_form flag)
		if (Compat1_9_2.hasFlag(currentForm, "special_form")) {
			player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
			ci.cancel();
		}
	}
}
