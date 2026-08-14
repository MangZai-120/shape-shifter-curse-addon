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
 * 水矛在物品栏/快捷栏/创造物品栏/物品展示框等所有 GUI 上下文强制显示 2D water1 平面图。
 * 手持（第一/第三人称）仍走 3D 模型（经 ssc_addon:held override）。
 *
 * 原理：物品栏图标经 DrawContext.drawItem → ItemRenderer.getHeldItemModel 解析 override。
 * held predicate 在手持时返回 1，会触发 override 把物品栏也切到 3D。
 * 这里在 drawItem 入口设 ThreadLocal 标记，held predicate 读标记——GUI 上下文时强制返回 0（不触发 3D）。
 * 手持渲染（HeldItemFeatureRenderer）不走 DrawContext.drawItem，标记保持 false，正常走 3D。
 *
 * 注意：DrawContext.drawItem 有多个重载（带/不带 LivingEntity），背包/创造栏的 slot 渲染
 * 多走不带 LivingEntity 的版本，必须全部拦截才能覆盖所有 GUI 路径。
 */
@Mixin(DrawContext.class)
public class WaterSpearInventoryMixin {

	// 带 LivingEntity 的重载（物品栏带实体上下文时）
	@Inject(
			method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V",
			at = @At("HEAD")
	)
	private void ssc_addon$markGuiContextA(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.setGuiContext(true);
	}

	@Inject(
			method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearGuiContextA(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.clear();
	}

	// 不带 LivingEntity 的 3 参重载（背包 slot 渲染常走此路径）
	@Inject(
			method = "drawItem(Lnet/minecraft/item/ItemStack;II)V",
			at = @At("HEAD")
	)
	private void ssc_addon$markGuiContextB(ItemStack stack, int x, int y, CallbackInfo ci) {
		RenderContextTracker.setGuiContext(true);
	}

	@Inject(
			method = "drawItem(Lnet/minecraft/item/ItemStack;II)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearGuiContextB(ItemStack stack, int x, int y, CallbackInfo ci) {
		RenderContextTracker.clear();
	}

	// 不带 LivingEntity 的 4 参重载（带 seed）
	@Inject(
			method = "drawItem(Lnet/minecraft/item/ItemStack;III)V",
			at = @At("HEAD")
	)
	private void ssc_addon$markGuiContextC(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.setGuiContext(true);
	}

	@Inject(
			method = "drawItem(Lnet/minecraft/item/ItemStack;III)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearGuiContextC(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
		RenderContextTracker.clear();
	}

	// drawItemWithoutEntity 重载（明确无实体上下文，如某些 tooltip/预览）
	@Inject(
			method = "drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;II)V",
			at = @At("HEAD")
	)
	private void ssc_addon$markGuiContextD(ItemStack stack, int x, int y, CallbackInfo ci) {
		RenderContextTracker.setGuiContext(true);
	}

	@Inject(
			method = "drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;II)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearGuiContextD(ItemStack stack, int x, int y, CallbackInfo ci) {
		RenderContextTracker.clear();
	}
}
