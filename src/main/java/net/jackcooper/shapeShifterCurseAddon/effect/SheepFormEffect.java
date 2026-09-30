package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 变羊状态（召唤系法术「羊了个羊 Beep Sheep」，jackcooper，2026-09-29 用户定稿）：
 * 中招实体被「变成一只羊」——客户端由 SheepFormClientHooks 用原版羊模型替换其渲染
 * （效果经原版 DataTracker 自动同步，无需自建网络）；期间实体<b>失去战斗能力</b>：
 * <ul>
 *   <li>攻击伤害 ×0（属性修正）+ 玩家端禁攻击/禁用物品/禁破坏输入（客户端 mixin）；</li>
 *   <li>服务端 UseBlockCallback/AttackBlockCallback 兜底禁止放置与破坏方块（防假客户端）；</li>
 *   <li>mob 侧 AI 停机（isImmobile → 整体跳过 tickNewAi，同 STUN 拦截模式）。</li>
 * </ul>
 *
 * <p><b>三项实体属性换血（2026-09-29 用户定稿）</b>——变羊期间目标临时成为「标准羊」：
 * 移速统一 0.23（羊基础值，快慢都拉平）、最大生命 8 心（16.0，按变羊瞬间血量百分比
 * 换算当前血）；效果结束按变羊期间血量百分比逆向还原。换血与恢复的完整状态机在
 * {@link SheepFormRestoreHandler}（按实体 UUID 记录，正常到期走 onRemoved 恢复，
 * 死亡/断线/清效果等非正常路径由 sweep 兜底）。</p>
 */
public class SheepFormEffect extends StatusEffect {
	// 固定 UUID：攻击力归零（同 StunEffect 命名规范，供孤儿修正清理按 UUID 精确移除）
	public static final java.util.UUID ATTACK_MODIFIER_UUID =
			java.util.UUID.fromString("A51E7C2D-9B34-4F5E-8D6A-3C0B7E1F9A24");

	public SheepFormEffect() {
		super(StatusEffectCategory.HARMFUL, 0xE8D8C0);
		// 攻击伤害 -100%（变羊后无法造成任何攻击伤害；击退仍存在，符合「羊乱撞」的直觉）。
		// 移速/血量上限不走 addAttributeModifier（需要动态系数与换血顺序控制），由 Handler 管理。
		this.addAttributeModifier(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,
				ATTACK_MODIFIER_UUID.toString(), -1.0, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		// mob 侧「羊 AI」（2026-09-29 用户定稿）：变羊期间 AI 暂时变成羊——清仇恨 +
		// 受惊远离施法者（SheepFormAiController 内 10t 节流寻路 + isImmobile 已改为不冻结 mob）。
		if (!entity.getWorld().isClient) {
			SheepFormAiController.tickSheepAi(entity);
		}
		// 变羊期间随机咩叫（演出）：约每 6~9 秒一声，客户端听到的是服务端广播的实体音
		if (!entity.getWorld().isClient && entity.age % 140 == 0 && entity.getRandom().nextFloat() < 0.5f) {
			entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					net.minecraft.sound.SoundEvents.ENTITY_SHEEP_AMBIENT,
					net.minecraft.sound.SoundCategory.NEUTRAL, 0.9f,
					0.9f + entity.getRandom().nextFloat() * 0.2f);
		}
	}

	@Override
	public void onApplied(LivingEntity entity, net.minecraft.entity.attribute.AttributeContainer attributes, int amplifier) {
		super.onApplied(entity, attributes, amplifier);
		if (entity.getWorld().isClient) return;
		SheepFormRestoreHandler.onSheepStart(entity);
		// 玩家：切空壳羊形态（技能/被动/魔法天然失效，2026-09-29 用户定稿；mob 维持效果路线）
		if (entity instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			net.jackcooper.shapeShifterCurseAddon.ability.BeepSheepFormManager.onSheepStart(serverPlayer);
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, net.minecraft.entity.attribute.AttributeContainer attributes, int amplifier) {
		super.onRemoved(entity, attributes, amplifier);
		if (entity.getWorld().isClient) return;
		SheepFormRestoreHandler.onSheepEnd(entity);
		// 玩家：切回原形态 + 回写 Apoli CD 快照（CD 正常续走不重算，用户定稿）
		if (entity instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			net.jackcooper.shapeShifterCurseAddon.ability.BeepSheepFormManager.onSheepEnd(serverPlayer);
		}
	}
}
