package net.jackcooper.shapeShifterCurseAddon.evolution;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

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

    /** 灵能宝珠转职代价：需 ≥ 该点数才能转职，且转职倒退该数量个里程碑档。 */
    public static final int JOB_CHANGE_COST = 3;

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
        // 转职（灵能宝珠）变身演出期间豁免：避免 nowForm 已切新形态但 route 未切时被下方「离开起点形态即重置」误清空。
        if (comp.isJobChanging()) {
            return;
        }
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

        // 进化 exp 累积（不用 mixin）：用原版总经验的正增量，只算获得、天然排除花费（附魔/铁砧）与死亡掉落。
        int curTotalExp = player.totalExperience;
        int lastExp = comp.getLastTotalExp();
        if (lastExp < 0) {
            comp.setLastTotalExp(curTotalExp); // 首次：以当前为基准，从此刻起算增量
        } else if (curTotalExp != lastExp) {
            if (curTotalExp > lastExp) {
                comp.addExp(curTotalExp - lastExp);
                sync(player);
            }
            comp.setLastTotalExp(curTotalExp);
        }

        // 性能优化：XP 里程碑 / 自动分支解锁检查降频到每 20t（XP 变化低频，升级解锁最多晚 1s，肉眼不可察）。
        // 上方「自动进入路线 / 离开起点形态重置」保持每 tick 即时响应变身，不受此节流影响。
        if (player.age % 20 != 0) {
            return;
        }
        int level = comp.getEvoLevel();
        boolean changed = grantMilestonePoints(comp, route, level);
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

    /** 按当前进化等级发放未发过的里程碑升级点，返回是否有变化。 */
    private static boolean grantMilestonePoints(EvolutionComponent comp, EvolutionRoute route, int evoLevel) {
        int[] milestones = (route != null && route.levelMilestones.length > 0)
                ? route.levelMilestones : LEVEL_MILESTONES;
        boolean changed = false;
        for (int milestone : milestones) {
            if (evoLevel >= milestone && !comp.hasGrantedLevel(milestone)) {
                comp.markGrantedLevel(milestone);
                comp.addPoints(1);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 灵能宝珠转职：从当前进化起点形态转职到另一个进化起点形态。
     * 校验（服务端权威）：当前处于某进化起点形态 + 目标是另一个 enabled 路线的起点形态 + 可用点数 ≥ {@link #JOB_CHANGE_COST}。
     * 通过则置转职标志并带动画变身，变身完成回调里倒退里程碑、扣点、切换到新路线。
     */
    public static void startJobChange(ServerPlayerEntity player, String targetFormIdStr) {
        IForm nowForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier nowFormId = (nowForm == null) ? null : nowForm.getFormID();
        EvolutionRoute curRoute = EvolutionRegistry.INSTANCE.getRouteByStartForm(nowFormId);
        if (curRoute == null) {
            return; // 当前不是进化起点形态（道具侧已拦，双保险）
        }
        Identifier targetFormId = Identifier.tryParse(targetFormIdStr);
        if (targetFormId == null || targetFormId.equals(nowFormId)) {
            return; // 不能转职到当前形态自身
        }
        EvolutionRoute targetRoute = EvolutionRegistry.INSTANCE.getRouteByStartForm(targetFormId);
        if (targetRoute == null || !targetRoute.enabled) {
            player.sendMessage(Text.translatable("message.ssc_addon.job_change.fail.invalid_target").formatted(Formatting.RED), true);
            return;
        }
        EvolutionComponent comp = get(player);
        if (comp.isJobChanging()) {
            return; // 正在转职中，忽略重复请求
        }
        // 门槛按「总进化点数（已用 + 未用）」判定，即已获得的里程碑点数总量
        if (comp.getGrantedLevelCount() < JOB_CHANGE_COST) {
            player.sendMessage(Text.translatable("message.ssc_addon.job_change.fail.not_enough_points", JOB_CHANGE_COST).formatted(Formatting.RED), true);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            return;
        }
        IForm targetForm = RegPlayerForms.getPlayerForm(targetFormId);
        if (targetForm == null) {
            return;
        }
        // 消耗一个灵能宝珠（创造模式不消耗）；没有则拒绝转职（异常情况，界面本由长按道具打开）
        if (!player.getAbilities().creativeMode && !consumeOnePsionicOrb(player)) {
            return;
        }
        comp.setJobChanging(true);
        int fxDuration = StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT;
        player.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, fxDuration, 0, false, false, false));
        // 带黑屏淡入淡出动画变身；完成回调里（nowForm 已是目标形态）再倒退里程碑 + 切路线
        TransformManager.startTransform(player, targetForm, data -> completeJobChange(player, targetRoute));
    }

    /** 转职变身完成回调（nowForm 已是新起点形态）：倒退里程碑、切换到新路线、按倒退后等级重发点。 */
    private static void completeJobChange(ServerPlayerEntity player, EvolutionRoute targetRoute) {
        EvolutionComponent comp = get(player);
        // 倒退用「当前（旧）路线」的里程碑档计算目标等级
        EvolutionRoute oldRoute = EvolutionRegistry.INSTANCE.getRoute(comp.getRoute());
        int[] oldMs = (oldRoute != null && oldRoute.levelMilestones.length > 0)
                ? oldRoute.levelMilestones : LEVEL_MILESTONES;
        int curLevel = comp.getEvoLevel();
        int crossed = 0;
        for (int m : oldMs) {
            if (curLevel >= m) crossed++;
        }
        int targetCrossed = Math.max(0, crossed - JOB_CHANGE_COST);
        int targetLevel = (targetCrossed <= 0) ? 0 : oldMs[targetCrossed - 1];
        // 倒退内置 exp 到目标等级对应值（exp = 目标等级总经验 * 2，因每 2 经验 = 1 进化 exp）
        comp.setExp(EvolutionComponent.levelToTotalExp(targetLevel) * 2);
        // 切换到新路线：新技能树重开
        comp.setRoute(targetRoute.routeId);
        comp.clearUnlockedNodes();
        if (targetRoute.getBaseNodeId() != null) {
            comp.unlock(targetRoute.getBaseNodeId());
        }
        // 按倒退后的进化等级，用新路线里程碑重发点
        comp.clearGrantedLevels();
        comp.setPoints(0);
        grantMilestonePoints(comp, targetRoute, comp.getEvoLevel());
        comp.markStarted();
        comp.markMigrated();
        comp.setJobChanging(false);
        sync(player);
        player.sendMessage(Text.translatable("message.ssc_addon.job_change.success").formatted(Formatting.GREEN, Formatting.BOLD), false);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 旧存档迁移：玩家首次打开加点界面时检测。
     * 旧存档特征：已在进化路线 + 未迁移 + 内置 exp 仍为 0（旧机制未用）+ 已发过里程碑点。
     * 命中则把进化等级加满、发满里程碑点（扣除已花），并提示机制已更新。
     */
    public static void checkAndMigrate(ServerPlayerEntity player) {
        EvolutionComponent comp = get(player);
        if (comp.hasMigrated()) {
            return;
        }
        boolean oldSave = comp.isOnSscaRoute() && comp.getExp() <= 0 && comp.getGrantedLevelCount() > 0;
        if (!oldSave) {
            // 新玩家：标记已迁移 + 初始化累积基准，避免以后误判
            comp.markMigrated();
            comp.setLastTotalExp(player.totalExperience);
            sync(player);
            return;
        }
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute(comp.getRoute());
        int[] milestones = (route != null && route.levelMilestones.length > 0)
                ? route.levelMilestones : LEVEL_MILESTONES;
        // 按玩家当前拥有的进化点数总量（= 已发里程碑数，含已使用过的）反推进化等级；
        // 例：已获得 5 个点（无论花没花）→ 定到第 5 个里程碑（30 级）。points / 已解锁节点保持不变。
        int totalPoints = comp.getGrantedLevelCount();
        int level;
        if (totalPoints <= 0) {
            level = 0;
        } else if (totalPoints >= milestones.length) {
            level = EvolutionComponent.EVO_LEVEL_CAP;
        } else {
            level = milestones[totalPoints - 1];
        }
        comp.setExp(EvolutionComponent.levelToTotalExp(level) * 2);
        comp.setLastTotalExp(player.totalExperience); // 初始化累积基准
        comp.markMigrated();
        sync(player);
        player.sendMessage(Text.translatable("message.ssc_addon.evolution.migrated", level).formatted(Formatting.GOLD), false);
    }

    /** 从玩家主手/副手/物品栏消耗一个灵能宝珠，成功返回 true。 */
    private static boolean consumeOnePsionicOrb(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(SscAddon.PSIONIC_ORB)) {
            player.getMainHandStack().decrement(1);
            return true;
        }
        if (player.getOffHandStack().isOf(SscAddon.PSIONIC_ORB)) {
            player.getOffHandStack().decrement(1);
            return true;
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(SscAddon.PSIONIC_ORB)) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }
}
