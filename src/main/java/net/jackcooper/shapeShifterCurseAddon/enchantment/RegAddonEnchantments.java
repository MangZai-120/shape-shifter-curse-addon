package net.jackcooper.shapeShifterCurseAddon.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 附属附魔注册（jackcooper）。
 *
 * <p>当前注册：</p>
 * <ul>
 *   <li><b>法术抗性 spell_resistance</b>（最高 5 级，每级 −15% 法术伤害，合计最高 −75%）：
 *       只对 {@code ssc_addon:spell_damage} 伤害类型生效；减伤结算在
 *       {@code SscAddonLivingEntityMixin#ssca$spellResistanceReduce}（LivingEntity.damage HEAD，
 *       与绑定脚环/诅咒标记同一注入模式）。附魔书本身无效果，需经铁砧附到护甲上生效；
 *       多件护甲等级求和、上限 5 级。</li>
 * </ul>
 */
public final class RegAddonEnchantments {

	/** 法术抗性：每级 −15% 法术伤害。 */
	public static final Enchantment SPELL_RESISTANCE = new SpellResistanceEnchantment(
			Enchantment.Rarity.VERY_RARE, EnchantmentTarget.ARMOR,
			new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});

	private RegAddonEnchantments() {
	}

	public static void init() {
		Registry.register(Registries.ENCHANTMENT, new Identifier("ssc_addon", "spell_resistance"), SPELL_RESISTANCE);
	}
}
