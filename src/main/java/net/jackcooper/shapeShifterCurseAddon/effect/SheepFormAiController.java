package net.jackcooper.shapeShifterCurseAddon.effect;

import net.jackcooper.shapeShifterCurseAddon.mixin.entity.GoalSelectorGoalsAccessor;
import net.jackcooper.shapeShifterCurseAddon.mixin.entity.MobEntityGoalAccessor;
import net.jackcooper.shapeShifterCurseAddon.mixin.entity.PrioritizedGoalAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.EatGrassGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 变羊 mob 的「羊 AI」控制器（羊了个羊，服务端权威，2026-09-29 用户四轮定稿）。
 *
 * <p><b>架构（Goal 集备份 → 替换 → 回放）</b>：
 * <ul>
 *   <li><b>变羊时</b>：先把 goalSelector / targetSelector 的原生 Goal 集<b>逐条备份</b>
 *       （裸 Goal + 优先级，经 accessor 读 {@code GoalSelector.goals} 与
 *       {@code PrioritizedGoal.goal/priority}），再整体替换为原版成年羊 Goal 集 +
 *       FleeEntityGoal(逃离施法者，苦力怕怕猫同款机制)。</li>
 *   <li><b>变羊结束</b>：清掉羊 Goal 集，<b>按备份逐条回放</b>（原优先级原 Goal 实例）。
 *       不用 initGoals() 重造——它只覆盖「在该方法里注册」的 Goal，<b>在构造器里注册 Goal
 *       的生物（部分原版与 mod 生物）重造后这部分永久丢失</b>（2026-09-29 实测
 *       「变回来后部分生物 AI 消失」根因）。备份/回放对任意注册途径都完整。</li>
 * </ul>
 * Goal 实例本身与 mob 绑定（构造时传入 mob），回放同一实例即恢复原行为。</p>
 *
 * <p><b>原能力停用</b>（用户定稿）：替换期间原生 Goal 全部不在选择器里——苦力怕自爆、
 * 唤魔者召唤恼鬼等一切主动行为彻底无法触发；targetSelector 一并备份并留空（羊不索敌）。</p>
 */
public final class SheepFormAiController {

	private SheepFormAiController() {}

	/** 变羊目标 UUID → 施法者 UUID（逃离源）。 */
	private static final Map<UUID, UUID> FLEE_FROM = new HashMap<>();

	/** 备份条目：裸 Goal + 注册优先级（goalSelector 与 targetSelector 各一份列表）。
	 *  mobRef：swap 时的 mob 实例引用——重载/换维度后同 UUID 是新实例，旧备份里的 Goal
	 *  全绑死在旧实体上（回放=把死引用塞进新 mob 的选择器，永不 tick），用于判备份过期。 */
	private record GoalBackup(MobEntity mobRef, List<GoalEntry> goals, List<GoalEntry> targets) {}
	private record GoalEntry(int priority, Goal goal) {}

	/** 变羊目标 UUID → Goal 备份。 */
	private static final Map<UUID, GoalBackup> BACKUPS = new HashMap<>();

	/** 咩弹命中时登记（目标 mob 与施法者）。 */
	public static void setFleeFrom(UUID target, UUID caster) {
		if (target != null && caster != null) {
			FLEE_FROM.put(target, caster);
		}
	}

	/** 变羊结束：清登记（Goal 回放由 {@link #restoreGoals} 承担，须在 clear 之前调用）。 */
	public static void clear(UUID target) {
		if (target != null) {
			FLEE_FROM.remove(target);
			BACKUPS.remove(target);
		}
	}

	/**
	 * 羊 AI 逐 tick 推进（SheepFormEffect.applyUpdateEffect 服务端调用，仅 MobEntity）。
	 * 首个 tick 执行「备份 + 替换」（幂等）。
	 */
	public static void tickSheepAi(LivingEntity entity) {
		if (entity.getWorld().isClient || !(entity instanceof MobEntity mob)) {
			return;
		}
		swapGoalsToSheep(mob);
	}

