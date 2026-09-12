package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 伙伴共鸣伤害增益管理器（jackcooper，服务端权威）：给宠物/召唤物挂
 * 「+2×等级 攻击伤害」属性修饰符（固定 UUID，同目标重复施放覆盖层叠值），
 * 到期自动移除；目标死亡/卸载时随实体属性一并消亡，无残留。
 *
 * <p>固定 UUID 修饰符 + 到期 tick 清理——同 StunEffect 的孤儿修正兜底思路，
 * 崩服/断电极端情况下的残留由重复施放覆盖与实体重建自然消解。</p>
 */
public final class CompanionResonanceManager {
	/** 伤害增益修饰符固定 UUID（同一实体上幂等：重复施放覆盖数值）。 */
	public static final UUID DAMAGE_BONUS_UUID = UUID.fromString("8d2f6a34-1b5c-4e7a-9d3f-0a1b2c3d4e5f");

	private static final List<Entry> ACTIVE = new ArrayList<>();

	private static final class Entry {
		final LivingEntity target;
		int ticksRemaining;

		Entry(LivingEntity target, int durationTicks) {
			this.target = target;
			this.ticksRemaining = durationTicks;
		}
	}

	private CompanionResonanceManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Entry> it = ACTIVE.iterator();
			while (it.hasNext()) {
				Entry entry = it.next();
				if (!entry.target.isAlive() || entry.target.isRemoved()) {
					removeModifier(entry.target);
					it.remove();
					continue;
				}
				if (--entry.ticksRemaining <= 0) {
					removeModifier(entry.target);
					it.remove();
				}
			}
		});
	}

	/** 施加/刷新伤害增益（同目标旧计时作废，修饰符数值覆盖）。 */
	public static void apply(LivingEntity target, int level, int durationTicks) {
		// 固定 UUID：先移除旧层，避免多施法叠加超出设计
		removeModifier(target);
		ACTIVE.removeIf(e -> e.target == target);
		double amount = 2.0 * level;
		var attr = target.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (attr == null) {
			return; // 无攻击伤害属性的实体（部分被动生物）跳过
		}
		attr.addPersistentModifier(new EntityAttributeModifier(DAMAGE_BONUS_UUID,
				"ssc_addon.companion_resonance_damage", amount,
				EntityAttributeModifier.Operation.ADDITION));
		ACTIVE.add(new Entry(target, durationTicks));
	}

	private static void removeModifier(LivingEntity target) {
		var attr = target.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (attr != null && attr.getModifier(DAMAGE_BONUS_UUID) != null) {
			attr.removeModifier(DAMAGE_BONUS_UUID);
		}
	}
}
