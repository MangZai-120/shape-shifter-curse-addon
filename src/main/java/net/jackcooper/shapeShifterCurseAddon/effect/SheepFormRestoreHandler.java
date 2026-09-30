package net.jackcooper.shapeShifterCurseAddon.effect;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 变羊属性换血与恢复状态机（羊了个羊配套，服务端权威，jackcooper，2026-09-29 用户定稿）。
 *
 * <p><b>变羊瞬间（onSheepStart）</b>：记录原最大生命 → 挂 8 心上限修饰符 → 按血量百分比
 * 重设当前血（满血变羊=8 心满、半血=4 心）；挂移速统一修饰符（动态求商把任何基础移速
 * 压到羊的 0.23）。</p>
 *
 * <p><b>恢复（onSheepEnd / sweep 兜底）</b>：移除两枚修饰符（还原原上限与原移速）→ 按
 * 「变羊期间血量百分比」逆向换算当前血（变羊时被打掉的血保留，不白嫖不亏）。</p>
 *
 * <p><b>边界</b>：</p>
 * <ul>
 *   <li>重复施法（效果刷新）：{@code onApplied} 会再次触发——状态表已有记录时只刷新时长
 *       不重复换血（百分比已按羊上限对齐，直接沿用）；</li>
 *   <li>死亡：效果随实体清除不走 onRemoved——sweep 扫描兜底 + 修饰符本就不进 NBT，
 *       重生/重进存档后原版 clamp 回原属性，状态表清掉即可；</li>
 *   <li>断线（玩家）：同上，sweep（每 5t）发现效果已无即恢复；修饰符 addTemporary 不进
 *       NBT，重进存档天然回原上限；</li>
 *   <li>牛奶清除：走原版 onRemoved 正常恢复。</li>
 * </ul>
 */
public final class SheepFormRestoreHandler {

	private SheepFormRestoreHandler() {}

	/** 移速统一修饰符 UUID（独立于攻击修饰符，动态系数）。 */
	public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("B7F29D41-2E85-4C7A-9D3B-6E84C1A2F0B3");
	/** 血量上限修饰符 UUID。 */
	public static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("C3A68E20-1D97-4B5F-AE2C-7F91D0B3E4A5");

	/** 变羊期间移速上限（格/秒，2026-09-29 用户定稿「最大 5 格/s」）。
	 *  原版速度属性→格/s 换算：实际格/s ≈ 属性值 × 43.17（1.20.1 玩家定 0.1→4.317 格/s 实测口径）。
	 *  5 格/s 对应属性值 ≈ 0.1159。 */
	public static final double SHEEP_MAX_SPEED_UNITS = 5.0;
	/** 血量公式基数：≤20 血固定 8 颗心（16.0 HP）。 */
	public static final double SHEEP_BASE_MAX_HEALTH = 16.0;
	/** 血量公式阈值：20 血（含）以下为基数档。 */
	public static final double HEALTH_FORMULA_THRESHOLD = 20.0;
	/** 血量公式步进：每多 4 点原血 → 羊上限 +1 颗心（即 +2.0 HP）。
	 *  用户例（2026-09-29）：24 血生物 → 8心 + (24-20)/4 = 9 颗心 = 18.0 HP。 */
	public static final double HEALTH_PER_STEP = 4.0;

	/** 按用户公式算变羊后的最大生命：≤20 血固定 16.0（8 心）；>20 每 +4 血 +1 颗心（+2.0 HP）。
	 *  例：24 血 → 8 + 1 = 9 颗心 = 18.0；44 血 → 8 + 6 = 14 颗心 = 28.0。 */
	public static double sheepMaxHealthFor(double originalMax) {
		if (originalMax <= HEALTH_FORMULA_THRESHOLD) return SHEEP_BASE_MAX_HEALTH;
		double hearts = SHEEP_BASE_MAX_HEALTH / 2.0 + Math.floor((originalMax - HEALTH_FORMULA_THRESHOLD) / HEALTH_PER_STEP);
		return hearts * 2.0;
	}

	/** 换血记录：实体 UUID → 变羊前的原最大生命（恢复时用变羊期间百分比 × 此值）。 */
	private static final Map<UUID, Double> ORIGINAL_MAX_HEALTH = new HashMap<>();
	/** 上次 sweep 的世界时间（5t 节流）。 */
	private static long lastSweep = -1;

