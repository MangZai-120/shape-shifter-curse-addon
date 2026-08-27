package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.NightmareFearClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 食梦魔「恐惧」1 秒隐匿窗口（客户端）：恐惧目标在梦魔进入其 16 格视野时，
 * 有 1 秒时间<b>完全看不见</b>该梦魔（实体渲染整体跳过，第一/第三人称一致，
 * 仅本地屏蔽——未恐惧的其它玩家正常看见）。
 *
 * <p>挂 {@code EntityRenderer.shouldRender} HEAD cancellable：命中隐匿窗口且
 * 被渲染者是「梦魔玩家」→ 返回 false 跳过整个渲染。名称牌等附属渲染随实体
 * 一并被跳过（shouldRender 是实体渲染总入口）。</p>
 */
@Mixin(EntityRenderer.class)
public class FearHideRendererMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private <T extends Entity> void ssca$hideNightmareDuringFear(T entity, Frustum frustum,
	                                                             double x, double y, double z,
	                                                             CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof PlayerEntity player)) return;
		if (NightmareFearClient.isHidden(player.getUuid())) {
			cir.setReturnValue(false);
		}
	}
}
