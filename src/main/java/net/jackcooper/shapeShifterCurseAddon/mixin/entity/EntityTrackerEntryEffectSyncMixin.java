package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 追踪开始时补发实体状态效果包（羊了个羊配套，2026-09-30 根因修复）。
 *
 * <p><b>根因（反编译 class_3231 实证）</b>：1.20.1 的 {@code EntityTrackerEntry.sendPackets}
 * 只发 spawn / 元数据 / 属性 / 装备 / 乘客包，<b>不含任何效果包</b>——玩家开始追踪一只
 * 带效果的 mob 时（退出重进、走远再走回、区块重载），客户端不知道它带着效果。变羊 NPC
 * 的渲染 mixin 判 {@code hasStatusEffect(SHEEP_FORM)}，客户端侧恒 false → 立刻画回原模型
 * （「玩家退出再回来羊立刻变回原型」的由来）。1.20.3+ 原版已在追踪包里补上效果同步，
 * 本 mixin 对齐该行为。</p>
 *
 * <p><b>影响面</b>：全量补发该实体当前所有效果（与 1.20.3+ 原版一致）——顺带修复其它
 * SSCA 效果（诅咒印记/潮汐减速/血雾形态等）在重追踪后客户端丢失显示的同类问题。
 * 客户端按效果 id 覆盖式添加，重复补发幂等；效果到期时原版照常发移除包，最终一致。</p>
 */
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryEffectSyncMixin {

	@Shadow
	private Entity entity;

	@Inject(method = "startTracking", at = @At("TAIL"))
	private void ssca$syncStatusEffects(ServerPlayerEntity player, CallbackInfo ci) {
		if (entity instanceof LivingEntity living) {
			for (StatusEffectInstance effect : living.getStatusEffects()) {
				player.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(living.getId(), effect));
			}
		}
	}
}