	/** 备份原生 Goal 集 → 替换为羊 Goal 集（幂等，按实例身份判）。 */
	private static void swapGoalsToSheep(MobEntity mob) {
		GoalBackup prev = BACKUPS.get(mob.getUuid());
		if (prev != null && prev.mobRef() == mob) {
			return; // 同一实例已替换过（幂等挡板）；实例已换则继续走自愈重备
		}
		UUID casterId = FLEE_FROM.get(mob.getUuid());
		if (casterId == null || !(mob.getWorld() instanceof ServerWorld world)) {
			return;
		}
		var caster = world.getEntity(casterId);
		if (!(caster instanceof LivingEntity casterLiving)) {
			return;
		}
		MobEntityGoalAccessor acc = (MobEntityGoalAccessor) mob;
		GoalSelector goalSelector = acc.ssca$getGoalSelector();
		GoalSelector targetSelector = acc.ssca$getTargetSelector();
		// ① 备份原生 Goal 集（裸 Goal + 优先级；含构造器注册的——比 initGoals 重造完整）。
		//    实例已换（重载/换维度）时旧备份的 Goal 绑死旧实体不可回放——方法入口挡板
		//    已放行，此处直接用当前选择器内容（重载后构造器重建的原生集）覆盖重新备份。
		GoalBackup backup = new GoalBackup(mob, snapshotGoals(goalSelector), snapshotGoals(targetSelector));
		BACKUPS.put(mob.getUuid(), backup);
		// 诊断日志（2026-09-29 实测「AI 消失」定位用；确认修复后可移除）
		org.slf4j.LoggerFactory.getLogger("SSCA/SheepAI").info(
				"swap: {} goals(goalSel={}) + targets(targetSel={})",
				mob.getType().toString(), backup.goals().size(), backup.targets().size());
		// ② 清空并替换为羊 Goal 集（照抄 SheepEntity.initGoals 注册表与优先级：
		// 0 Swim / 1 EscapeDanger(1.25) / 2 EatGrass(仅 Animal) / 3 WanderFar(1.0)
		// / 4 看玩家(6格) / 5 环视；AnimalMate/Tempt/FollowParent 依赖羊专属行为，非羊挂不上，跳过）
		stopAndClear(goalSelector);
		stopAndClear(targetSelector);
		PathAwareEntity pathMob = mob instanceof PathAwareEntity p ? p : null;
		goalSelector.add(0, new SwimGoal(mob));
		if (pathMob != null) {
			goalSelector.add(1, new EscapeDangerGoal(pathMob, 1.25));
		}
		if (mob instanceof net.minecraft.entity.passive.AnimalEntity animal) {
			goalSelector.add(2, new EatGrassGoal(animal));
		}
		if (pathMob != null) {
			goalSelector.add(3, new WanderAroundFarGoal(pathMob, 1.0));
		}
		goalSelector.add(4, new LookAtEntityGoal(mob, PlayerEntity.class, 6.0F));
		goalSelector.add(5, new LookAroundGoal(mob));
		// ③ 逃离施法者（苦力怕怕猫同款）：FleeEntityGoal 优先级 0（最高档，压过游荡/吃草），
		//    12 格内逃离，惊慌速度 1.2。两个谓词（selection + includeTarget）都锁定施法者实体。
		//    FleeEntityGoal 构造需要 PathAwareEntity——非 PathAware 的稀有 mob 跳过逃离（羊 Goal 集其余照挂）。
		if (pathMob != null) {
			FleeEntityGoal<LivingEntity> flee = new FleeEntityGoal<>(
					pathMob, LivingEntity.class,
					(LivingEntity target) -> target == casterLiving,
					12.0F, 1.0, 1.2,
					(LivingEntity target) -> target == casterLiving);
			goalSelector.add(0, flee);
		}
		// ④ targetSelector 留空：羊不索敌不记仇
		// ⑤ 清残留仇恨（换 Goal 前已被锁定的旧目标/旧攻击状态）
		mob.setTarget(null);
		mob.setAttacking(null);
	}

	/** 备份是否绑在当前实例上（sweep 判定重载中间态用：备份在但实例已换 → 需重 swap）。 */
	public static boolean hasStaleBackupFor(LivingEntity entity) {
		GoalBackup b = BACKUPS.get(entity.getUuid());
		return b != null && b.mobRef() != entity;
	}

