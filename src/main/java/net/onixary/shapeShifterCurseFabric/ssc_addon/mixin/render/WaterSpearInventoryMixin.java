package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.RenderContextTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 水矛在物品栏/快捷栏（DrawContext.drawItem）强制显示 2D water1 平面图。
 * 手持（第一/第三人称）仍走 3D 模型（经 ssc_addon:held override）。
 *
 * 原理：物品栏图标经 DrawContext.drawItem → ItemRenderer.getHeldItemModel 解析 override。
 * held predicate 在手持时返回 1，会触发 override 把物品栏也切到 3D。
 * 这里在 drawItem 入口设 ThreadLocal 标记，held predicate 读标记——GUI 上下文时强制返回 0（不触发 3D）。
 * 手持渲染（HeldItemFeatureRenderer）不走 DrawContext.drawItem，标记保持 false，正常走 3D。
 */
@Mixin(DrawContext.class)
public class WaterSpearInventoryMixin {

	@Inject(
			method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V",
			at = @At("HEAD")
	)
	private void ssc_addon$markGuiContext(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.setGuiContext(true);
	}

	@Inject(
			method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearGuiContext(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.clear();
	}
}
