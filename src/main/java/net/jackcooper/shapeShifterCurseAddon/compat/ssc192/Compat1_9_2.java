package net.jackcooper.shapeShifterCurseAddon.compat.ssc192;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * SSC 1.9.2 兼容层（仅 v8.0.0-fabric-ssc-1.9.2 分支使用）。
 *
 * <p>背景：v8 主线建立在 SSC 1.10 的新形态体系（IForm 接口 / getFormID() / formFlag /
 * applyScale / NormalGroup.registerForm / RegPlayerFormComponent.PLAYER_FORM / 组件字段
 * nowForm / TransformManager.startTransform(player, form, consumer)）之上；而 SSC 1.9.2
 * 运行的是旧体系（PlayerFormBase 公开字段 FormID / PlayerFormGroup.addForm /
 * RegPlayerFormComponent.PLAYER_FORM + getCurrentForm()/setCurrentForm /
 * TransformManager.handleDirectTransform(player, form, immediately) + 1.9.2 自带的
 * new_form_system 半成品未接线）。</p>
 *
 * <p>本类把全部差异集中收口，业务代码只 import 本类，保持与主线 v8 的 diff 最小：
 * <ul>
 *   <li>形态注册：{@link #registerForm} / {@link #registerGroup}</li>
 *   <li>flag 系统：1.9.2 无字符串 flag，用 phase + 布尔开关近似（{@link #applyCommonFlags}）</li>
 *   <li>形态查询/比较：{@link #getFormId} / {@link #formEquals} / {@link #getPlayerForm}</li>
 *   <li>当前形态读写：{@link #getCurrentForm} / {@link #nowForm}</li>
 *   <li>变身：{@link #startTransform} / {@link #immediatelyTransform}（带 consumer 的
 *       startTransform 在 1.9.2 无回调参数，改为变身完成后同 tick 直接回调——1.9.2 的
 *       handleDirectTransform 为同步执行，语义等价）</li>
 *   <li>flag 查询：{@link #hasFlag}（special_form 等按 phase=PHASE_SP 近似判定）</li>
 * </ul></p>
 *
 * <p>体型缩放不走 Java（1.9.2 的 PlayerFormBase 无 applyScale 链），统一走数据层
 * shape-shifter-curse:scale power（v8 已有 form_*_scale.json，1.9.2 同格式直接兼容），
 * 详见 SscAddonForms 内注释。</p>
 */
public final class Compat1_9_2 {

    private Compat1_9_2() {
    }

    // ==================== 形态注册 ====================

    /** 等价 1.10 RegPlayerForms.registerPlayerForm(form)。 */
    public static void registerForm(PlayerFormBase form) {
        RegPlayerForms.registerPlayerForm(form);
    }

    /**
     * 等价 1.10 RegPlayerForms.registerPlayerFormGroup(new NormalGroup(id).registerForm(tier, form))。
     * 1.9.2 为 new PlayerFormGroup(id).addForm(form, tier)（参数序相反）。
     */
    public static void registerGroup(Identifier groupId, PlayerFormBase form, int tier) {
        RegPlayerForms.registerPlayerFormGroup(
                new net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup(groupId).addForm(form, tier));
    }

    // ==================== flag / phase ====================

    /**
     * 近似 1.10 的 formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune)。
     * 1.9.2 无字符串 flag 系统，映射到等价布尔开关：
     * <ul>
     *   <li>SpecialForm → phase = PHASE_SP（1.9.2 的 SP 判定依据，成就/月髓环爆炸逻辑等价）</li>
     *   <li>NoInstinct / NoCursedMoonEffect / InhibitorImmune → 1.9.2 里 SP 形态默认
     *       不受本能/诅咒之月影响（PHASE_SP 分支本身跳过本能条与月变），无需额外开关</li>
     * </ul>
     */
    public static void applySpFlags(PlayerFormBase form) {
        form.setPhase(PlayerFormPhase.PHASE_SP);
    }

    /**
     * 等价 1.10 form.getFormFlag().contains("xxx")。
     * 1.9.2 无 flag：special_form 按 PHASE_SP 近似；其它 flag（no_instinct 等）在 1.9.2
     * 的 SP 分支行为里隐含，返回与 special_form 一致的近似值即可满足现有调用点语义。
     */
    public static boolean hasFlag(Object form, String flag) {
        if (!(form instanceof PlayerFormBase base)) {
            return false;
        }
        return "special_form".equals(flag) && base.getPhase() == PlayerFormPhase.PHASE_SP;
    }

    // ==================== 形态查询 ====================

    /** 等价 1.10 form.getFormID()；1.9.2 为公开字段 FormID。 */
    public static Identifier getFormId(Object form) {
        if (form instanceof PlayerFormBase base) {
            return base.FormID;
        }
        return null;
    }

    /** 等价 1.10 RegPlayerForms.getPlayerForm(Identifier)；1.9.2 返回 PlayerFormBase。 */
    public static PlayerFormBase getPlayerForm(Identifier id) {
        return RegPlayerForms.getPlayerForm(id);
    }

    /** 等价 1.10 RegPlayerForms.getPlayerForm(String)（toString 形态名）。 */
    public static PlayerFormBase getPlayerForm(String name) {
        return RegPlayerForms.getPlayerForm(name);
    }

    /** 等价 1.10 form.isPlayerForm(player)。 */
    public static boolean isPlayerForm(PlayerFormBase form, PlayerEntity player) {
        PlayerFormBase cur = nowForm(player);
        return cur != null && cur.FormID != null && cur.FormID.equals(form.FormID);
    }

    /** 等价 1.10 a.isEquals(b)。 */
    public static boolean formEquals(Object a, Object b) {
        Identifier ia = getFormId(a);
        Identifier ib = getFormId(b);
        return ia != null && ia.equals(ib);
    }

    // ==================== 当前形态读写 ====================

    /**
     * 等价 1.10 RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm。
     * 1.9.2 为 PLAYER_FORM.get(player).getCurrentForm()。
     */
    public static PlayerFormBase nowForm(PlayerEntity player) {
        PlayerFormComponent comp = RegPlayerFormComponent.PLAYER_FORM.get(player);
        return comp == null ? null : comp.getCurrentForm();
    }

    /** 现在形态的 FormID（等价 1.10 nowFormID 字段）。 */
    public static Identifier nowFormId(PlayerEntity player) {
        PlayerFormBase f = nowForm(player);
        return f == null ? null : f.FormID;
    }

    // ==================== 变身 ====================

    /**
     * 等价 1.10 TransformManager.startTransform(player, form, consumer)（带黑屏动画变身）。
     * 1.9.2 handleDirectTransform(player, form, isByCure=false) 同步执行变身全流程
     * （黑屏动画在内部异步 tick 播放，但形态切换当 tick 即达），故 consumer 在调用后直接执行，
     * 语义等价（v8 调用点的 consumer 只用于职业切换/进化树记账，无时序依赖）。
     * 第三参语义已核实：v7 mixin 签名为 boolean isByCure（非 immediately），false = 非抑制剂解除。
     */
    public static void startTransform(PlayerEntity player, PlayerFormBase form, Consumer<Object> onTransformComplete) {
        TransformManager.handleDirectTransform(player, form, false);
        if (onTransformComplete != null) {
            onTransformComplete.accept(form);
        }
    }

    /**
     * 等价 1.10 TransformManager.immediatelyTransform(player, form)（无动画瞬间变）。
     * 1.9.2 对应 setFormDirectly（v7 RedFormTickMixin 注释明确：setFormDirectly 瞬换不播动画）。
     * 注：handleDirectTransform 的第三参是 isByCure 而非 immediately，不能用 true 达成瞬变。
     */
    public static void immediatelyTransform(PlayerEntity player, PlayerFormBase form) {
        TransformManager.setFormDirectly(player, form);
    }
}
