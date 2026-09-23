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
		final UUID castId;
		final int poisonTicks;
		final int slownessTicks;

		Mist(ServerPlayerEntity caster, double radius, int durationTicks, int intervalTicks, int level, UUID castId) {
			this.casterId = caster.getUuid();
			this.caster = caster;
			this.radius = radius;
			this.ticksRemaining = durationTicks;
			this.intervalTicks = intervalTicks;
			this.ticksToNextPulse = intervalTicks;
			this.level = level;
			this.castId = castId;
			int basePoisonTicks = net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell.POISON_TICKS;
			boolean venomAffinity = castId != null && net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(
					caster, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SPIDER_SALTICIDAE);
			this.poisonTicks = (castId == null ? basePoisonTicks
					: net.jackcooper.shapeShifterCurseAddon.spell.FormAffinity.curseDurationTicks(caster, basePoisonTicks))
					+ (venomAffinity ? 60 : 0);
			this.slownessTicks = castId == null ? 40
					: net.jackcooper.shapeShifterCurseAddon.spell.FormAffinity.curseDurationTicks(caster, 40);
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
					it.remove();
					continue;
				}
				if (--mist.ticksRemaining <= 0) {
					it.remove();
					continue;
				}
				// 雾粒子（每 5 tick 一轮，滞留药水风格：圆盘内均匀密度覆盖，范围轮廓大致可见）
				if (mist.ticksRemaining % 5 == 0 && mist.caster.getWorld() instanceof ServerWorld serverWorld) {
					double r = mist.radius;
					// 按面积撒点：密度恒定，半径越大数量越多 → 边界轮廓可辨（仿滞留药水云）
					int count = (int) Math.max(10, r * r * 4);
					for (int i = 0; i < count; i++) {
						// 均匀圆盘采样：随机角度 + sqrt 均匀半径（避免中心聚堆）
						double angle = serverWorld.getRandom().nextDouble() * 2 * Math.PI;
						double dist = Math.sqrt(serverWorld.getRandom().nextDouble()) * r;
						double px = mist.caster.getX() + Math.cos(angle) * dist;
						double pz = mist.caster.getZ() + Math.sin(angle) * dist;
						double py = mist.caster.getY() + 0.1 + serverWorld.getRandom().nextDouble() * 0.9;
						// 紫（主）+ 绿（辅，约 1/4）混搭
						if (serverWorld.getRandom().nextInt(4) == 0) {
							serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
									px, py, pz, 1, 0.15, 0.1, 0.15, 0.0);
						} else {
							serverWorld.spawnParticles(ParticleTypes.DRAGON_BREATH,
									px, py, pz, 1, 0.15, 0.1, 0.15, 0.002);
						}
					}
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
	                         int intervalTicks, int level, UUID castId) {
		MISTS.removeIf(m -> m.casterId.equals(caster.getUuid()));
		MISTS.add(new Mist(caster, radius, durationTicks, intervalTicks, level, castId));
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
			boolean applied = target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, mist.poisonTicks, poisonAmplifier));
			applied |= target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, mist.slownessTicks, slownessAmplifier));
			if (applied) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(mist.caster, target,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.CURSE, mist.castId, hadHarmfulEffect);
			}
		}
	}
}
