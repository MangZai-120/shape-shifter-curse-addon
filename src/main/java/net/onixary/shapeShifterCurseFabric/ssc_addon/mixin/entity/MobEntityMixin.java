package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAggroTracker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {

	/**
	 * 每个生物实例独立记录：最后一次看到挑衅玩家的世界时间（-1=未追踪）
	 */
	@Unique
	private long ssc_addon$lastSawProvokedTarget = -1;

	/**
	 * 判断生物是否与玩家形态匹配的中立生物。
	 * 裁决者: 所有亡灵中立
	 * 金沙岚: 仅尸壳和咒文胡狼中立
	 */
	@Unique
	private boolean ssc_addon$isNeutralMobPair(MobEntity mob, PlayerEntity player) {
		if (FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
			return mob.getGroup() == EntityGroup.UNDEAD;
		}
		if (FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			return mob instanceof HuskEntity || FormUtils.isTransformativeWolf(mob);
		}
		return false;
	}

	/**
	 * 检查生物是否应该忽略该玩家（保持中立）。
	 * 返回true=忽略（不攻击），返回false=不忽略（可攻击）
	 */
	@Unique
	private boolean ssc_addon$shouldUndeadIgnore(MobEntity mob, PlayerEntity player) {
		if (!ssc_addon$isNeutralMobPair(mob, player)) return false;
		// 玩家处于挑衅状态 → 生物可攻击
		return !UndeadNeutralState.isPlayerProvoked(player.getUuid(), mob.getWorld().getTime());
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onSetTarget(LivingEntity target, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		// 契灵：mob 自然丢失目标（vanilla setTarget(null)），清除激怒记录 → "逃离重置"
		if (target == null) {
			LivingEntity prev = self.getTarget();
			if (prev instanceof PlayerEntity pp && FormUtils.isForm(pp, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				MancianimaAggroTracker.forget(self.getUuid());
			}
		}
		if (target != null) {
			if (self instanceof IronGolemEntity golem
					&& target instanceof PlayerEntity player
					&& FormUtils.isAllaySP(player)) {
				if (golem.getTarget() == player) {
					golem.setTarget(null);
				}
				golem.setAngryAt(null);
				golem.setAngerTime(0);
				ci.cancel();
				return;
			}
			if (target.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
				ci.cancel();
				return;
			}
			if (target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
				ci.cancel();
				return;
			}
			// 幻翼亲和：幻翼是失眠者梦魇的进生物，视食梦魔为同类——绝不将其设为目标
			// （含主动索敌与受击反击，同 PLAYING_DEAD/TRUE_INVISIBILITY 先例全拦；被打仍可还手由玩家主动选择）
			if (self instanceof PhantomEntity
					&& target instanceof PlayerEntity phantomTarget
					&& FormUtils.isForm(phantomTarget, FormIdentifiers.WILD_CAT_NIGHTMARE)) {
				ci.cancel();
				return;
			}
			// 食梦魔「恐惧」非玩家目标：恐惧期间 mob 无法主动获得对该目标的仇恨；
			// 被梦魔攻击后仅 2 秒反击窗口（isMobAggroAllowed 判 lastAttackedTime），窗口外拦截
			if (target != null
					&& !net.jackcooper.shapeShifterCurseAddon.ability.NightmareFearManager.isMobAggroAllowed(self, target)) {
				ci.cancel();
				return;
			}
			// 劫掠阵营不攻击女巫使魔
			if ((self instanceof RaiderEntity || self instanceof VexEntity || self instanceof WitchEntity)
					&& target instanceof WitchFamiliarEntity) {
				ci.cancel();
				return;
			}
			// 女巫使魔不攻击劫掠阵营
			if (self instanceof WitchFamiliarEntity
					&& (target instanceof RaiderEntity || target instanceof VexEntity || target instanceof WitchEntity || target instanceof WitchFamiliarEntity)) {
				ci.cancel();
				return;
			}
			// 灾厄中立
			if ((self instanceof RaiderEntity || self instanceof VexEntity)
					&& target instanceof PlayerEntity player
					&& player.getCommandTags().contains("ssc_raid_friend")) {
				ci.cancel();
				return;
			}
			// 亡灵中立：阻止主动索敌，但允许受击后反击
			if (target instanceof PlayerEntity player
					&& ssc_addon$shouldUndeadIgnore(self, player)) {
				ci.cancel();
				return;
			}
			// 契灵：mob 默认不主动攻击契灵，除非被激怒；坚守者/铁傀儡 不受此限制
			if (target instanceof PlayerEntity player
					&& FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
					&& !(self instanceof WardenEntity)
					&& !(self instanceof IronGolemEntity)) {
				if (!MancianimaAggroTracker.isAngered(self.getUuid(), player.getUuid())) {
					ci.cancel();
				}
			}
		}
	}

	@Inject(method = "mobTick", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onMobTick(CallbackInfo ci) {
		MobEntity mob = (MobEntity) (Object) this;
		// 食梦魔「恐惧」：恐惧中的 mob 每 tick 检查仇恨是否过期（被击 2 秒后清目标）
		if (!mob.getWorld().isClient()) {
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareFearManager.tickFearedMobAggro(mob);
		}

		// 1. 眩晕逻辑
		if (mob.hasStatusEffect(SscAddon.STUN)) {
			ci.cancel();
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null) {
			ssc_addon$lastSawProvokedTarget = -1;
			return;
		}

		// 2. 真隐身脱战
		if (target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
			mob.setTarget(null);
			return;
		}

		// 3. 灾厄联盟脱战
		if ((mob instanceof RaiderEntity || mob instanceof VexEntity)
				&& target instanceof PlayerEntity player
				&& player.getCommandTags().contains("ssc_raid_friend")) {
			mob.setTarget(null);
			return;
		}

		// 4. 中立生物脱战机制：基于视野的脱战机制（类似僵尸猪灵）
		if (target instanceof PlayerEntity player
				&& ssc_addon$isNeutralMobPair(mob, player)) {
			// 挑衅已过期 → 立即脱战
			if (!UndeadNeutralState.isPlayerProvoked(player.getUuid(), mob.getWorld().getTime())) {
				mob.setTarget(null);
				ssc_addon$lastSawProvokedTarget = -1;
				return;
			}
			long worldTime = mob.getWorld().getTime();
			if (mob.canSee(target)) {
				// 能看到目标 → 重置视野计时，同时刷新全局挑衅
				ssc_addon$lastSawProvokedTarget = worldTime;
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), worldTime);
			} else {
				// 看不到目标 → 脱战倒计时
				if (ssc_addon$lastSawProvokedTarget < 0) {
					ssc_addon$lastSawProvokedTarget = worldTime;
				}
				if (worldTime - ssc_addon$lastSawProvokedTarget > UndeadNeutralState.SIGHT_TIMEOUT) {
					mob.setTarget(null);
					ssc_addon$lastSawProvokedTarget = -1;
				}
			}
		}
	}
}
