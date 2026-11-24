package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 牛奶保留蛛网缠身和变羊。Fabric 与 Forge 的牛奶净化调用签名不同，
 * 但两端完成饮用的入口相同，让各自的净化逻辑处理其余效果和物品栈。
 */
@Mixin(MilkBucketItem.class)
public abstract class MilkBucketWebBoundMixin {

	@WrapMethod(method = "finishUsing")
	private ItemStack ssca$keepMilkResistantEffects(ItemStack stack, World world, LivingEntity entity, Operation<ItemStack> original) {
		if (world.isClient) return original.call(stack, world, entity);
		StatusEffectInstance web = entity.getStatusEffect(RegAddonEffects.SPIDER_WEB_BOUND);
		StatusEffectInstance sheep = entity.getStatusEffect(SscAddon.SHEEP_FORM);
		ItemStack result = original.call(stack, world, entity);
		if (web != null && !entity.hasStatusEffect(RegAddonEffects.SPIDER_WEB_BOUND)) {
			entity.addStatusEffect(web);
		}
		if (sheep != null && !entity.hasStatusEffect(SscAddon.SHEEP_FORM)) {
			entity.addStatusEffect(sheep);
		}
		return result;
	}
}
