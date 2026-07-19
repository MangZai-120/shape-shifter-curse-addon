package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.config.CommonConfig;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CursedMoon.class)
public class CursedMoonSpMessageMixin {

	/**
	 * 在诅咒之月效果应用时，为SP形态显示额外消息
	 * SP形态（special_form flag）不会被诅咒之月影响，显示特殊消息
	 * 注入在 applyStartCursedMoonEffect 头部，仅首次应用（isCursedMoonApplied 尚未置位）时显示
	 */
	@Inject(method = "applyStartCursedMoonEffect", at = @At("HEAD"), remap = false)
	private static void onApplyMoonEffect(World world, PlayerEntity player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayerEntity)) return;
		PlayerFormComponent formComp = RegPlayerFormComponent.PLAYER_FORM.get(player);
		if (formComp == null) return;

		IForm currentForm = formComp.nowForm;
		if (currentForm == null) return;

		// SP形态（special_form flag）显示特殊消息
		// 只在第一次应用效果时显示（检查 isCursedMoonApplied 标记）
		// 若管理员通过通用配置关闭了诅咒之月变形（enableCursedMoonTransform=false），
		// 则所有玩家都不会变形，此时不应再暗示"你形态特殊"，避免误导。
		if (currentForm.getFormFlag().contains("special_form") && !formComp.isCursedMoonApplied) {
			CommonConfig commonConfig = ShapeShifterCurseFabric.commonConfig;
			if (commonConfig != null && !commonConfig.enableCursedMoonTransform) {
				return;
			}
			player.sendMessage(Text.translatable("message.ssc_addon.cursed_moon_sp_special").formatted(Formatting.YELLOW), false);
		}
	}
}
