package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.MilkBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让「蛛网缠身」防牛奶：牛奶清状态效果时把该 debuff 保留下来（其余效果照常清除）。
 *
 * <p>用 {@code @WrapOperation} 取代原先的 {@code @Redirect}——{@code @Redirect} 与其它 mod /
 * SSC 主包对同位置 {@code clearStatusEffects()} 的注入会硬冲突（{@code InjectionError} 崩溃），
 * 而 {@code @WrapOperation} 支持多 mod 链式叠加：先记下蛛网 debuff、放行原 {@code clearStatusEffects}、
 * 再把蛛网 debuff 重新挂回。</p>
 */
@Mixin(MilkBucketItem.class)
public class MilkBucketWebBoundMixin {

	@WrapOperation(method = "finishUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;clearStatusEffects()Z"), require = 0)
	private boolean ssca$keepWebBound(LivingEntity entity, Operation<Boolean> original) {
		StatusEffectInstance web = entity.getStatusEffect(RegAddonEffects.SPIDER_WEB_BOUND);
		boolean result = original.call(entity);
		if (web != null) {
			entity.addStatusEffect(new StatusEffectInstance(web));
		}
		return result;
	}
}
