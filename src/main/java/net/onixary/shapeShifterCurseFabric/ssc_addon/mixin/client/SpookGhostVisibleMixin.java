package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.NightmareSpookClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端专属：食梦魔「惊吓」幽灵实体（幽灵苦力怕/幽灵野猫）局部显形。
 *
 * <p>服务端 spawn 的幽灵是真实体（invisible=true 对所有人隐身）。只有目标本人的客户端
 * 收过 {@code PACKET_SPOOK_GHOST}（{@code NightmareSpookClient.GHOSTS} 持有 UUID）→
 * 本 mixin 对这些实体把 {@code isInvisible} 局部改回 false，目标就看得见原版渲染的
 * 苦力怕/野猫（正立、原版动画、可被打），其它客户端完全无感知。</p>
 *
 * <p>原理核实：{@code LivingEntityRenderer.render} 内经 {@code isVisible(entity)} →
 * {@code entity.isInvisible()} 判定是否跳过主体渲染（反编译 LivingEntityRenderer
 * offset 433 {@code isVisible} 调用链），故拦 {@code Entity.isInvisible} RETURN 即可。</p>
 */
@Mixin(Entity.class)
public class SpookGhostVisibleMixin {

	@Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
	private void ssca$showGhost(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()
				&& NightmareSpookClient.isGhostVisible(((Entity) (Object) this).getUuid())) {
			cir.setReturnValue(false);
		}
	}
}
