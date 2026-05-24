package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 月髓环（SpUpgradeItem）成功变形后，按目标形态发放对应的子成就。
 * <p>
 * 用法：
 * <pre>{@code
 * register("familiar_fox_sp", "moon_marrow_to_familiar_fox_sp");
 * }</pre>
 * 参数 1 = 目标形态在 my_addon 命名空间下的 path（如 familiar_fox_sp）；
 * 参数 2 = 对应成就 JSON 在 data/ssc_addon/advancements/ssc_addon/ 下的文件名（不含 .json）。
 * <p>
 * 添加新成就的 3 步：
 * 1) 新建 JSON：data/ssc_addon/advancements/&lt;advPath&gt;.json，
 *    其中 parent 指向 "ssc_addon:tonight_moon_beautiful"，trigger 用 "minecraft:impossible"。
 * 2) 在 zh_cn.json / en_us.json 追加 advancements.ssc_addon.&lt;advPath&gt;.title 与 .description。
 * 3) 在本文件 static 块里加一行 register(...)。
 * <p>
 * 若未注册或对应成就 JSON 不存在，{@link AdvancementUtils#grant} 会静默忽略，不报错。
 */
public final class MoonMarrowFormAdvancements {

	private static final Map<Identifier, Identifier> MAP = new HashMap<>();

	static {
		// TODO：按需为每个可由月髓环变身的形态注册对应成就，例如：
		// register("familiar_fox_sp",  "moon_marrow_to_familiar_fox_sp");
		// register("axolotl_sp",       "moon_marrow_to_axolotl_sp");
		// register("fallen_allay_sp",  "moon_marrow_to_fallen_allay_sp");
		// register("anubis_wolf_sp",   "moon_marrow_to_anubis_wolf_sp");
	}

	private MoonMarrowFormAdvancements() {}

	/**
	 * 注册「目标形态 → 子成就」映射。
	 * @param formPath 目标形态在 my_addon 下的 path
	 * @param advPath  成就在 ssc_addon/ 下的文件名（不含 .json）
	 */
	public static void register(String formPath, String advPath) {
		MAP.put(new Identifier("my_addon", formPath),
				new Identifier("ssc_addon", advPath));
	}

	/**
	 * 根据目标形态 ID 查询对应子成就 Identifier，未注册时返回 null。
	 */
	@Nullable
	public static Identifier get(Identifier formId) {
		return MAP.get(formId);
	}
}
