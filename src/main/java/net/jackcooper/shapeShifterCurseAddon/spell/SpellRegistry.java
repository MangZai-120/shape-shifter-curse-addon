package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostSpikeSpell;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

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
 * <p>所有已注册法术先完整解析成候选表；缺文件或无效耗能使本次重载失败。
 * 全局重载成功后才提交，客户端也先校验整张表再替换镜像。</p>
 *
 * <p>用 {@link LinkedHashMap} 保持注册顺序（供 REI/JEI 或书内展示按序）。</p>
 */
public final class SpellRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Map<Identifier, Spell> SPELLS = new LinkedHashMap<>();
	/** 原始 JSON 文本镜像（id path → json），供 S2C 同步给客机。 */
	private volatile Map<String, String> rawJson = Map.of();
    private volatile Map<String, SpellConfig> serverConfigs = Map.of();
    private volatile Map<String, SpellConfig> clientConfigs = Map.of();
    private Map<String, String> pendingRaw;
    private Map<String, SpellConfig> pendingConfigs;

    public SpellConfig configFor(String path, SpellConfig fallback) {
        var configs = net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isClientThread()
                ? clientConfigs : serverConfigs;
        return configs.getOrDefault(path, fallback);
    }
    public synchronized void commitPending() {
        if (pendingConfigs == null) return;
        rawJson = pendingRaw;
        serverConfigs = pendingConfigs;
        discardPending();
    }
    public synchronized void discardPending() { pendingConfigs = null; pendingRaw = null; }
    public synchronized void clearServer() { discardPending(); serverConfigs = Map.of(); rawJson = Map.of(); }
    public void clearClient() { clientConfigs = Map.of(); }
    public void sendTo(net.minecraft.server.network.ServerPlayerEntity player) {
        var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        var raw = getRawJson();
        out.writeInt(raw.size());
        raw.forEach((id, json) -> { out.writeString(id, 256); out.writeString(json, 2000000); });
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, out);
    }

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

	/** 注册所有内置魔法行为类（数值等待 JSON 注入）。
	 *  按系别聚簇注册（与 {@link FormationElement} 枚举序一致：火→冰→月辉→诅咒→召唤→虚无→空间），
	 *  创造页「法术」页内同系卷轴紧挨展示；LinkedHashMap 保序（见类注释）。 */
	public static void init() {
		// —— 火系（对立冰）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FireBoltSpell());       // 火球术
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FlameNovaSpell());     // 烈焰新星
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.MeteorSpell());        // 陨火术
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.ExplosionSpell());      // 爆裂魔法（红色单级）
		// —— 冰系（对立火）——
		register(new FrostSpikeSpell());                                                        // 冰锥
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.IceBarrageSpell());    // 冰锥齐射
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostNovaSpell());     // 冰霜新星
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostArmorSpell());    // 霜甲术
		// —— 月辉系（对立诅咒）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.MoonlightArrowSpell());  // 月光箭
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarMendSpell());       // 月华治愈
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarVeilSpell());       // 月幕
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarPhaseSpell());      // 月相（按住描边锁定，松手百分比换血）
		// —— 诅咒系（对立月辉）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CurseMarkSpell());       // 诅咒标记
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.DreadWhisperSpell());    // 恐惧低语
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CorruptMistSpell());     // 腐蚀之雾
		// —— 召唤系（对立虚无）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SummonLunarSpiritSpell()); // 召唤月灵
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.CompanionResonanceSpell()); // 伙伴共鸣
		// —— 虚无系（对立召唤）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.VoidDevourSpell());      // 虚空吞噬
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.VoidErosionSpell());     // 虚空侵蚀
		// —— 空间系（独立，位移探索向）——
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceBlinkSpell());      // 空间跳跃
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceStrideSpell());     // 空间漫步
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceRecallSpell());     // 空间归途
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.PocketSpaceSpell());     // 随身空间
		register(new net.jackcooper.shapeShifterCurseAddon.spell.spells.DomainSpell());
	}

	// ---- datapack reload（服务端 / 单人；客机走 applyClientSync 镜像）----

	@Override
	public Identifier getFabricId() {
		return LISTENER_ID;
	}

    @Override
    public synchronized void reload(ResourceManager manager) {
        discardPending();
        Map<String, String> loaded = new LinkedHashMap<>();
        Map<String, SpellConfig> configs = new LinkedHashMap<>();
        for (var entry : manager.findResources(DIR, id -> id.getPath().endsWith(".json")).entrySet()) {
            var id = entry.getKey();
            if (!id.getNamespace().equals("ssc_addon")) continue;
            String path = id.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            if (name.startsWith("_") || get(name) == null) continue;
            try (var input = entry.getValue().getInputStream()) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                configs.put(name, parseConfig(json, name));
                loaded.put(name, json);
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Invalid spell resource " + id, invalid);
            }
        }
        for (Spell spell : SPELLS.values()) {
            if (!configs.containsKey(spell.getId().getPath()))
                throw new IllegalArgumentException("Missing spell config: " + spell.getId());
        }
        pendingRaw = Map.copyOf(loaded);
        pendingConfigs = Map.copyOf(configs);
    }

	/** 解析并注入配置（服务端 reload 与客机 S2C 镜像共用，保证两端解析一致）。 */
    private static SpellConfig parseConfig(String json, String path) {
        SpellConfig config = SpellConfig.fromJson(JsonHelper.deserialize(json));
        if (!config.manaCostConfigured) throw new IllegalArgumentException("Missing/invalid mana_cost: " + path);
        return config;
    }

	/** 客户端收到 S2C 同步后重建配置镜像（多人环境下客户端无 datapack 数据）。 */
    public void applyClientSync(Map<String, String> raw) {
        Map<String, SpellConfig> configs = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            if (get(entry.getKey()) == null) throw new IllegalArgumentException("Unknown server spell " + entry.getKey());
            configs.put(entry.getKey(), parseConfig(entry.getValue(), entry.getKey()));
        }
        for (Identifier id : SPELLS.keySet()) {
            if (!configs.containsKey(id.getPath()))
                throw new IllegalArgumentException("Missing server spell " + id);
        }
        clientConfigs = Map.copyOf(configs);
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

