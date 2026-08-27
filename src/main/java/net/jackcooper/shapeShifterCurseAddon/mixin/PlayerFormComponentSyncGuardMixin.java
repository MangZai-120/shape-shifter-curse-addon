package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

/**
 * 形态组件同步包超限 —— 兜底防护。
 *
 * <p><b>背景</b>：原版 {@link PlayerFormComponent} 的 {@code readFromNbt} 读取
 * {@code formHistory} / {@code instinctEffects} 时没有先清空现有集合，导致每次反序列化
 * （登录/切维度/重生/同步）都追加，集合无限累积 → writeToNbt 写出的 NBT 超过 MC 网络包
 * 硬上限 {@code 1048576} 字节 → {@code sync()} 抛 {@link IllegalArgumentException}，
 * 在重生路径被原版吞掉，表现为形态未同步、重生按钮无反应、卡死亡屏幕、坏档。</p>
 *
 * <p><b>治本由主包负责</b>：该根因已由官方修复——
 * <a href="https://github.com/onixary/shape-shifter-curse-fabric/pull/491">PR #491</a>
 * （xu233333「修复多次死亡 formHistory 过大的 Bug」）在 {@code readFromNbt} 的
 * formHistory / instinctEffects 块前各加了 {@code clear()}。同样的思路也见
 * <a href="https://github.com/wuhenqiubai/Shape-Shifter-Curse_Unofficial-Port/commit/4cc011e67f156ed2d735a8b2fe5a7d1d71d31a42">wuhenqiubai 4cc011e</a>。
 * 感谢两者的贡献。主包修好后本兜底自然不再触发，仅作旧版 jar / 未更新主包的保险。</p>
 *
 * <p><b>本 mixin 只保留兜底</b>：主包已（或将）治本，附属侧不重复做 readFromNbt clear，
 * 避免与主包改动重复/冲突。本 mixin 仅在 {@code sync()} 外层兜底——
 * sync 前检测 instinctEffects/formHistory 异常膨胀则裁剪，并对「Payload may not be larger than」
 * 异常 try-catch 放行重生。作为主包治本之上的最后一道保险，应对未知的其它膨胀源
 * 或主包尚未更新的旧 jar 场景。</p>
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
     * 兜底：sync 前自清理异常膨胀字段，并对包超限异常 try-catch 放行重生。
     *
     * <p>HEAD 接管原 sync 方法体，直接强转 this 访问字段，规避 @Shadow 对 mod 类的 obf mapping 问题。
     * 主包治本修复（readFromNbt clear）生效后，这里的自清理分支正常情况下永不触发；
     * 仅当膨胀源是未知的其它字段、或主包 jar 未更新时才会兜底生效。</p>
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