	/** mob 卸载时清全部 AI 记录（Goal 集随实体消亡，备份的旧 Goal 实例已无意义）。 */
	public static void clearOffline(UUID target) {
		if (target != null) {
			FLEE_FROM.remove(target);
			BACKUPS.remove(target);
		}
	}

	/**
	 * 实体重载后的中间态修复（sweep 调用）：效果残留但实例已换（新实例构造器已重建原生集），
	 * 旧备份的 Goal 绑死旧实体不可回放——丢弃旧备份并重新 swap（用当前原生集重新备份）。
	 * 只在「实例已换」时执行，同实例正常变羊中恒不触发。
	 */
	public static void reswapAfterReload(LivingEntity entity) {
		if (hasStaleBackupFor(entity) && entity instanceof MobEntity mob) {
			BACKUPS.remove(entity.getUuid());
			swapGoalsToSheep(mob);
		}
	}

	/** 从选择器读出全部（优先级，裸 Goal）对（快照拷贝）。 */
	private static List<GoalEntry> snapshotGoals(GoalSelector selector) {
		List<GoalEntry> list = new ArrayList<>();
		for (PrioritizedGoal pg : ((GoalSelectorGoalsAccessor) selector).ssca$getGoals()) {
			PrioritizedGoalAccessor pga = (PrioritizedGoalAccessor) pg;
			list.add(new GoalEntry(pga.ssca$getPriority(), pga.ssca$getGoal()));
		}
		return list;
	}

	/** 停止并清空一个选择器：先对运行中的 PrioritizedGoal 调 stop() 释放 Move/Look 控制锁
	 *  （GoalSelector.clear 只是 removeIf，不调 stop——运行中的 Goal 会在 goalsByControl 里
	 *  持锁，替换后新 Goal 抢不到 Move 控制权 → 站桩。stop 是 PrioritizedGoal 的 public
	 *  方法，反编译已核实签名）。 */
	private static void stopAndClear(GoalSelector selector) {
		for (PrioritizedGoal pg : new ArrayList<>(((GoalSelectorGoalsAccessor) selector).ssca$getGoals())) {
			if (pg.isRunning()) {
				pg.stop();
			}
		}
		selector.clear(goal -> true);
	}

	/**
	 * 变羊结束恢复原生 Goal 集（SheepFormRestoreHandler.restore 调用，须在 clear 之前）：
	 * 清羊 Goal → 按备份逐条回放（原优先级原 Goal 实例）。任意注册途径（构造器/initGoals/
	 * mod 注入）的 Goal 都完整恢复——根治「部分生物 AI 消失」。
	 */
	public static void restoreGoals(LivingEntity entity) {
		if (entity.getWorld().isClient || !(entity instanceof MobEntity mob)) {
			return;
		}
		GoalBackup backup = BACKUPS.get(mob.getUuid());
		if (backup == null) {
			return; // 未替换过（玩家目标/重复恢复），no-op
		}
		BACKUPS.remove(mob.getUuid());
		MobEntityGoalAccessor acc = (MobEntityGoalAccessor) mob;
		stopAndClear(acc.ssca$getGoalSelector());
		stopAndClear(acc.ssca$getTargetSelector());
		// 实例已换（重载/换维度）：旧备份的 Goal 绑死旧实体，不回放死引用——新实例构造器
		// 已重建原生集，直接保持即可。
		if (backup.mobRef() != mob) {
			org.slf4j.LoggerFactory.getLogger("SSCA/SheepAI").info(
					"restore: {} instance changed, keep rebuilt native goals",
					mob.getType().toString());
			return;
		}
		for (GoalEntry entry : backup.goals()) {
			acc.ssca$getGoalSelector().add(entry.priority(), entry.goal());
		}
		for (GoalEntry entry : backup.targets()) {
			acc.ssca$getTargetSelector().add(entry.priority(), entry.goal());
		}
		// 诊断日志（2026-09-29 实测「AI 消失」定位用；确认修复后可移除）
		org.slf4j.LoggerFactory.getLogger("SSCA/SheepAI").info(
				"restore: {} replayed {} goals + {} targets",
				mob.getType().toString(), backup.goals().size(), backup.targets().size());
		// 清残留导航/仇恨，原生 AI 从干净状态启动
		mob.getNavigation().stop();
		mob.setTarget(null);
		mob.setAttacking(null);
	}
}
