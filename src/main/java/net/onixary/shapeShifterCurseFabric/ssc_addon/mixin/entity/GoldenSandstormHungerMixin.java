package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 饱食度自然回血拦截集成点（名由历史原因保留，实际处理多种场景）。
 *
 * 通过 Redirect 拦截 HungerManager#update 内部的 {@code player.heal(amount)} 调用：
 *   - 金沙岚SP形态：跳过回血（由 GoldenSandstormRegen 接管）
 *   - 寄生果蝠「感染孢子」状态实体：跳过回血（被寄生玩家无法以饱食度恢复血量）
 *
 * Mixin 只拦截 heal 调用本身，不影响饱食/耗散度/只饱食计时器。
 */
@Mixin(HungerManager.class)
public abstract class GoldenSandstormHungerMixin {

	@Redirect(
			method = "update",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V"),
			require = 0
	)
	private void ssc_addon$blockNaturalRegen(PlayerEntity player, float amount) {
		// 金沙岚SP禁用饱食度自然回血
		if (FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) return;
		// 被感染孢子状态的实体禁用饱食度自然回血
		if (InfectionSporeManager.isInfected(player.getUuid())) return;
		player.heal(amount);
	}
}
