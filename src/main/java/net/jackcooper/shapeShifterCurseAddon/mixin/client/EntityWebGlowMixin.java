package net.jackcooper.shapeShifterCurseAddon.mixin.client;

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

	@Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true, require = 0)
	private void ssca$webHighlightGlow(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && WebHighlightClient.isHighlighted(((Entity) (Object) this).getId())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
	private void ssca$webHighlightColor(CallbackInfoReturnable<Integer> cir) {
		int id = ((Entity) (Object) this).getId();
		if (WebHighlightClient.isHighlighted(id)) {
			cir.setReturnValue(WebHighlightClient.getHighlightColor(id)); // 高亮描边颜色（踩网/敌人蓝、拴住友军绿）
		}
	}
}
