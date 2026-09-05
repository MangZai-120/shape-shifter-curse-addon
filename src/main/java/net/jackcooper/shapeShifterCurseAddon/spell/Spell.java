package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 魔法（法术）抽象基类（jackcooper）。每个具体魔法定义基础数值与释放效果。
 *
 * <p>数值语义（均为「装入魔法书且卷轴满次数」时的基准值）：</p>
 * <ul>
 *   <li>基础伤害 / 冷却 / 施法时间 / 法力消耗；</li>
 *   <li><b>装书内</b>：伤害 ×耐久比、冷却与施法时间 ×(2−耐久比)（耐久比 = 卷轴剩余次数/上限，红色恒 1）；</li>
 *   <li><b>单独使用</b>：固定惩罚（伤害 ×{@link #getSoloDamageMultiplier}、冷却/施法时间 ×对应倍率），每次消耗 1 次数。</li>
 * </ul>
 * <p>缩放由调用方（服务端施法逻辑）统一计算后把最终威力传入 {@link #cast}，本类只负责发出效果。</p>
 */
public abstract class Spell {
	private final Identifier id;
	private final SpellRarity rarity;

	protected Spell(Identifier id, SpellRarity rarity) {
		this.id = id;
		this.rarity = rarity;
	}

	public Identifier getId() {
		return id;
	}

	public SpellRarity getRarity() {
		return rarity;
	}

	/** 基础伤害（装书内、卷轴满次数时）。无伤害类魔法可返回 0。 */
	public abstract float getBaseDamage();

	/** 基础冷却（tick）。 */
	public abstract int getBaseCooldownTicks();

	/** 基础施法时间（tick），0 = 无前摇瞬发。 */
	public abstract int getBaseCastTimeTicks();

	/** 每次施法消耗的魔法书法力。 */
	public abstract int getManaCost();

	/** 单独使用时的伤害倍率（默认 0.5）。 */
	public float getSoloDamageMultiplier() {
		return 0.5f;
	}

	/** 单独使用时的冷却倍率（默认 2.0）。 */
	public float getSoloCooldownMultiplier() {
		return 2.0f;
	}

	/** 单独使用时的施法时间倍率（默认 2.0）。 */
	public float getSoloCastTimeMultiplier() {
		return 2.0f;
	}

	/**
	 * 释放魔法（服务端权威）。伤害/范围等已由调用方按耐久与单独/装书惩罚算好，通过 {@code power} 传入。
	 *
	 * @param caster 施法者（已确认装备魔法书或持有卷轴）
	 * @param power  最终威力（多为最终伤害值）
	 * @param solo   是否为单独使用卷轴（部分魔法可据此微调表现，一般无需区分）
	 */
	public abstract void cast(ServerPlayerEntity caster, float power, boolean solo);

	/** 魔法名 lang key。 */
	public String getNameKey() {
		return "spell.ssc_addon." + id.getPath() + ".name";
	}

	/** 魔法描述 lang key。 */
	public String getDescKey() {
		return "spell.ssc_addon." + id.getPath() + ".description";
	}

	/**
	 * 魔法图标贴图路径（16×16 源图，GUI 内可按需最近邻放大到任意尺寸，保持像素风）。
	 * 默认 {@code textures/gui/spell_icons/<id path>.png}；子类可覆写自定义路径。
	 * 返回 null 表示无专用图标（HUD 回落到绘制卷轴物品本身）。
	 */
	public Identifier getIconTexture() {
		return new Identifier("ssc_addon", "textures/gui/spell_icons/" + id.getPath() + ".png");
	}
}
