package net.jackcooper.shapeShifterCurseAddon.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

/**
 * 法术抗性附魔（jackcooper）：最高 5 纪级，每级 −15% 法术伤害（合计最高 −75%）。
 *
 * <p>数值语义：本类只负责「定义与注册」（名字/稀有度/可附魔装备槽/等级上限/兼容性），
 * 减伤倍率与判定在 {@code SscAddonLivingEntityMixin#ssca$spellResistanceReduce}（服务端
 * LivingEntity.damage HEAD 统一缩放），伤害类型精确匹配 {@code ssc_addon:spell_damage}。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>附魔书可通过创造栏 / 附魔台 / 铁砧获取，需铁砧附到护甲才生效（原版惯例）；</li>
 *   <li>多件护甲等级求和、上限 5（见 mixin 内 {@code totalLevel} 计算），即单件满级
 *       与四件套满级减伤一致，防止完全免疫；</li>
 *   <li>与保护系列附魔<b>互斥</b>（{@link #canAccept} 拒绝已有保护系附魔的物品 / 反向同样成立），
 *       避免与原版保护减伤叠乘过强。</li>
 * </ul>
 */
public class SpellResistanceEnchantment extends Enchantment {

	public SpellResistanceEnchantment(Rarity rarity, EnchantmentTarget target, EquipmentSlot[] slots) {
		super(rarity, target, slots);
	}

	/** 最高 5 级。 */
	@Override
	public int getMaxLevel() {
		return 5;
	}

	/** 最小消耗（附魔台出现门槛）：稀有度自带 + 每级 ×10。 */
	@Override
	public int getMinPower(int level) {
		return 1 + (level - 1) * 10;
	}

	/** 最大消耗（附魔台出现上限）。 */
	@Override
	public int getMaxPower(int level) {
		return super.getMinPower(level) + 50;
	}

	/**
	 * 兼容性：与原版保护系列（保护/火焰保护/爆炸保护/弹射物保护）互斥。
	 * 原版保护系互为互斥组（exclusiveSet），本附魔加入同组需改原版类不可行，
	 * 故在此单向拒绝（铁砧时原版侧的 exclusiveSet 也会拒绝与保护系共存）。
	 */
	@Override
	public boolean canAccept(Enchantment other) {
		return super.canAccept(other)
				&& other != net.minecraft.enchantment.Enchantments.PROTECTION
				&& other != net.minecraft.enchantment.Enchantments.FIRE_PROTECTION
				&& other != net.minecraft.enchantment.Enchantments.BLAST_PROTECTION
				&& other != net.minecraft.enchantment.Enchantments.PROJECTILE_PROTECTION;
	}
}
