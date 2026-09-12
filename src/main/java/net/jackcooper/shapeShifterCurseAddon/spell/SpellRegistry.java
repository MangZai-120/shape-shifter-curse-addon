package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostSpikeSpell;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魔法注册表（jackcooper）：id → {@link Spell}。仿 Iron's Spellbooks 的注册架构——
 * <b>Java 侧只注册行为类（单例），数值由 JSON 数据包注入</b>（{@code data/ssc_addon/spells/<path>.json}）。
 *
 * <p>加载链路：</p>
 * <ol>
 *   <li>{@code SscAddon.onInitialize} 调 {@link #init()} 注册所有内置行为类（此时数值为 fallback）；</li>
 *   <li>服务端 datapack reload（{@link #reload}）扫描 JSON 并逐法术注入配置；</li>
 *   <li>多人环境：客机加入时服务端 S2C 推送原始 JSON（{@link #getRawJson()}），
 *       客户端 {@link #applyClientSync} 重建镜像——客机自己的 resources 里没有服务器的数据包。</li>
 * </ol>
 *
 * <p>容错（回退默认值）：JSON 缺文件 → 该法术维持 fallback 数值（打日志）；缺字段 → 字段级回退
 * （见 {@link SpellConfig#fromJson}）；JSON 指向未注册法术 → 忽略并警告。任何情况都不崩溃。</p>
 *
 * <p>用 {@link LinkedHashMap} 保持注册顺序（供 REI/JEI 或书内展示按序）。</p>
 */
public final class SpellRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Map<Identifier, Spell> SPELLS = new LinkedHashMap<>();
	/** 原始 JSON 文本镜像（id path → json），供 S2C 同步给客机。 */
	private final Map<String, String> rawJson = new LinkedHashMap<>();

	public static final SpellRegistry INSTANCE = new SpellRegistry();
	private static final Identifier LISTENER_ID = new Identifier("ssc_addon", "spell_configs");
	private static final String DIR = "spells";

	private SpellRegistry() {
	}

	// ---- 行为类注册（mod 初始化期，一次性）----

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

	/** 注册所有内置魔法行为类（数值等待 JSON 注入）。 */
	public static void init() {
		register(new FrostSpikeSpell());
		// —— 2026-09 新增六法术（火系三 + 冰系三，jackcooper）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FireBoltSpell());       // 火球术
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FlameNovaSpell());     // 烈焰新星
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.MeteorSpell());        // 陨火术
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.IceBarrageSpell());    // 冰锥齐射
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostNovaSpell());     // 冰霜新星
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostArmorSpell());    // 霜甲术
		// —— 2026-09 新增系别：月辉 × 诅咒（对立对，jackcooper）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.MoonlightArrowSpell());  // 月光箭
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarMendSpell());       // 月华治愈
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarVeilSpell());       // 月幕
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CurseMarkSpell());       // 诅咒标记
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.DreadWhisperSpell());    // 恐惧低语
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell());     // 腐蚀之雾
		// —— 2026-09 新增系别：召唤 × 虚无（对立对）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SummonLunarSpiritSpell()); // 召唤月灵
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CompanionResonanceSpell()); // 伙伴共鸣
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.VoidDevourSpell());      // 虚空吞噬
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.VoidErosionSpell());     // 虚空侵蚀
		// —— 2026-09 新增系别：空间（独立，位移探索向）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceBlinkSpell());      // 空间跳跃
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceStrideSpell());     // 空间漫步
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceRecallSpell());     // 空间归途
	}

	// ---- datapack reload（服务端 / 单人；客机走 applyClientSync 镜像）----

	@Override
	public Identifier getFabricId() {
		return LISTENER_ID;
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<String, String> loaded = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> entry :
				manager.findResources(DIR, path -> path.getPath().endsWith(".json")).entrySet()) {
			Identifier fileId = entry.getKey();
			// 只认自己命名空间的 data/ssc_addon/spells/*.json（数据包覆盖同路径时资源管理器已按优先级取胜者）
			if (!"ssc_addon".equals(fileId.getNamespace())) {
				continue;
			}
			String path = fileId.getPath();
			String fileName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
			if (fileName.startsWith("_")) {
				continue; // 跳过下划线开头的样例/注释文件
			}
			try (InputStream is = entry.getValue().getInputStream()) {
				String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
				Spell spell = get(fileName);
				if (spell == null) {
					System.err.println("[ssc_addon] Spell config skipped (no such spell registered): " + fileName);
					continue;
				}
				applyConfig(spell, content, fileName);
				loaded.put(fileName, content);
			} catch (Exception e) {
				System.err.println("[ssc_addon] Failed to load spell config: " + fileId + " - " + e);
			}
		}
		synchronized (this) {
			rawJson.clear();
			rawJson.putAll(loaded);
		}
		// 提示未获得 JSON 的已注册法术（维持 fallback，方便排查漏文件）
		for (Spell spell : SPELLS.values()) {
			if (!loaded.containsKey(spell.getId().getPath())) {
				System.err.println("[ssc_addon] Spell '" + spell.getId().getPath()
						+ "' has no JSON config (data/ssc_addon/spells/" + spell.getId().getPath()
						+ ".json); using fallback values.");
			}
		}
	}

	/** 解析并注入配置（服务端 reload 与客机 S2C 镜像共用，保证两端解析一致）。 */
	private static void applyConfig(Spell spell, String json, String path) {
		JsonObject o = JsonHelper.deserialize(json);
		SpellConfig config = SpellConfig.fromJson(o);
		((SpellConfigInjector) spell).ssc_addon$applyConfig(config);
	}

	/** 客户端收到 S2C 同步后重建配置镜像（多人环境下客户端无 datapack 数据）。 */
	public void applyClientSync(Map<String, String> raw) {
		for (Map.Entry<String, String> e : raw.entrySet()) {
			Spell spell = get(e.getKey());
			if (spell == null) {
				continue;
			}
			try {
				applyConfig(spell, e.getValue(), e.getKey());
			} catch (Exception ex) {
				System.err.println("[ssc_addon] Failed to apply synced spell config: " + e.getKey() + " - " + ex);
			}
		}
	}

	/** 各法术的原始 JSON 文本（用于 S2C 同步转发）。 */
	public Map<String, String> getRawJson() {
		synchronized (this) {
			return Collections.unmodifiableMap(new LinkedHashMap<>(rawJson));
		}
	}

	/** 配置注入桥：{@link Spell} 基类的包级注入方法由此接口转 public 调用。 */
	public interface SpellConfigInjector {
		void ssc_addon$applyConfig(SpellConfig config);
	}
}

