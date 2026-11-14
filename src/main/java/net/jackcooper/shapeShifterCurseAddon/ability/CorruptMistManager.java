package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
		final UUID visualId = UUID.randomUUID();
		final int durationTicks;
		final double visualExtent;
		final double[] visualParams;
		int ticksRemaining;
		final int intervalTicks;
		int ticksToNextPulse;
		final int level;
		final UUID castId;
		final int poisonTicks;
		final int slownessTicks;
        final net.jackcooper.shapeShifterCurseAddon.spell.BatSpellLink.Cast bat;
        final net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.Cast court;
        final net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext anomaly;

		Mist(ServerPlayerEntity caster, double radius, int durationTicks, int intervalTicks, int level, UUID castId) {
			this.casterId = caster.getUuid();
            this.bat=net.jackcooper.shapeShifterCurseAddon.spell.BatSpellLink.current();
            this.court=net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.current();
            this.anomaly=net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.current();
			this.caster = caster;
			this.radius = radius;
			this.durationTicks = durationTicks;
			this.visualExtent = Math.hypot(radius, 1);
			this.visualParams = new double[]{radius, durationTicks};
			this.ticksRemaining = durationTicks;
			this.intervalTicks = intervalTicks;
			this.ticksToNextPulse = intervalTicks;
			this.level = level;
			this.castId = castId;
            this.poisonTicks = net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell.poisonDuration(caster, castId == null);
            this.slownessTicks = net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell.slownessDuration(caster, castId == null);
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
				if (mist.caster.isRemoved() || !mist.caster.isAlive()) {
					ParametricVisuals.stop(mist.visualId, ParametricVisuals.Kind.CORRUPT_MIST);
					it.remove();
					continue;
				}
				if (--mist.ticksRemaining <= 0) {
					ParametricVisuals.stop(mist.visualId, ParametricVisuals.Kind.CORRUPT_MIST);
					it.remove();
					continue;
				}
				// Send the disk recipe once; every viewer samples the same event/tick on its client.
				ParametricVisuals.touch(mist.caster, mist.visualId, ParametricVisuals.Kind.CORRUPT_MIST,
						mist.casterId, mist.caster.getPos(), true,
						mist.caster.getWorld().getTime() - (mist.durationTicks - mist.ticksRemaining),
						mist.durationTicks, mist.visualExtent, mist.visualParams);
				// 每跳结算
				if (--mist.ticksToNextPulse <= 0) {
					mist.ticksToNextPulse = mist.intervalTicks;
					pulse(mist);
				}
			}
		});
		net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) ->
				removeMist(handler.player.getUuid()));
	}

	/** 施放/刷新一团雾（同施法者旧雾被覆盖）。 */
	public static void start(ServerPlayerEntity caster, double radius, int durationTicks,
	                         int intervalTicks, int level, UUID castId) {
		removeMist(caster.getUuid());
		MISTS.add(new Mist(caster, radius, durationTicks, intervalTicks, level, castId));
	}

	private static void removeMist(UUID casterId) {
		MISTS.removeIf(mist -> {
			if (!mist.casterId.equals(casterId)) return false;
			ParametricVisuals.stop(mist.visualId, ParametricVisuals.Kind.CORRUPT_MIST);
			return true;
		});
	}

	/** 单跳结算：雾内敌人中毒 + 缓速（白名单免受；L4+ 中毒 II）。 */
	private static void pulse(Mist mist) {
		if (!(mist.caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		// 高等级毒更深：L4+ 中毒 II（紫/橙卷轴）
		int poisonAmplifier = mist.level >= 4 ? 1 : 0;
		// 雾跳缓速：L5 升 II 级（2026-09-17 用户定稿）
		int slownessAmplifier = mist.level >= 5 ? 1 : 0;
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				mist.caster.getBoundingBox().expand(mist.radius), e -> e != mist.caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(mist.caster) > mist.radius) {
				continue;
			}
			// 领域隔离：目标被任一领域壳隔开（与施法者分属内外）→ 不中毒不缓速
			if (net.jackcooper.shapeShifterCurseAddon.spell.DomainManager.blocksTargeting(mist.caster, target)) {
				continue;
			}
			if (WhitelistUtils.isProtected(mist.caster, target)) {
				continue;
			}
			boolean hadHarmfulEffect = net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.hasHarmfulEffect(target);
            boolean applied=net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.with(mist.court,()->net.jackcooper.shapeShifterCurseAddon.spell.NightmareSpellLink.with(mist.caster,()->net.jackcooper.shapeShifterCurseAddon.spell.BatSpellLink.with(mist.bat,()->target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON,mist.poisonTicks,poisonAmplifier)))));
            boolean slowed=net.jackcooper.shapeShifterCurseAddon.spell.CourtSpellLink.with(mist.court,()->net.jackcooper.shapeShifterCurseAddon.spell.NightmareSpellLink.with(mist.caster,()->net.jackcooper.shapeShifterCurseAddon.spell.BatSpellLink.with(mist.bat,()->target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,mist.slownessTicks,slownessAmplifier)))));
            applied|=slowed;
            if (applied || net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.acceptedStatus(target,StatusEffects.POISON,mist.poisonTicks,poisonAmplifier,false)
                    || net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.acceptedStatus(target,StatusEffects.SLOWNESS,mist.slownessTicks,slownessAmplifier,false))
                net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.hit(mist.anomaly,target);
			if (applied) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(mist.caster, target,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.CURSE, mist.castId, hadHarmfulEffect);
			}
		}
	}
}
