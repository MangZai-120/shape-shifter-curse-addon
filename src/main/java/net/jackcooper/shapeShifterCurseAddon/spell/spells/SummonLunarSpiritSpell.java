package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * 召唤月灵（召唤系，蓝色基底，jackcooper）：召唤 1 只月灵协战（L3+ 2 只），
 * 寿命 30s + 10s/等级。月灵跟随主人、攻击主人攻击过的目标（伤害归因主人）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/summon_lunar_spirit.json}：
 * cd 30s / 耗蓝 35；召唤数量与寿命由等级在服务端定（数量 1/2 只）。</p>
 */
public class SummonLunarSpiritSpell extends Spell {

	/** 基础寿命（tick）：30s + 10s/级。 */
	private static final int BASE_LIFE_TICKS = 600;
	private static final int LIFE_PER_LEVEL = 200;

	public SummonLunarSpiritSpell() {
		super(new Identifier("ssc_addon", "summon_lunar_spirit"), SpellRarity.BLUE);
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
		// 召唤数量：每两级 +1（L1-2=1只、L3-4=2只、L5=3只）
		int count = 1 + (level - 1) / 2;
		int lifeTicks = BASE_LIFE_TICKS + (level - 1) * LIFE_PER_LEVEL;
		for (int i = 0; i < count; i++) {
			LunarSpiritEntity spirit = new LunarSpiritEntity(
					net.jackcooper.shapeShifterCurseAddon.SscAddon.LUNAR_SPIRIT_ENTITY, serverWorld);
			spirit.setOwnerUuid(caster.getUuid());
			spirit.setLifeTicks(lifeTicks);
			// 三色循环分配：第1只粉（火）、第2只蓝（冰）、第3只绿（毒）
			spirit.setVariant(i % 3);
			// 编队槽位：按召唤序号左右分列在主人背后，多只不重叠
			spirit.setFormationSlot(i);
			// 出生位置：主人周围偏上随机散布
			double angle = serverWorld.getRandom().nextDouble() * 2 * Math.PI;
			spirit.refreshPositionAndAngles(
					caster.getX() + Math.cos(angle) * 1.2,
					caster.getY() + 1.5,
					caster.getZ() + Math.sin(angle) * 1.2,
					serverWorld.getRandom().nextFloat() * 360f, 0f);
			serverWorld.spawnEntity(spirit);
			// 出生演出：月辉汇聚
			serverWorld.spawnParticles(ParticleTypes.END_ROD,
					spirit.getX(), spirit.getBodyY(0.5), spirit.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
		}
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.4f);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}
}
