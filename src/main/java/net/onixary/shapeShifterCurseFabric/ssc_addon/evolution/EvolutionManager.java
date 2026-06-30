package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

/**
 * SSCA 进化加点系统 - 服务端业务逻辑入口（框架骨架）。
 *
 * 网络包接收器与指令统一调用本类，避免业务逻辑散落。
 * 「待后续设计」标注处为技能解锁规则 / EXP 消耗 / 前置校验等，留待业务设计阶段填充。
 */
public final class EvolutionManager {
    private EvolutionManager() {
    }

    public static EvolutionComponent get(ServerPlayerEntity player) {
        return RegEvolutionComponent.EVOLUTION.get(player);
    }

    public static void sync(ServerPlayerEntity player) {
        RegEvolutionComponent.EVOLUTION.sync(player);
    }

    /**
     * 进化形态继续进化的门控：当前形态若是某条进化路线的起点形态，
     * 必须已解锁该路线的【全部分支节点】，才允许月髓环 / 进化石继续进化到分支形态。
     *
     * <p>当前形态不是任何路线的起点形态时恒为 true（不施加门控）。</p>
     */
    public static boolean canUpgradeFoxEvolve(ServerPlayerEntity player) {
        IForm nowForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier nowFormId = (nowForm == null) ? null : nowForm.getFormID();
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRouteByStartForm(nowFormId);
        if (route == null) {
            return true; // 非任何进化路线的起点形态 → 不限制
        }
        EvolutionComponent comp = get(player);
        if (!comp.isOnSscaRoute()) {
            return false;
        }
        java.util.List<String> branchNodes = route.getBranchNodeIds();
        if (branchNodes.isEmpty()) {
            return true;
        }
        for (String bn : branchNodes) {
            if (!comp.isUnlocked(bn)) {
                return false;
            }
        }
        return true;
    }

    /** 发点等级里程碑默认值（route JSON 未配置 level_milestones 时回退）。 */
    private static final int[] LEVEL_MILESTONES = {5, 10, 15, 20, 30, 40, 45};

    /** 选择进化路线；自动解锁该路线初始节点，并（若当前非起点形态）变身进入起点形态。 */
    public static void selectRoute(ServerPlayerEntity player, String routeId) {
        EvolutionComponent comp = get(player);
        comp.setRoute(routeId);
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute(routeId);
        if (route != null) {
            if (route.getBaseNodeId() != null) {
                comp.unlock(route.getBaseNodeId());
            }
            transformToStartForm(player, route);
        }
        sync(player);
    }

    /** 若玩家当前形态非该路线起点形态，立即变身为起点形态。 */
    private static void transformToStartForm(ServerPlayerEntity player, EvolutionRoute route) {
        if (route.startForm == null) {
            return;
        }
        IForm currentForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier formId = (currentForm == null) ? null : currentForm.getFormID();
        if (formId == null || formId.equals(route.startForm)) {
            return;
        }
        IForm startForm = RegPlayerForms.getPlayerForm(route.startForm);
        if (startForm != null) {
            TransformManager.immediatelyTransform(player, startForm);
        }
    }

    /** 该形态 id 是否为某条「已开放」进化路线的起点形态（可在开局选形态界面进入）。 */
    private static boolean isStartFormAllowed(Identifier formId) {
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRouteByStartForm(formId);
        return route != null && route.enabled;
    }

