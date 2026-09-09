package net.jackcooper.shapeShifterCurseAddon.spell.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonHelper;

/**
 * 单个法术的数值配置（jackcooper）。仿 Iron's Spellbooks 的「Java 行为类 + 外置数值」分离：
 * 本类只承载数值，行为（投射物/音效/粒子）仍在各 {@code Spell} 子类。
 *
 * <p>来源：{@code data/ssc_addon/spells/<id>.json}（数据包可覆盖）。任何字段缺失或非法时
 * 回退 {@link #fallback()} 的 Java 内置默认值——日志警告、不崩溃。</p>
 *
 * <p>字段一览（全部可选）：</p>
 * <ul>
 *   <li>{@code base_damage}（float，默认 0）：装书内基础伤害（满次数卷轴）；</li>
 *   <li>{@code base_cooldown_ticks}（int，默认 20）：基础冷却；</li>
 *   <li>{@code base_cast_time_ticks}（int，默认 0）：基础施法前摇，0=瞬发；</li>
 *   <li>{@code mana_cost}（int，默认 0）：每次施法耗书法力；</li>
 *   <li>{@code solo_damage_multiplier} / {@code solo_cooldown_multiplier} /
 *       {@code solo_cast_time_multiplier}（float，默认 0.5 / 2.0 / 2.0）：单独使用惩罚；</li>
 *   <li>{@code element}（string，默认 {@code null}）：系别标记（fire / ice），供法阵对立系判定；</li>
 *   <li>{@code levels}[5]（对象数组，可只写前几级，越界回退默认）：每级
 *       {@code damage_multiplier} / {@code cooldown_multiplier} / {@code speed_multiplier}
 *       （float，默认 1.0）与 {@code rarity}（string：white/green/blue/purple/orange）。</li>
 * </ul>
 */
public final class SpellConfig {

	public final float baseDamage;
	public final int baseCooldownTicks;
	public final int baseCastTimeTicks;
	public final int manaCost;
	public final float soloDamageMultiplier;
	public final float soloCooldownMultiplier;
	public final float soloCastTimeMultiplier;
	/** 系别标记（fire / ice / null）。null = 无系别（法阵对立系判定不生效）。 */
	public final String element;

	/** 每级倍率与品质（index = level-1；长度可不足 5，读取时越界回退默认）。 */
	public final float[] damageMultipliers;
	public final float[] cooldownMultipliers;
	public final float[] speedMultipliers;
	public final String[] rarities;

	private SpellConfig(float baseDamage, int baseCooldownTicks, int baseCastTimeTicks, int manaCost,
						float soloDamageMultiplier, float soloCooldownMultiplier, float soloCastTimeMultiplier,
						String element, float[] damageMultipliers, float[] cooldownMultipliers,
						float[] speedMultipliers, String[] rarities) {
		this.baseDamage = baseDamage;
		this.baseCooldownTicks = baseCooldownTicks;
		this.baseCastTimeTicks = baseCastTimeTicks;
		this.manaCost = manaCost;
		this.soloDamageMultiplier = soloDamageMultiplier;
		this.soloCooldownMultiplier = soloCooldownMultiplier;
		this.soloCastTimeMultiplier = soloCastTimeMultiplier;
		this.element = element;
		this.damageMultipliers = damageMultipliers;
		this.cooldownMultipliers = cooldownMultipliers;
		this.speedMultipliers = speedMultipliers;
		this.rarities = rarities;
	}

	/** 指定等级伤害倍率（level 1-5；越界/缺省回退 1.0）。 */
	public float damageMultiplier(int level) {
		return at(damageMultipliers, level, 1.0f);
	}

	/** 指定等级冷却倍率（level 1-5；越界/缺省回退 1.0）。 */
	public float cooldownMultiplier(int level) {
		return at(cooldownMultipliers, level, 1.0f);
	}

