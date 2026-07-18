package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.nbt.NbtCompound;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

/**
 * 形态组件 NBT 膨胀 / 同步包超限 —— 治本 + 兜底防护。
 *
 * <p><b>特别感谢</b>：本 mixin 的治本思路（{@code readFromNbt} 读 formHistory 前先 clear）
 * 来自 <b>wuhenqiubai</b> 的 Shape-Shifter-Curse 非官方移植版提交
 * <a href="https://github.com/wuhenqiubai/Shape-Shifter-Curse_Unofficial-Port/commit/4cc011e67f156ed2d735a8b2fe5a7d1d71d31a42">4cc011e</a>
 * 「修复NBT膨胀导致坏档问题」。感谢他的贡献与定位。本附属将其方案以 mixin 形式移植到 1.20.1，
 * 避免侵入主包源码；并保留 sync 兜底作为双保险。</p>
 *
 * <hr>
 *
 * <p><b>根因（wuhenqiubai 定位）</b>：原版 {@link PlayerFormComponent#readFromNbt(NbtCompound)}
 * 在读取 {@code formHistory} 时<b>没有先清空现有 list</b>，而是直接 {@code add}。
 * 由于 {@code readFromNbt} 会在玩家登录、切维度、重生 COPY_FROM、组件同步等多个时机被调用，
 * 每次都把 NBT 里的 formHistory <b>追加</b>到运行时 list 上，导致 formHistory 无限累积。
 * 累积到一定程度后，{@code writeToNbt} 写出的 NBT 超过 MC 网络包硬上限 {@code 1048576} 字节，
 * {@code sync()} 抛 {@link IllegalArgumentException}，在重生路径被原版吞掉，
 * 表现为形态未同步、重生按钮无反应、卡死亡屏幕、坏档。</p>
 *
 * <p><b>两层防御</b>：
 * <ol>
 *   <li><b>治本（来自 wuhenqiubai）</b>：{@code readFromNbt} HEAD 注入，读取前先 {@code formHistory.clear()}。
 *       这从源头消除膨胀——无论 readFromNbt 被调用多少次，formHistory 永远只含本次 NBT 的内容，
 *       不再累积。这是核心修复。</li>
 *   <li><b>兜底（附属自有）</b>：{@code sync()} HEAD 接管，sync 前检测 instinctEffects/formHistory
 *       异常膨胀则裁剪，并对「Payload may not be larger than」异常 try-catch 放行重生。
 *       作为治本修复之上的双保险，应对未知的其它膨胀源。</li>
 * </ol></p>
 *
 * <p><b>为什么不完全照搬 wuhenqiubai 的第二处改动（去掉 copyFormAndAbility 里的 _loadForm）？</b><br>
 * 他的 1.21.1 方案还把 {@code copyFormAndAbility} 里的 {@code FormUtils._loadForm} 改成只
 * {@code applyScale + sendFormChange}，理由是「CCA ALWAYS_COPY 已自动复制组件和 origin」。
 * 但本附属在 1.20.1 上有大量 SP / 进化形态，{@code _loadForm} 内的 {@code applyLayer} /
 * {@code ReApplyAccessoryPowerOnPlayerFormChange} 对这些形态的 power 重挂可能是必需的；
 * 既然治本修复（formHistory.clear）已经让 sync 不再超限，就没有必要冒险删 _loadForm 引入新 bug。
 * 故本附属只采用他的治本 clear，保留 _loadForm 原逻辑 + sync 兜底。</p>
 */
@Mixin(PlayerFormComponent.class)
public class PlayerFormComponentSyncGuardMixin {

    /** 本能效果集合膨胀阈值；正常游玩仅几个，超过视为异常 */
    private static final int INSTINCT_EFFECTS_THRESHOLD = 64;
    /** 形态历史膨胀阈值；正常每次切换 clear 重建仅 2-3 个，超过视为异常 */
    private static final int FORM_HISTORY_THRESHOLD = 16;
    /** formHistory 裁剪后保留的最大长度（保留回退能力） */
    private static final int FORM_HISTORY_KEEP = 3;

    /**
     * 【治本】readFromNbt 读取前先清空 formHistory，消除无限累积的根源。
     *
     * <p>思路来自 wuhenqiubai 的 commit 4cc011e。原版直接 add 导致每次反序列化都追加，
     * 这里在 HEAD 清空，保证 list 只含本次 NBT 内容。clear 在所有 readFromNbt 调用点
     * （登录/切维度/重生/同步）都安全——因为紧接着就会从 NBT 重新填充。</p>
     */
    @Inject(method = "readFromNbt", at = @At("HEAD"))
    private void sscAddon$clearFormHistoryBeforeRead(NbtCompound tag, CallbackInfo ci) {
        ((PlayerFormComponent) (Object) this).formHistory.clear();
    }

    /**
     * 【兜底】sync 前自清理异常膨胀字段，并对包超限异常 try-catch 放行重生。
     *
     * <p>治本修复之上双保险，应对未知的其它膨胀源（如 instinctEffects 或 CCA 内部数据）。
     * HEAD 接管原 sync 方法体，直接强转 this 访问字段，规避 @Shadow 对 mod 类的 obf mapping 问题。</p>
     */
    @Inject(method = "sync", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void sscAddon$guardSyncOverflow(CallbackInfo ci) {
        // 通过强转访问目标字段，规避 @Shadow 对 mod 类的 obf mapping 问题
        PlayerFormComponent self = (PlayerFormComponent) (Object) this;

        // 【1 预防性自清理】在序列化之前裁剪异常膨胀的可增长集合
        HashMap<?, ?> effects = self.instinctEffects;
        if (effects.size() > INSTINCT_EFFECTS_THRESHOLD) {
            ShapeShifterCurseFabric.LOGGER.warn(
                    "[SSCA] instinctEffects 异常膨胀(size={}>{})，sync 前已自清理以避免同步包超限",
                    effects.size(), INSTINCT_EFFECTS_THRESHOLD);
            effects.clear();
        }
        List<?> history = self.formHistory;
        if (history.size() > FORM_HISTORY_THRESHOLD) {
            ShapeShifterCurseFabric.LOGGER.warn(
                    "[SSCA] formHistory 异常膨胀(size={}>{})，sync 前已裁剪至 {}",
                    history.size(), FORM_HISTORY_THRESHOLD, FORM_HISTORY_KEEP);
            while (history.size() > FORM_HISTORY_KEEP) {
                history.remove(0);
            }
        }

        // 【2 兜底】手动执行原 sync 逻辑并 try-catch 包超限，放行重生
        try {
            PlayerFormComponent.COMPONENT.sync(self.player);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Payload may not be larger than")) {
                ShapeShifterCurseFabric.LOGGER.warn(
                        "[SSCA] 形态组件同步包仍超限（膨胀源非 instinctEffects/formHistory），已跳过本次 sync 放行重生；"
                                + "下一 tick 正常 sync 会自动补上。异常: {}", e.getMessage());
            } else {
                throw e;
            }
        }

        // 取消原 sync 方法体（已由上方手动完成同步）
        ci.cancel();
    }
}