    /**
     * 游戏开局直接走 SSCA 进化路线：玩家在 StartBook 界面选定一个 SSCA 形态后调用。
     *
     * <p>仅允许尚未启用 mod（{@code ORIGINAL_BEFORE_ENABLE}）的玩家进入，与本体「翻开幻形者之书」对称。
     * 流程：设置进化路线并解锁初始节点 → 触发启用 mod 语义 → 带黑屏淡入淡出动画变身到目标形态，
     * 动画期间定身（STUN），完成时播放升级音效。</p>
     *
     * @param formIdStr 目标 SSCA 起点形态 ID 字符串
     */
    public static void startSscaRoute(ServerPlayerEntity player, String formIdStr) {
        if (!RegPlayerForms.ORIGINAL_BEFORE_ENABLE.isPlayerForm(player)) {
            return;
        }
        Identifier formId = Identifier.tryParse(formIdStr);
        if (formId == null || !isStartFormAllowed(formId)) {
            return;
        }
        IForm targetForm = RegPlayerForms.getPlayerForm(formId);
        if (targetForm == null) {
            return;
        }
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRouteByStartForm(formId);
        // 设置进化路线并解锁初始节点（独立于变身动画）
        EvolutionComponent comp = get(player);
        comp.setRoute(route.routeId);
        if (route.getBaseNodeId() != null) {
            comp.unlock(route.getBaseNodeId());
        }
        sync(player);
        // 启用 mod 语义（成就 / 状态），与本体「翻开幻形者之书」一致
        ShapeShifterCurseFabric.ON_ENABLE_MOD.trigger(player);
        // 进化演出：黑屏淡入淡出动画期间定身，完成时升级音效
        int fxDuration = StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT;
        player.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, fxDuration, 0, false, false, false));
        TransformManager.startTransform(player, targetForm, data ->
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F));
    }

    /** 选择 SP 分支。框架阶段不校验分支前置条件，待业务设计。 */
    public static void selectBranch(ServerPlayerEntity player, String branchId) {
        get(player).setBranch(branchId);
        sync(player);
    }

    /**
     * 请求解锁一个天赋节点：校验节点合法、未解锁、非自动节点、前置满足（AND）、点数足够，
     * 通过则扣点并解锁。节点取自玩家当前路线（数据驱动）。
     */
    public static boolean tryUnlock(ServerPlayerEntity player, String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return false;
        }
        EvolutionComponent comp = get(player);
        if (!comp.isOnSscaRoute()) {
            return false;
        }
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute(comp.getRoute());
        EvolutionNode node = (route == null) ? null : route.getNode(nodeId);
        if (node == null || node.autoUnlock || comp.isUnlocked(nodeId)) {
            return false;
        }
        if (!prereqsMet(comp, node)) {
            return false;
        }
        if (!comp.spendPoints(node.cost)) {
            return false;
        }
        comp.unlock(nodeId);
        sync(player);
        return true;
    }

    /** 前置语义：节点无前置，或前置中【全部】已解锁才满足（AND）。 */
    private static boolean prereqsMet(EvolutionComponent comp, EvolutionNode node) {
        if (node.prereqs.isEmpty()) {
            return true;
        }
        for (String p : node.prereqs) {
            if (!comp.isUnlocked(p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 服务端每 tick 调用：当前形态为某路线起点形态时自动进入该路线并解锁初始节点；
     * 按 route 的经验等级里程碑发放点数，到达 route 的自动分支等级时解锁满足前置的分支节点；
     * 离开起点形态则重置进度。仅对已走 SSCA 路线的玩家生效。
     */
    public static void tickPlayer(ServerPlayerEntity player) {
        EvolutionComponent comp = get(player);
        IForm nowForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier nowFormId = (nowForm == null) ? null : nowForm.getFormID();

        // 成为「某进化路线的起点形态」即自动进入该 SSCA 路线并解锁初始节点
        //（覆盖开局之书 / 指令等所有途径）；放在 isOnSscaRoute 早退之前以便首次自动设路线。
        EvolutionRoute enterRoute = EvolutionRegistry.INSTANCE.getRouteByStartForm(nowFormId);
        if (enterRoute != null) {
            boolean autoChanged = false;
            if (!comp.isOnSscaRoute()) {
                comp.setRoute(enterRoute.routeId);
                autoChanged = true;
            }
            String baseId = enterRoute.getBaseNodeId();
            if (baseId != null && !comp.isUnlocked(baseId)) {
                comp.unlock(baseId);
                autoChanged = true;
            }
            if (!comp.hasStarted()) {
                comp.markStarted();
                autoChanged = true;
            }
            if (autoChanged) {
                sync(player);
            }
        }

        if (!comp.isOnSscaRoute()) {
            return;
        }

        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute(comp.getRoute());
        boolean onOwnStartForm = route != null && route.startForm != null
                && route.startForm.equals(nowFormId);

        // 离开自己路线的起点形态（变成其它形态）→ 重置进度。
        // 用 started 标志避免变身动画期间（route 已设但尚未变成起点形态）误重置。
        if (!onOwnStartForm && comp.hasStarted()) {
            comp.reset();
            sync(player);
            return;
        }

        int level = player.experienceLevel;
        boolean changed = false;
        int[] milestones = (route != null && route.levelMilestones.length > 0)
                ? route.levelMilestones : LEVEL_MILESTONES;
        for (int milestone : milestones) {
            if (level >= milestone && !comp.hasGrantedLevel(milestone)) {
                comp.markGrantedLevel(milestone);
                comp.addPoints(1);
                changed = true;
            }
        }
        int autoBranchLevel = (route != null) ? route.autoBranchLevel : 50;
        if (route != null && autoBranchLevel > 0 && level >= autoBranchLevel) {
            for (EvolutionNode node : route.nodes) {
                if (node.autoUnlock && !node.branch.isEmpty()
                        && !comp.isUnlocked(node.id) && prereqsMet(comp, node)) {
                    comp.unlock(node.id);
                    changed = true;
                }
            }
        }
        if (changed) {
            sync(player);
        }
    }

    /** 管理指令：把目标玩家进化路线设为全解锁。 */
    public static void unlockAll(ServerPlayerEntity player) {
        get(player).setUnlockAll(true);
        sync(player);
    }

    /** 管理指令：重置目标玩家全部进化数据。 */
    public static void reset(ServerPlayerEntity player) {
        get(player).reset();
        sync(player);
    }
}
