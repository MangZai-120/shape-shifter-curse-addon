package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 金沙岚SP：禁止饱食度/饱和度自然回血。
 *
 * 通过 Redirect 拦截 HungerManager#update 内部的 {@code player.heal(amount)} 调用：
 *   - 当玩家处于金沙岚SP形态时，跳过该回血。
 *   - 其他形态保持原版行为，不影响饥饿值消耗或耗散度逻辑。
 *
 * 该 Mixin 不会取消整个 update 方法，因此饥饿值/耗散度仍正常更新（金沙岚仍需消耗食物施放消耗）。
 */
@Mixin(HungerManager.class)
public abstract class GoldenSandstormHungerMixin {

	@Redirect(
			method = "update",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;heal(F)V"),
			require = 0
	)
	private void ssc_addon$preventGoldenSandstormNaturalRegen(PlayerEntity player, float amount) {
		if (FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			// 金沙岚禁用饱食度自然回血，由 GoldenSandstormRegen 接管
			return;
		}
		player.heal(amount);
	}
}
