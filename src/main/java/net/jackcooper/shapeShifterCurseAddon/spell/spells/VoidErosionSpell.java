package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 虚空侵蚀（虚无系，绿色基底，jackcooper）：以自身为圆心爆发虚无波，
 * 范围内敌人挖掘疲劳 + 虚弱 8s（对怪物对玩家均生效，走白名单）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/void_erosion.json}：
 * 基准半径 3 格 / 8s / cd 20s（L5 12s，每级 -2s）/ 耗蓝 20；半径按 speed_multiplier 缩放。</p>
 */
public class VoidErosionSpell extends Spell {

	/** 基础半径（格）默认，实际半径 = 基础 × speed_multiplier(level)；运行时从 balance 快照读取。 */
	private static final double BASE_RADIUS = 3.0;
	/** 减益时长（tick）：8s。默认值；运行时从 balance 快照读取。 */
	private static final int DURATION_TICKS = 160;

	// 阶段 5：运行时快照读取（spells.void_erosion；快照未初始化回退上方默认常量）
	private static final BalanceReader BAL = new BalanceReader("spells.void_erosion");

	public VoidErosionSpell() {
		super(new Identifier("ssc_addon", "void_erosion"), SpellRarity.GREEN);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		if (!(caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		double radius = BAL.d("base_radius", BASE_RADIUS) * getSpeedMultiplier(level);
		// 减益时长运行时读取（下方两种减益共用局部变量，快照未初始化回退默认常量）
		int durationTicks = BAL.i("duration_ticks", DURATION_TICKS);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(radius), e -> e != caster && e.isAlive());
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > radius) {
				continue;
			}
			// 领域隔离：目标被任一领域壳隔开（与施法者分属内外）→ 不变减益
			if (net.jackcooper.shapeShifterCurseAddon.spell.DomainManager.blocksTargeting(caster, target)) {
				continue;
			}
			// 默认白名单：受保护目标免受减益
			if (WhitelistUtils.isProtected(caster, target)) {
				continue;
			}
			// 减益每两级 +1 级：L1/L2=疲劳 I + 虚弱 II、L3/L4=II + III、L5=III + IV
			int debuffAmplifier = (level - 1) / 2;
			boolean applied = target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, durationTicks, debuffAmplifier));
			applied |= target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, durationTicks, 1 + debuffAmplifier));
			if (applied) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(caster, target,
						net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.VOID,
						solo ? null : ssc_addon$getRefundCastId());
			}
		}
		// 演出：暗紫侵蚀波纹（双圈）+ 低沉音效
		net.jackcooper.shapeShifterCurseAddon.util.SpellFxUtils.ring(serverWorld, caster, ParticleTypes.PORTAL,
				caster.getX(), caster.getY() + 0.2, caster.getZ(), radius * 0.6, 20);
		net.jackcooper.shapeShifterCurseAddon.util.SpellFxUtils.ring(serverWorld, caster, ParticleTypes.PORTAL,
				caster.getX(), caster.getY() + 0.4, caster.getZ(), radius, 28);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.6f);
	}
}
