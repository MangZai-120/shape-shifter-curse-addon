package net.jackcooper.shapeShifterCurseAddon.mixin.plugin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 附属包 Mixin 条件加载 / 冲突自动让路插件（对应"mixin 冲突自动兼容"策略的 L3 层）。
 *
 * <p>提供两套「按 mod 是否加载决定要不要应用某个 mixin」的机制：</p>
 * <ul>
 *   <li><b>条件加载 {@link #REQUIRED_MODS}</b>：某 mixin 依赖的可选 mod 没装时，直接跳过该 mixin，
 *       避免因目标类缺失而崩溃（等价于原版 SSC 的 requiredMods 机制）。</li>
 *   <li><b>自动让路 {@link #CONFLICT_MODS}</b>：检测到某个「已知会和我们硬冲突」的 mod 时，
 *       主动跳过自己的 mixin 让对方生效，避免两边抢同一注入点互相破坏。</li>
 * </ul>
 *
 * <p><b>使用边界（重要）</b>：让路会让附属放弃这条 mixin 的功能，只用于「确实无法共存的硬冲突」。
 * 大多数情况应优先用 MixinExtras 的可组合注解（{@code @WrapOperation}/{@code @ModifyExpressionValue}）
 * 与其它 mod <b>共存</b>，而不是让路——附属现有 mixin 已普遍这么做，故本表初始为空。</p>
 *
 * <p><b>IronsSpellbooksAnimationMixin 的处理</b>：已登记条件加载（modId {@code irons_spellbooks}，
 * 据其 jar 内 {@code META-INF/mods.toml} 权威确认）——未装 Iron's Spellbooks 时更早、更明确地跳过。
 * 它同时保留 {@code @Pseudo} + 字符串 target（编译期附属无此 Forge 类，必须如此才能编译）：
 * {@code @Pseudo} 负责编译期、本插件的 mod 检测负责运行期，二者互补。Iron's Spellbooks 是 Forge mod，
 * 经 Sinytra Connector 加载时 Connector 会保留其 Forge modId，故 {@code isModLoaded("irons_spellbooks")} 可正确检测。</p>
 *
 * <p>未登记的 mixin 一律默认应用（{@link #shouldApplyMixin} 返回 {@code true}），
 * 因此本插件对现有全部 mixin 的行为零影响，只是预留了随时可用的扩展点。</p>
 */
public class SscAddonMixinConfigPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("ssc-addon-mixin");

    /** 附属 mixin 包前缀，配合下方 helper 用简短类名登记，省去每次写全限定名。 */
    private static final String MIXIN_PREFIX = "net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.";

    /** mixin 全限定类名 → 必需的 mod id 列表（列表内全部加载，该 mixin 才应用；缺任一即跳过）。 */
    private static final Map<String, String[]> REQUIRED_MODS = new HashMap<>();

    /** mixin 全限定类名 → 冲突的 mod id 列表（列表内任一加载，就自动让路跳过该 mixin）。 */
    private static final Map<String, String[]> CONFLICT_MODS = new HashMap<>();

    static {
        // Iron's Spellbooks（Forge mod，经 Sinytra Connector 加载；modId 已据其 jar 内
        // META-INF/mods.toml 确认为 "irons_spellbooks"）未装时，跳过施法动画拦截 mixin。
        // 该 mixin 仍保留 @Pseudo + 字符串 target（编译期附属无此 Forge 类，必须如此才能编译）；
        // 此处 mod 检测是运行期额外条件，未装时更早、更明确地跳过。
        requireMod("IronsSpellbooksAnimationMixin", "irons_spellbooks");
        // Iris（光影加载器）未装时跳过恐惧失明雾的内存补丁 mixin（目标类 ProgramSource 不存在会崩）。
        // 该 mixin 在 Iris 载入光影时把 DoBlindnessFog 内存替换为粉色 12→16 格版（不改光影文件）。
        requireMod("IrisShaderPackMixin", "iris");

        // ========== 扩展示例（需要时取消注释并按需修改）==========
        // ① 某 mixin 只在装了某可选 mod 时才应用（缺 mod 就跳过，防崩）：
        //    requireMod("client.SomeOptionalModMixin", "some_optional_mod");
        // ② 某 mixin 撞到某已知冲突 mod 时自动让路（放弃自己这条注入，交给对方）：
        //    yieldOnConflict("player.SscPlayerMixin", "some_conflicting_combat_mod");
        // 简短类名 = my_addon.mixins.json 里 "mixins"/"client" 数组的条目原样填入。
    }

    /** 登记「必需 mod」条件加载规则。 */
    @SuppressWarnings("unused")
    private static void requireMod(String simpleMixinName, String... mods) {
        REQUIRED_MODS.put(MIXIN_PREFIX + simpleMixinName, mods);
    }

    /** 登记「撞到冲突 mod 自动让路」规则。 */
    @SuppressWarnings("unused")
    private static void yieldOnConflict(String simpleMixinName, String... mods) {
        CONFLICT_MODS.put(MIXIN_PREFIX + simpleMixinName, mods);
    }

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("SscAddonMixinConfigPlugin loaded for package {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null; // 沿用 mixins.json 里声明的默认 refmap，不覆盖
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 条件加载：缺任一必需 mod → 跳过
        String[] required = REQUIRED_MODS.get(mixinClassName);
        if (required != null) {
            for (String mod : required) {
                if (!FabricLoader.getInstance().isModLoaded(mod)) {
                    LOGGER.info("[required-mod] {} not loaded, skipping mixin {}", mod, mixinClassName);
                    return false;
                }
            }
        }
        // 自动让路：撞到任一冲突 mod → 跳过
        String[] conflicts = CONFLICT_MODS.get(mixinClassName);
        if (conflicts != null) {
            for (String mod : conflicts) {
                if (FabricLoader.getInstance().isModLoaded(mod)) {
                    LOGGER.info("[conflict-yield] conflicting mod {} detected, skipping mixin {}", mod, mixinClassName);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无需处理
    }

    @Override
    public List<String> getMixins() {
        return null; // 不动态追加 mixin，沿用 mixins.json 静态列表
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }
}