	/** 变羊开始（SheepFormEffect.onApplied 服务端调用）。 */
	public static void onSheepStart(LivingEntity entity) {
		if (ORIGINAL_MAX_HEALTH.containsKey(entity.getUuid())) {
			return; // 效果刷新（重复施法）：百分比已按羊上限对齐，不重复换血
		}
		double originalMax = entity.getMaxHealth();
		double ratio = originalMax > 0 ? entity.getHealth() / originalMax : 1.0;
		double sheepMax = sheepMaxHealthFor(originalMax);

		EntityAttributeInstance healthAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (healthAttr != null) {
			// 按公式上限压缩：MULTIPLY_TOTAL 系数 = 目标上限/原值 - 1（例 24 血 → 18/24-1）
			double factor = sheepMax / Math.max(0.01, originalMax) - 1.0;
			healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
			healthAttr.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_UUID,
					"SSCA Sheep Form Health", factor, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		// 按百分比重设当前血（挂完上限修饰符后 setHealth 自动 clamp 到羊上限）
		entity.setHealth((float) (sheepMax * Math.max(0.0, Math.min(1.0, ratio))));
		// 记录原值（恢复基准）
		ORIGINAL_MAX_HEALTH.put(entity.getUuid(), originalMax);

		applySpeedUniform(entity);
	}

	/** 变羊结束（onRemoved 服务端调用）。 */
	public static void onSheepEnd(LivingEntity entity) {
		restore(entity);
	}

	/**
	 * 重追踪/重进后的修饰符重挂（sweep 的实例已换分支调用；2026-09-30 伴生修复）：
	 * 临时修饰符不进 NBT，同 JVM 重进（退出到标题再进存档）后效果还在但修饰符已随实体
	 * 重建消失——血量/移速回到原生值。此方法按记录的原上限重算羊上限并重挂两枚修饰符、
	 * 按当前血量百分比压回羊血（与 onSheepStart 同公式；移速只压快的不加速）。
	 * 记录不存在（完整重启游戏）时为 no-op——那种场景 AI/外观随效果 NBT 恢复，数值走
	 * 原生属性（可接受降级，恢复时也按无记录路径只清修饰符）。
	 */
	public static void reapplyModifiers(LivingEntity entity) {
		Double originalMax = ORIGINAL_MAX_HEALTH.get(entity.getUuid());
		if (originalMax == null) return;
		double sheepMax = sheepMaxHealthFor(originalMax);
		EntityAttributeInstance healthAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (healthAttr != null) {
			double factor = sheepMax / Math.max(0.01, originalMax) - 1.0;
			healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
			healthAttr.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_UUID,
					"SSCA Sheep Form Health", factor, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
			if (entity.getHealth() > sheepMax) {
				entity.setHealth((float) sheepMax);
			}
		}
		applySpeedUniform(entity);
	}

	/** 移速上限：属性压到 ≈0.1159（≈5 格/s；MULTIPLY_TOTAL 求商系数按当前基础值动态算）。
	 *  用户定稿「移动速度最大为 5 格/s」——快者被压到上限；慢者（基础属性 < 0.1159 的少数实体）
	 *  不加速（上限语义，不是拉平语义）。 */
	private static void applySpeedUniform(LivingEntity entity) {
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr == null) return;
		double base = speedAttr.getBaseValue();
		if (base <= 0.001) return; // 无移速属性实体（如静态 boss）跳过
		double target = SHEEP_MAX_SPEED_UNITS / 43.17; // 5 格/s 对应属性值 ≈ 0.1159
		if (base <= target) return; // 本来就慢于上限：不加速
		double factor = target / base - 1.0; // 任意基准 → 目标的等价乘区
		speedAttr.removeModifier(SPEED_MODIFIER_UUID);
		speedAttr.addTemporaryModifier(new EntityAttributeModifier(SPEED_MODIFIER_UUID,
				"SSCA Sheep Form Speed", factor, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	/** 恢复原属性（幂等）：移除修饰符 → 按变羊期间百分比 × 原上限重设当前血。 */
	private static void restore(LivingEntity entity) {
		Double originalMax = ORIGINAL_MAX_HEALTH.remove(entity.getUuid());
		// 变羊结束：恢复 mob 原生 Goal 集（initGoals 重造），再清「受惊逃离施法者」记录。
		// ⚠ 顺序不能反（2026-09-29 实测「变回来不恢复原 AI」根因）：clear 会先删 SWAPPED 表项，
		// restoreGoals 里按 SWAPPED.remove 判断是否执行 initGoals——先 clear 后 restore 恒为
		// false → initGoals 永不调用 → 羊 Goal 集永久残留（苦力怕不自爆/唤魔者不召唤）。
		SheepFormAiController.restoreGoals(entity);
		SheepFormAiController.clear(entity.getUuid());
		EntityAttributeInstance healthAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (healthAttr != null) healthAttr.removeModifier(HEALTH_MODIFIER_UUID);
		EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speedAttr != null) speedAttr.removeModifier(SPEED_MODIFIER_UUID);
		if (originalMax == null) return; // 没换过血（理论不可达）只清修饰符

		if (entity.isAlive()) {
			// 修饰符移除后 getMaxHealth 已回原值；按「变羊期间血量百分比」逆向换算当前血
			// （isAlive 已涵盖活体判定；羊上限按记录的原值重算，与变羊时同一公式）
			double sheepMax = originalMax != null ? sheepMaxHealthFor(originalMax) : SHEEP_BASE_MAX_HEALTH;
			double sheepRatio = sheepMax > 0 ? entity.getHealth() / sheepMax : 1.0;
			entity.setHealth((float) (entity.getMaxHealth() * Math.max(0.0, Math.min(1.0, sheepRatio))));
		}
	}

	/**
	 * 兜底扫描的唯一入口（挂 ServerTickEvents.END_SERVER_TICK，5t 节流）：
	 * 效果已不在但状态表仍有记录的实体 → 恢复。
	 * 覆盖：死亡清效果（不走 onRemoved）、换形态/指令强清状态、异常路径。
	 * 实体不在线（断线/区块卸载）：只清记录——修饰符 addTemporary 不进 NBT，
	 * 重进存档原版 clamp 回原属性，无残留。
	 */
	public static void tickSweep(net.minecraft.server.MinecraftServer server) {
		long now = server.getOverworld() != null ? server.getOverworld().getTime() : 0;
		if (now == lastSweep || Math.floorMod(now, 5) != 0) return;
		lastSweep = now;
		Iterator<Map.Entry<UUID, Double>> it = ORIGINAL_MAX_HEALTH.entrySet().iterator();
		while (it.hasNext()) {
			UUID id = it.next().getKey();
			LivingEntity found = null;
			for (ServerWorld world : server.getWorlds()) {
				var e = world.getEntity(id);
				if (e instanceof LivingEntity living && living.isAlive()) {
					found = living;
					break;
				}
			}
			if (found == null) {
				it.remove(); // 不在线：修饰符不进 NBT，重进自动回原属性
				// 同步清 AI 备份（2026-09-29「部分生物 AI 消失」根因之一）：mob 卸载时
				// Goal 集随实体消亡（Goal 不进 NBT），残留 BACKUPS 会让重载后（效果还在但
				// Goal 已是构造器新注册的原生集）被误判「已替换」跳过 swap / 或错误回放旧实例。
				SheepFormAiController.clearOffline(id);
				continue;
			}
			if (!found.hasStatusEffect(SscAddon.SHEEP_FORM)) {
				// 效果已无但记录还在（非正常路径清除）→ 立即恢复
				restore(found);
			} else if (found instanceof net.minecraft.entity.mob.MobEntity
					&& SheepFormAiController.hasStaleBackupFor(found)) {
				// 效果还在但备份实例已换（实体重载后 Goal 重建、效果残留的中间态）：
				// 重新执行 swap 到羊 Goal 集（用重载后的原生 Goal 重新备份）。
				// ⚠ 只在「实例已换」时触发——同实例正常变羊中备份恒在，恒不触发（2026-09-29
				// 实测「AI 消失」根因：旧写法只判 hasBackup，每 5t 丢弃重备，把正在运行的羊
				// Goal 集错存成备份，到期回放出羊 Goal 而非原生集）。
				SheepFormAiController.reswapAfterReload(found);
				// 重进场景修饰符已随实体重建消失（addTemporary 不进 NBT）——重挂血量/移速
				// 修饰符，数值恢复「标准羊」（2026-09-30 伴生修复）。
				reapplyModifiers(found);
			}
		}
	}
}
