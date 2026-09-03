package net.jackcooper.shapeShifterCurseAddon.resource;

import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

/**
 * 全部资源条的注册表与句柄（jackcooper）。
 *
 * <p>现有五条 apoli resource 型资源在此声明（id 沿用，底层存储不变）；
 * 原版使魔/蜘蛛 mana（ManaComponent 体系）以 {@link #VANILLA_MANA} 直通适配，
 * 不包装假抽象。新形态接入：加一行 Def + 需要时挂规则对象。
 */
public final class BarKeys {

	private BarKeys() {}

	// ==================== 现有 resource 型资源条 ====================

	/** SP 悦灵 mana（max 200，信标增幅回复）。 */
	public static final ResourceBarDef ALLAY_MANA = new ResourceBarDef(
			new Identifier("my_addon", "form_allay_sp_mana_resource"), "mana", 200, FormIdentifiers.ALLAY_SP);

	/** 吸血蝙蝠血条（max 100，战斗攒/脱战衰减/三段效果）。 */
	public static final ResourceBarDef BAT_BLOOD = new ResourceBarDef(
			new Identifier("my_addon", "form_bat_desmodus_blood_resource"), "blood", 100, FormIdentifiers.BAT_DESMODUS);

	/** SP 阿努比斯灵魂能量（max 100，累积制满清零）。 */
	public static final ResourceBarDef ANUBIS_SOUL = new ResourceBarDef(
			new Identifier("my_addon", "form_anubis_wolf_sp_soul_energy"), "energy", 100, FormIdentifiers.ANUBIS_WOLF_SP);

	/** SP 雪狐寒霜能量（max 100，技能扣费制）。 */
	public static final ResourceBarDef SNOW_FOX = new ResourceBarDef(
			new Identifier("my_addon", "form_snow_fox_sp_resource"), "mana", 100, FormIdentifiers.SNOW_FOX_SP);

	/** 寄生果蝠种子能量（10 格点条）。 */
	public static final ResourceBarDef SEED = new ResourceBarDef(
			new Identifier("my_addon", "form_bat_parasitic_fruit_seed_energy"), "seed", 10, FormIdentifiers.BAT_PARASITIC_FRUIT);

	// ==================== 原版体系直通适配 ====================

	/**
	 * 原版 mana（使魔/蜘蛛系 ManaComponent）的适配句柄。
	 * kind 同为 "mana"；id 用占位标识（真实读写走 ManaUtils 直通，见 ResourceBars）。
	 */
	public static final ResourceBarDef VANILLA_MANA = new ResourceBarDef(
			new Identifier("ssc_addon", "vanilla_mana_adapter"), "mana", 100);

	/** 全部条（调度器遍历用；VANILLA_MANA 不参与 regen 调度——原版自带 tick）。 */
	public static final ResourceBarDef[] ALL = {
			ALLAY_MANA, BAT_BLOOD, ANUBIS_SOUL, SNOW_FOX, SEED,
	};
}
