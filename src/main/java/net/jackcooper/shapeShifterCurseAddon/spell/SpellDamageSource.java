package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * 法术伤害源（jackcooper）：SSCA 魔法体系专用独立伤害类型 {@code ssc_addon:spell_damage}。
 *
 * <p>用途：把 9 处法术伤害调用点从原版「间接魔法 indirectMagic」迁移到独立类型，实现：</p>
 * <ul>
 *   <li><b>法术抗性附魔</b>（spell_resistance，每级 −15%）能精确识别并只减免法术伤害，
 *       不误伤女巫药水等其它魔法伤害；</li>
 *   <li>死亡消息独立（death.attack.ssc_addon.spell_damage）。</li>
 * </ul>
 *
 * <p>tag 对齐原版 magic 行为（data/minecraft/tags/damage_type/ 下 replace:false 追加本类型）：
 * <b>is_magic</b>（吃抗性/药水等 magic 类判定）与 <b>witch_resistant_to</b>（保持女巫对法术的
 * 原有抗性）。伤害类型本体 effects=hurt（红字受击）、bypasses_armor 不追加——法术伤害
 * 与原版 magic 一致<b>不无视护甲</b>（原版 magic 也不在 bypasses_armor 内，走魔咒减伤通道）。</p>
 *
 * <p>用法（与原 indirectMagic(owner, owner) 语义一一对应）：</p>
 * <ul>
 *   <li>有施法者：{@code SpellDamageSource.of(world.getDamageSources(), owner)} —— attacker/source 均为施法者；</li>
 *   <li>月灵光弹等投射物：{@code SpellDamageSource.of(ds, bolt, ownerPlayer)} —— source=弹体（供
 *       LunarSpiritTargetLink 区分「玩家亲手攻击」），attacker=主人（击杀/经验归玩家）；</li>
 *   <li>无施法者兜底：{@code SpellDamageSource.of(ds)}。</li>
 * </ul>
 */
public final class SpellDamageSource {

	/** 法术伤害类型 key（data/ssc_addon/damage_type/spell_damage.json）。 */
	public static final RegistryKey<DamageType> SPELL_DAMAGE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("ssc_addon", "spell_damage"));

	private SpellDamageSource() {
	}

	/** 无归因（原 magic() 兜底位）。 */
	public static DamageSource of(net.minecraft.entity.damage.DamageSources ds) {
		return ds.create(SPELL_DAMAGE);
	}

	/** 施法者即来源（原 indirectMagic(owner, owner) 位）。 */
	public static DamageSource of(net.minecraft.entity.damage.DamageSources ds, Entity attacker) {
		return ds.create(SPELL_DAMAGE, attacker, attacker);
	}

	/** 来源与归因分离（投射物法术：source=弹体、attacker=玩家主人）。 */
	public static DamageSource of(net.minecraft.entity.damage.DamageSources ds, Entity source, Entity attacker) {
		return ds.create(SPELL_DAMAGE, source, attacker);
	}
}
