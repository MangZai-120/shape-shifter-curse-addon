package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 腐蚀之雾持续区域结算（jackcooper，服务端权威）。每个施法者至多一团雾
 * （重复施放刷新覆盖），雾随施法者移动（以施法者为圆心）。
 *
 * <p>每 INTERVAL tick 对雾内敌人：中毒 I（POISON_TICKS）+ 缓速 I（2s）；
 * 白名单受保护目标免受。断线清理。</p>
 */
public final class CorruptMistManager {
	private static final List<Mist> MISTS = new ArrayList<>();

	private static final class Mist {
		final UUID casterId;
		final ServerPlayerEntity caster;
		final double radius;
		int ticksRemaining;
		final int intervalTicks;
		int ticksToNextPulse;
		final int level;

		Mist(ServerPlayerEntity caster, double radius, int durationTicks, int intervalTicks, int level) {
			this.casterId = caster.getUuid();
			this.caster = caster;
			this.radius = radius;
			this.ticksRemaining = durationTicks;
			this.intervalTicks = intervalTicks;
			this.ticksToNextPulse = intervalTicks;
			this.level = level;
		}
	}

	private CorruptMistManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Mist> it = MISTS.iterator();
			while (it.hasNext()) {
				Mist mist = it.next();
				// 施法者离线/被移除 → 雾消散（DISCONNECT 已兜底，这里防意外引用）
				if (mist.caster.isRemoved()) {
					it.remove();
					continue;
				}
				if (--mist.ticksRemaining <= 0) {
					it.remove();
					continue;
				}
				// 雾粒子（每 10 tick 一轮，服务端撒，天然多人同步）
				if (mist.ticksRemaining % 10 == 0 && mist.caster.getWorld() instanceof ServerWorld serverWorld) {
					double r = mist.radius;
					double angle = serverWorld.getRandom().nextDouble() * 2 * Math.PI;
					double dist = serverWorld.getRandom().nextDouble() * r;
					serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH,
							mist.caster.getX() + Math.cos(angle) * dist,
							mist.caster.getY() + 0.3 + serverWorld.getRandom().nextDouble() * 0.8,
							mist.caster.getZ() + Math.sin(angle) * dist,
							2, 0.2, 0.1, 0.2, 0.005);
				}
				// 每跳结算
				if (--mist.ticksToNextPulse <= 0) {
					mist.ticksToNextPulse = mist.intervalTicks;
					pulse(mist);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				MISTS.removeIf(m -> m.casterId.equals(handler.player.getUuid())));
	}

	/** 施放/刷新一团雾（同施法者旧雾被覆盖）。 */
	public static void start(ServerPlayerEntity caster, double radius, int durationTicks,
	                         int intervalTicks, int level) {
		MISTS.removeIf(m -> m.casterId.equals(caster.getUuid()));
		MISTS.add(new Mist(caster, radius, durationTicks, intervalTicks, level));
	}

	/** 单跳结算：雾内敌人中毒 + 缓速（白名单免受；L4+ 中毒 II）。 */
	private static void pulse(Mist mist) {
		if (!(mist.caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		// 高等级毒更深：L4+ 中毒 II（紫/橙卷轴）
		int poisonAmplifier = mist.level >= 4 ? 1 : 0;
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				mist.caster.getBoundingBox().expand(mist.radius), e -> e != mist.caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(mist.caster) > mist.radius) {
				continue;
			}
			if (WhitelistUtils.isProtected(mist.caster, target)) {
				continue;
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON,
					net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell.POISON_TICKS, poisonAmplifier));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
		}
	}
}
