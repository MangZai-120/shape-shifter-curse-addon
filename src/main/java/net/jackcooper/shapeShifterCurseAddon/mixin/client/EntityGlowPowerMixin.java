package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import io.github.apace100.apoli.power.EntityGlowPower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 食梦魔「入梦」透视屏蔽（客户端）：入梦者本地的 entity_glow 透视 power 不再对把它打入梦的
 * 食梦魔描边。Apoli 的 entity_glow 判定全在客户端（MinecraftClient.hasOutline / WorldRenderer，
 * 均以本地玩家为 power 持有者评估 doesApply(被观察实体)），服务端 addStatusEffect 拦截管不到。
 *
 * <p>判定逻辑：被观察实体（doesApply 入参）的 UUID 命中本地镜像
 * {@code NightmareDreamManager.CLIENT_DREAMED_BY}（把本地玩家入梦的食梦魔 → 到期时间，
 * 由晕影包携带 UUID 维护）→ 返回 false 不描边。镜像仅存在于入梦者本人的客户端，
 * 第三方/食梦魔自己的客户端镜像为空 → 行为不变。</p>
 *
 * <p>注：不 @Shadow 父类 Power.entity 字段（Mixin 无法 shadow 继承字段，曾致崩溃，
 * 2026-08-15 实机事故）；本判定也无需持有者身份。EntityGlowPower 是 Apoli 自有类
 * （非 MC intermediary），remap=false + devtime 方法名（同 PowerHolderComponentShadowFixMixin
 * 先例）；doesApply 无重载，按名匹配。</p>
 */
@Mixin(value = EntityGlowPower.class, remap = false)
public class EntityGlowPowerMixin {

	@Inject(method = "doesApply", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void ssca$blockGlowOnDreamTarget(Entity entityToCheck, CallbackInfoReturnable<Boolean> cir) {
		if (entityToCheck == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return;
		// 被观察实体 = 把本地玩家（入梦者）打入梦的食梦魔 → 入梦者的透视对它不描边
		if (net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager
				.clientIsDreamingMe(entityToCheck.getUuid(), client.world.getTime())) {
			cir.setReturnValue(false);
		}
	}
}
