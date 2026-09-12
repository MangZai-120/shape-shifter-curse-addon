package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.ability.CompanionResonanceManager;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 伙伴共鸣（召唤系，白色基底，jackcooper）：强化自己的宠物（狼/猫/鹦鹉等驯服生物）
 * 与自己的召唤物（月灵等归属自己的友方实体）。增益类：白名单内同样受益。
 *
 * <p>增益按等级成长：</p>
 * <ul>
 *   <li>伤害：每级 +2 点（属性修饰符，固定 UUID 到期移除，见
 *       {@link CompanionResonanceManager}）；</li>
 *   <li>迅捷：每两级 +1 级（L1=迅捷 I、L3=迅捷 II、L5=迅捷 III）；</li>
 *   <li>抗性：L3 起每两级 +1 级（L3/L4=抗性 I、L5=抗性 II）。</li>
 * </ul>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/companion_resonance.json}：
 * 基准半径 8 格 / 30s + 10s/级 / cd 25s / 耗蓝 15。</p>
 */
public class CompanionResonanceSpell extends Spell {

	/** 增益半径（格）。 */
	private static final double RADIUS = 8.0;
	/** 基础时长（tick）：30s。 */
	private static final int BASE_DURATION_TICKS = 600;
	/** 每级增加的时长（tick）：10s。 */
	private static final int DURATION_PER_LEVEL = 200;

	public CompanionResonanceSpell() {
		super(new Identifier("ssc_addon", "companion_resonance"), SpellRarity.WHITE);
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
		int duration = BASE_DURATION_TICKS + (level - 1) * DURATION_PER_LEVEL;
		// 状态效果等级：迅捷每两级 +1（L1=0、L3=1、L5=2）；抗性 L3 起每两级 +1（L3=0、L5=1）
		int speedAmplifier = (level - 1) / 2;
		int resistanceAmplifier = level >= 3 ? (level - 3) / 2 : -1; // -1 = 不加抗性
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class,
				caster.getBoundingBox().expand(RADIUS), e -> e != caster && e.isAlive());
		int buffed = 0;
		for (LivingEntity target : targets) {
			if (target.distanceTo(caster) > RADIUS) {
				continue;
			}
			// 目标筛选：自己的驯服宠物 或 自己的召唤物（月灵）
			boolean isOwnPet = target instanceof TameableEntity tameable
					&& tameable.isTamed() && tameable.getOwner() == caster;
			boolean isOwnSummon = target instanceof LunarSpiritEntity spirit
					&& caster.getUuid().equals(spirit.getOwnerUuid());
			if (!isOwnPet && !isOwnSummon) {
				continue;
			}
			// 伤害：每级 +2（属性修饰符，到期移除）
			CompanionResonanceManager.apply(target, level, duration);
			// 迅捷：每两级 +1 级
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, speedAmplifier));
			// 抗性：L3 起每两级 +1 级
			if (resistanceAmplifier >= 0) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, resistanceAmplifier));
			}
			buffed++;
			// 单体演出：绿色共鸣上升粒子
			serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
					target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
		// 施法者反馈（无目标时也提示，避免「无反应」困惑）
		if (buffed == 0) {
			caster.sendMessage(net.minecraft.text.Text.translatable("message.ssc_addon.spell.companion_none")
					.formatted(net.minecraft.util.Formatting.YELLOW), true);
		} else {
			caster.sendMessage(net.minecraft.text.Text.translatable("message.ssc_addon.spell.companion_buffed", buffed)
					.formatted(net.minecraft.util.Formatting.GREEN), true);
		}
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.8f);
	}
}
