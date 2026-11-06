package net.jackcooper.shapeShifterCurseAddon.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/** Optional dependency and mutually exclusive bridge policy shared by the Mixin plugin and regressions. */
public final class MixinSelection {
    private MixinSelection() {}
    private static final Logger LOGGER = LoggerFactory.getLogger("ssc-addon-mixin");

    /** 附属 mixin 包前缀，配合下方 helper 用简短类名登记，省去每次写全限定名。 */
    private static final String MIXIN_PREFIX = "net.jackcooper.shapeShifterCurseAddon.mixin.";

    /** mixin 全限定类名 → 必需的 mod id 列表（列表内全部加载，该 mixin 才应用；缺任一即跳过）。 */
    private static final Map<String, String[]> REQUIRED_MODS = new HashMap<>();

    /** mixin 全限定类名 → 冲突的 mod id 列表（列表内任一加载，就自动让路跳过该 mixin）。 */
    private static final Map<String, String[]> CONFLICT_MODS = new HashMap<>();

    /** 与主包 TrinketImpl 互斥的桥接 mixin 全限定名（主包那个被跳过时这个才应用）。 */
    private static final String TRINKET_BRIDGE_MIXIN = MIXIN_PREFIX + "item.AddonTrinketBridgeMixin";

    static {
        // Iron's Spellbooks（Forge mod，经 Sinytra Connector 加载；modId 已据其 jar 内
        // META-INF/mods.toml 确认为 "irons_spellbooks"）未装时，跳过施法动画拦截 mixin。
        // 该 mixin 仍保留 @Pseudo + 字符串 target（编译期附属无此 Forge 类，必须如此编译）；
        // 此处 mod 检测是运行期额外条件，未装时更早、更明确地跳过。
        requireMod("IronsSpellbooksAnimationMixin", "irons_spellbooks");
        // Iris（光影加载器）未装时跳过恐惧失明雾的内存补丁 mixin（目标类 ProgramSource 不存在会崩）。
        // 该 mixin 在 Iris 载入光影时把 DoBlindnessFog 内存替换为粉色 12→16 格版（不改光影文件）。
        requireMod("client.IrisShaderPackMixin", "iris");
        requireMod("client.AsyncParticleSyncBatchMixin", "asyncparticles");
        requireMod("client.AsyncParticleGpuEligibilityMixin", "asyncparticles");
        requireMod("client.CarryOnAnimationMixin", "carryon");
        // 饰品 Trinkets 桥接兜底（AddonTrinketBridgeMixin）：仅当 trinkets API 可用
        // （原生 Trinkets 或 tclayer 兼容层 provides "trinkets"）时应用；
        // 与主包 TrinketImpl 的互斥见 shouldApplyMixin 内专门判定（最高优先级插件非 trinkets 才应用）。
        requireMod("item.AddonTrinketBridgeMixin", "trinkets");

        // ========== 扩展示例（需要时取消注释并按需修改）==========
        // ① 某 mixin 只在装了某可选 mod 时才应用（缺 mod 就跳过，防崩）：
        //    requireMod("client.SomeOptionalModMixin", "some_optional_mod");
        // ② 某 mixin 撞到某已知冲突 mod 时自动让路（放弃自己这条注入，交给对方）：
        //    yieldOnConflict("player.SscPlayerMixin", "some_conflicting_combat_mod");
        // 简短类名 = my_addon.mixins.json 里 "mixins"/"client" 数组的条目原样填入。
    }

    /** 登记「必需 mod」条件加载规则。（已被 static 块调用，无需 unused 抑制） */
    private static void requireMod(String simpleMixinName, String... mods) {
        REQUIRED_MODS.put(MIXIN_PREFIX + simpleMixinName, mods);
    }

    /** 登记「撞到冲突 mod 自动让路」规则。 */
    @SuppressWarnings("unused")
    private static void yieldOnConflict(String simpleMixinName, String... mods) {
        CONFLICT_MODS.put(MIXIN_PREFIX + simpleMixinName, mods);
    }

    public static boolean select(String mixinClassName, java.util.function.Predicate<String> modLoaded,
                               java.util.function.Supplier<String> highestBackend) {
        // 饰品桥接兜底：与主包 TrinketImpl 严格互斥——
        // 主包 plugin 在「最高优先级插件 == trinkets」时应用 TrinketImpl 并跳过我们；
        // 反之（如 Kilt 转载的 curios 2000 抢占）主包跳过 TrinketImpl，此时我们才应用。
        // 两边判定条件互补，保证同一目标类 AccessoryItem 永远只有一份 Trinket 接口实现。
        // 条件加载：缺任一必需 mod → 跳过
        String[] required = REQUIRED_MODS.get(mixinClassName);
        if (required != null) {
            for (String mod : required) {
                if (!modLoaded.test(mod)) {
                    LOGGER.info("[required-mod] {} not loaded, skipping mixin {}", mod, mixinClassName);
                    return false;
                }
            }
        }
        if (TRINKET_BRIDGE_MIXIN.equals(mixinClassName)) {
            String highest = highestBackend.get();
            if ("trinkets".equals(highest)) {
                LOGGER.info("[trinket-bridge] main SSC TrinketImpl active (highest=trinkets), skipping bridge mixin");
                return false;
            }
            LOGGER.info("[trinket-bridge] main SSC TrinketImpl skipped (highest={}), applying bridge mixin", highest);
            return true;
        }
        // 自动让路：撞到任一冲突 mod → 跳过
        String[] conflicts = CONFLICT_MODS.get(mixinClassName);
        if (conflicts != null) {
            for (String mod : conflicts) {
                if (modLoaded.test(mod)) {
                    LOGGER.info("[conflict-yield] conflicting mod {} detected, skipping mixin {}", mod, mixinClassName);
                    return false;
                }
            }
        }
        return true;
    }

}
