package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.WebHighlightClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端专属：让「踩网蓝色高亮」表中的实体在本机描蓝边。
 * 只有施法者客户端持有该表（由 S2C 包填充）→ 实现「仅施法者可见的蓝色高亮」。
 * isGlowing 返回 true 触发原版实体描边渲染；getTeamColorValue 返回蓝色决定描边颜色。
 */
@Mixin(Entity.class)
public class EntityWebGlowMixin {

	@Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
	private void ssca$webHighlightGlow(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && WebHighlightClient.isHighlighted(((Entity) (Object) this).getId())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
	private void ssca$webHighlightColor(CallbackInfoReturnable<Integer> cir) {
		if (WebHighlightClient.isHighlighted(((Entity) (Object) this).getId())) {
			cir.setReturnValue(0x3AA0FF); // 蓝色描边
		}
	}
}
