package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostSpikeSpell;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魔法注册表（jackcooper）：id → {@link Spell}。在 {@code SscAddon.onInitialize} 里调用 {@link #init()} 填充。
 * 用 {@link LinkedHashMap} 保持注册顺序（供 REI/JEI 或书内展示按序）。
 */
public final class SpellRegistry {
	private static final Map<Identifier, Spell> SPELLS = new LinkedHashMap<>();

	private SpellRegistry() {
	}

	public static void register(Spell spell) {
		SPELLS.put(spell.getId(), spell);
	}

	public static Spell get(Identifier id) {
		return id == null ? null : SPELLS.get(id);
	}

	/** 按 path 取魔法（命名空间恒 {@code ssc_addon}）。 */
	public static Spell get(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		return SPELLS.get(new Identifier("ssc_addon", path));
	}

	public static Collection<Spell> all() {
		return SPELLS.values();
	}

	/** 注册所有内置魔法。 */
	public static void init() {
		register(new FrostSpikeSpell());
	}
}
