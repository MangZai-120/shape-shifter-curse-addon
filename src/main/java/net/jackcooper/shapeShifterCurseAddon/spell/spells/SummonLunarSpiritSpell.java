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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.Entity;

/**
 * 召唤月灵（召唤系，蓝色基底，jackcooper）：召唤月灵协战（L1-2=1只、L3-4=2只、L5=3只），
 * 寿命 30s + 10s/等级。月灵跟随主人、攻击主人攻击过的目标（伤害归因主人）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/summon_lunar_spirit.json}：
 * cd 30s / 耗蓝 35；召唤数量与寿命由等级在服务端定（数量 1/2 只）。
 * 契约容量（阶段 B / 计划书 §11.2）：同时存活月灵上限 {@link #CONTRACT_CAPACITY} 只，
 * 满编时新召唤自动顶替最早的（不拦截施法、不误删其它实体）。</p>
 */
public class SummonLunarSpiritSpell extends Spell {

	/** 基础寿命（tick）：30s + 10s/级。 */
	private static final int BASE_LIFE_TICKS = 600;
	private static final int LIFE_PER_LEVEL = 200;

	/** 契约容量：同时存活的月灵总数上限（每只占 1；超出顶替最早召唤的）。 */
	public static final int CONTRACT_CAPACITY = 6;

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
		// 契约容量（阶段 B）：超出容量的旧月灵（最早召唤优先）被新召唤顶替——不拦截施法、
		// 不扣费失败，满编时自动腾位（计划书 §11.2「替换预览」的服务端等价实现）。
		// 全维度计数（修复：原先只查施法者周围 128 格，远处月灵不计入可超额存活）
		List<LunarSpiritEntity> owned = new ArrayList<>();
		for (Entity e : serverWorld.iterateEntities()) {
			if (e instanceof LunarSpiritEntity s && s.isAlive()
					&& caster.getUuid().equals(s.getOwnerUuid())) {
				owned.add(s);
			}
		}
		int overflow = owned.size() + count - CONTRACT_CAPACITY;
		if (overflow > 0) {
			// 存活时间最长的旧月灵先被顶替。
			owned.sort(Comparator.comparingInt((LunarSpiritEntity spirit) -> spirit.age).reversed());
			for (int i = 0; i < overflow && i < owned.size(); i++) {
				owned.get(i).discard();
			}
		}
		int lifeTicks = BASE_LIFE_TICKS + (level - 1) * LIFE_PER_LEVEL;
		for (int i = 0; i < count; i++) {
			LunarSpiritEntity spirit = new LunarSpiritEntity(
					net.jackcooper.shapeShifterCurseAddon.SscAddon.LUNAR_SPIRIT_ENTITY, serverWorld);
			spirit.setOwnerUuid(caster.getUuid());
			spirit.setLifeTicks(lifeTicks);
			// 三色纯随机分配（2026-09-18 用户定稿：粉/蓝/绿随机，不再按召唤序号固定）；
			// 多只同色属正常结果，每只独立掷骰
			spirit.setVariant(serverWorld.getRandom().nextInt(3));
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
