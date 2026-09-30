package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

/**
 * 变羊期间的行为封锁（服务端权威兜底，jackcooper，2026-09-29）：
 * 「羊了个羊」中招实体无法放置方块、无法攻击/破坏。
 *
 * <p>三层防线：</p>
 * <ol>
 *   <li><b>属性层</b>：{@code SheepFormEffect} 挂攻击伤害 ×0（服务端结算，天然防作弊）；</li>
 *   <li><b>本类服务端兜底</b>：UseBlockCallback（禁放置——手持方块物品对方块/地面右键直接 FAIL）、
 *       AttackBlockCallback（禁破坏方块）、AttackEntityCallback（禁攻击实体——防×0 后仍触发
 *       副作用类武器/击退/仇恨）；</li>
 *   <li><b>客户端输入层</b>：{@code SheepFormInputMixin} 拦 doAttack/doItemUse/handleBlockBreaking，
 *       本机即时无回弹手感（同 STUN 的 StunnedInputMixin 模式）。</li>
 * </ol>
 *
 * <p>mob 侧 AI 停机不在此处：走 {@code SscAddonLivingEntityMixin.isImmobile} 拦截
 * （SHEEP_FORM 期间返回 true → 整体跳过 tickNewAi，怪物不再索敌/寻路/攻击）。</p>
 */
public final class SheepFormGuard {

	private SheepFormGuard() {}

	public static void init() {
		// 禁放置方块：手持方块物品右键方块/地面 → FAIL（仅对「手持方块物品」拦截，
		// 其它物品交互（开门/吃东西）也一并禁——羊蹄子按不了任何东西，语义更彻底且省特判）
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		// 禁破坏方块（挖掘起手就拦）
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		// 禁攻击实体（含生物与玩家；×0 伤害之外连击退/仇恨/特效武器副作用一并杜绝）
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}

	/** 空手/非方块物品是否仍可使用（客户端预检用；当前设计：变羊期间全部禁止，恒 false）。 */
	public static boolean canUseItem(LivingEntity entity, ItemStack stack) {
		return !entity.hasStatusEffect(SscAddon.SHEEP_FORM) && !(stack.getItem() instanceof BlockItem);
	}

	/** 装备槽位占位检查（预留：变羊期间不掉装备——装备槽只读，死亡掉落走原版）。 */
	public static boolean isSlotLocked(EquipmentSlot slot) {
		return slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
	}
}
