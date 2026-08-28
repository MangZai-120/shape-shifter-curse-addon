package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.SkillBlocker;

import java.util.UUID;

/**
 * 野猫系「夜行者」昼夜移速被动（代码实现，替代原 wild_cat_sp_speed_passive power JSON）。
 *
 * <p>夜间（13000~23000）移速 +20%（速度 I 等效）、白天移速 -15%（缓慢 I 等效）。
 * 用<b>持久属性修饰符</b>而非周期施加的药水效果实现：原 apoli action_over_time 每 20t
 * 重新施加缓慢/速度药水，效果到期→属性移除→再施加的循环会让 FOV（视角随移速缩放）
 * 周期性来回弹动；持久修饰符只在昼夜/形态/禁用状态<b>变化时</b>切换一次，FOV 全程稳定。</p>
 *
 * <p>技能禁用联动：沿用 SkillBlocker 的 "wild_cat:night_speed" / "wild_cat:day_slow"
 * 标签与服务器配置，禁用对应技能即摘除修饰符（与原 JSON 的 ssc_addon:skill_disabled 条件等价）。
 * 作用于野猫 SP（{@code wild_cat_sp}）与食梦魔（{@code wild_cat_nightmare}）两形态。</p>
 */
public final class WildCatPaceManager {
	/** 固定 UUID：状态切换时先移除再按新值添加，防重复堆积。 */
	private static final UUID PACE_MODIFIER_UUID = UUID.fromString("5f6a2e8c-1b3d-4c7e-9a0f-8e2d1c4b6a3c");
	private static final String MODIFIER_NAME = "Wild Cat Night Walker";
	/** 白天缓慢 I 等效（vanilla slowness amplifier 0 = -15% 移速）。 */
	private static final double DAY_SLOW = -0.15;
	/** 夜间速度 I 等效（vanilla speed amplifier 0 = +20% 移速）。 */
	private static final double NIGHT_SPEED = 0.20;
	/** 检查粒度（tick）：与原 JSON interval 20 一致，昼夜切换响应延迟最多 1 秒。 */
	private static final int CHECK_INTERVAL = 20;

	private WildCatPaceManager() {
	}

	/** 每服务端 tick 对每个在线玩家调用（内部按 CHECK_INTERVAL 降频）。 */
	public static void tick(ServerPlayerEntity player) {
		if (player.getWorld().getTime() % CHECK_INTERVAL != 0) return;

		Double target = targetValue(player);
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr == null) return;

		EntityAttributeModifier current = attr.getModifier(PACE_MODIFIER_UUID);
		if (target == null) {
			// 非野猫形态 / 技能禁用：摘除后返回
			if (current != null) attr.removeModifier(PACE_MODIFIER_UUID);
			return;
		}
		// 状态未变不动修饰符 —— 每 20t 无脑 remove+add 会复现 FOV 周期弹动，必须跳过
		if (current != null && Math.abs(current.getValue() - target) < 1.0e-9) return;
		if (current != null) attr.removeModifier(PACE_MODIFIER_UUID);
		attr.addTemporaryModifier(new EntityAttributeModifier(
				PACE_MODIFIER_UUID, MODIFIER_NAME, target, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	/**
	 * 计算当前应挂的修饰符值；null = 不挂。
	 * <p>昼夜判定与原 JSON 的 apoli:time_of_day 完全对齐：夜间 [13000, 23000]（含边界），
	 * 其余为白天。技能禁用各自独立判定。</p>
	 */
	private static Double targetValue(ServerPlayerEntity player) {
		boolean isWildCat = FormUtils.isForm(player, FormIdentifiers.WILD_CAT_SP)
				|| FormUtils.isForm(player, FormIdentifiers.WILD_CAT_NIGHTMARE);
		if (!isWildCat) return null;

		long timeOfDay = player.getWorld().getTimeOfDay() % 24000L;
		boolean night = timeOfDay >= 13000L && timeOfDay <= 23000L;
		if (night) {
			return SkillBlocker.isSkillBlocked(player, "wild_cat", "night_speed") ? null : NIGHT_SPEED;
		}
		return SkillBlocker.isSkillBlocked(player, "wild_cat", "day_slow") ? null : DAY_SLOW;
	}

	/** 断线兜底清理（temporary modifier 不进 NBT，重连由 tick 重新挂上）。 */
	public static void clearPlayer(ServerPlayerEntity player) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr != null) attr.removeModifier(PACE_MODIFIER_UUID);
	}
}
