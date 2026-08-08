package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.MilkBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「蛛网缠身」防牛奶：牛奶清状态效果时把该 debuff 保留下来（其余效果照常清除）。
 * 重定向 {@link MilkBucketItem#finishUsing} 内对 {@code clearStatusEffects()} 的调用。
 */
@Mixin(MilkBucketItem.class)
public class MilkBucketWebBoundMixin {

	@Redirect(method = "finishUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;clearStatusEffects()Z"))
	private boolean ssca$keepWebBound(LivingEntity entity) {
		StatusEffectInstance web = entity.getStatusEffect(RegAddonEffects.SPIDER_WEB_BOUND);
		boolean result = entity.clearStatusEffects();
		if (web != null) {
			entity.addStatusEffect(new StatusEffectInstance(web));
		}
		return result;
	}
}
