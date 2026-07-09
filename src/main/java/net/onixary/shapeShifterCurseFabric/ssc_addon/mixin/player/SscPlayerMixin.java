package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAggroTracker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SscPlayerMixin {

	/**
	 * 「月痕之力」剧情真睡期间，阻止该玩家参与原版跳夜判定（{@code canResetTimeBySleeping} 返回 false），
	 * 从而保证真睡不推进时间 / 不跳夜。仅在剧情真睡中生效（客户端 STORY_SLEEPING 为空、不介入）。
	 */
	@Inject(method = "canResetTimeBySleeping", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$preventMoonScarStorySleepSkip(CallbackInfoReturnable<Boolean> cir) {
		UUID uuid = ((PlayerEntity) (Object) this).getUuid();
		if (net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager.isStorySleeping(uuid)
				|| net.onixary.shapeShifterCurseFabric.ssc_addon.story.TideSpiritStoryManager.isStorySleeping(uuid)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * 「月痕之力」剧情真睡：sp 使魔上床真正睡眠期间，顶住 SSC {@code CursedMoonWorldMixin} 每 tick
	 * 对其的「诅咒之月强制唤醒」（SSC 唯一禁睡点），让玩家能真正睡着、不被弹起。
	 * <p>
	 * <b>关键</b>：SSC 调的是 {@code player.wakeUp()}（无参），而 {@code PlayerEntity} 重写了无参
	 * {@code wakeUp()} → 它覆盖了 {@code LivingEntity.wakeUp()}，所以必须注入 <b>PlayerEntity 层</b>的
	 * {@code wakeUp()V} 才拦得住（注入 LivingEntity 层会被多态绕过，这正是之前「假睡」抖动的根因）。
	 * <p>
	 * 仅对「处于剧情真睡的服务端玩家」生效（{@code instanceof ServerPlayerEntity} 短路，客户端实体不读状态、
	 * 无跨线程访问）；睡满后由 {@code MoonScarStoryManager.tickStorySleep} 移除标记后主动叫醒。
	 */
	@Inject(method = "wakeUp()V", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$keepMoonScarStorySleeping(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity sp) {
			UUID uuid = sp.getUuid();
			if (net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager.isStorySleeping(uuid)
					|| net.onixary.shapeShifterCurseFabric.ssc_addon.story.TideSpiritStoryManager.isStorySleeping(uuid)) {
				ci.cancel();
			}
		}
	}

	@ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), ordinal = 2)
	private boolean forceCrit(boolean isCritical) {
		if (((PlayerEntity) (Object) this).hasStatusEffect(SscAddon.GUARANTEED_CRIT)) {
			return true;
		}
		return isCritical;
	}

	/**
	 * 契灵基础攻击设定（用 @WrapOperation 兼容 SSC 主包同位置的 @Redirect）：
	 * - 无法攻击劫掠阵营生物
	 * - 空手：固定 1 颗心（2.0F），无视护甲（indirectMagic 伤害源）
	 * - 持武器：总伤害 -20%（暴击通过 base*0.8*1.5 = base*1.2 自动 -20%）
	 * - 主动攻击 mob → 激怒该 mob（之后 mob 才能将契灵设为目标）
	 */
	@WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
	private boolean ssc_addon$mancianimaAttackScale(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!FormUtils.isForm(self, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			return original.call(target, source, amount);
		}
		// 无法攻击劫掠阵营生物
		if (target instanceof RaiderEntity) {
			if (!self.getWorld().isClient()) {
				self.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.8f);
			}
			return false;
		}
		boolean bareHand = self.getMainHandStack().isEmpty();
		boolean result;
		if (bareHand) {
			DamageSource bypassSrc = self.getDamageSources().indirectMagic(self, self);
			result = original.call(target, bypassSrc, 2.0F);
		} else {
			result = original.call(target, source, amount * 0.8F);
		}
		// 激怒被攻击的 mob（仅在伤害命中时记录；坚守者/铁傀儡 本就主动攻击，仍记录无副作用）
		if (result && target instanceof MobEntity mob && !self.getWorld().isClient()) {
			MancianimaAggroTracker.provoke(mob.getUuid(), self.getUuid());
		}
		return result;
	}
}