	/** 指定等级速度倍率（level 1-5；越界/缺省回退 1.0）。 */
	public float speedMultiplier(int level) {
		return at(speedMultipliers, level, 1.0f);
	}

	/** 指定等级品质 id（level 1-5；越界/缺省回退 null，由调用方再回退 Java 品质）。 */
	public String rarity(int level) {
		return at(rarities, level, null);
	}

	private static float at(float[] arr, int level, float def) {
		int i = level - 1;
		if (arr == null || i < 0 || i >= arr.length) {
			return def;
		}
		return arr[i];
	}

	private static <T> T at(T[] arr, int level, T def) {
		int i = level - 1;
		if (arr == null || i < 0 || i >= arr.length) {
			return def;
		}
		return arr[i];
	}

	/** Java 内置默认配置（JSON 缺失/损坏时的兜底全 0 数值 + 全 1.0 倍率）。 */
	public static SpellConfig fallback() {
		return new SpellConfig(0f, 20, 0, 0, 0.5f, 2.0f, 2.0f, null,
				new float[0], new float[0], new float[0], new String[0]);
	}

	/** 从 JSON 解析（缺字段回退默认；任何异常向上抛由调用方按损坏处理）。 */
	public static SpellConfig fromJson(JsonObject o) {
		float baseDamage = JsonHelper.getFloat(o, "base_damage", 0f);
		int baseCooldown = Math.max(0, JsonHelper.getInt(o, "base_cooldown_ticks", 20));
		int baseCastTime = Math.max(0, JsonHelper.getInt(o, "base_cast_time_ticks", 0));
		int manaCost = Math.max(0, JsonHelper.getInt(o, "mana_cost", 0));
		float soloDmg = positive(JsonHelper.getFloat(o, "solo_damage_multiplier", 0.5f));
		float soloCd = positive(JsonHelper.getFloat(o, "solo_cooldown_multiplier", 2.0f));
		float soloCast = positive(JsonHelper.getFloat(o, "solo_cast_time_multiplier", 2.0f));
		String element = o.has("element") && o.get("element").isJsonPrimitive()
				? normalizeElement(o.get("element").getAsString()) : null;

		float[] dmg = new float[0];
		float[] cd = new float[0];
		float[] speed = new float[0];
		String[] rarity = new String[0];
		if (o.has("levels") && o.get("levels").isJsonArray()) {
			JsonArray levels = o.getAsJsonArray("levels");
			int n = Math.min(levels.size(), 5);
			dmg = new float[n];
			cd = new float[n];
			speed = new float[n];
			rarity = new String[n];
			for (int i = 0; i < n; i++) {
				JsonElement el = levels.get(i);
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject lv = el.getAsJsonObject();
				dmg[i] = Math.max(0f, JsonHelper.getFloat(lv, "damage_multiplier", 1.0f));
				cd[i] = Math.max(0f, JsonHelper.getFloat(lv, "cooldown_multiplier", 1.0f));
				speed[i] = Math.max(0f, JsonHelper.getFloat(lv, "speed_multiplier", 1.0f));
				rarity[i] = lv.has("rarity") && lv.get("rarity").isJsonPrimitive()
						? normalizeRarity(lv.get("rarity").getAsString()) : null;
			}
		}
		return new SpellConfig(baseDamage, baseCooldown, baseCastTime, manaCost,
				soloDmg, soloCd, soloCast, element, dmg, cd, speed, rarity);
	}

	/** element 只认 fire / ice，其它归 null（与 FormationElement 枚举对齐）。 */
	private static String normalizeElement(String s) {
		return "fire".equals(s) || "ice".equals(s) ? s : null;
	}

	/** rarity 只认五个合法品质 id，其它归 null（回退 Java 侧品质）。 */
	private static String normalizeRarity(String s) {
		return switch (s == null ? "" : s) {
			case "white", "green", "blue", "purple", "orange" -> s;
			default -> null;
		};
	}

	private static float positive(float v) {
		return v <= 0f ? 0.01f : v;
	}
}
