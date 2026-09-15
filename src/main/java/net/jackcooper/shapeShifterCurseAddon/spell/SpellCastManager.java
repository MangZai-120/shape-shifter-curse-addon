package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * 月尘魔法书施法服务端核心（jackcooper）。服务端权威：验证佩戴魔法书、读书内卷轴、判冷却/法力、
 * 按卷轴耐久比缩放伤害与冷却（满次数=正常，用过越多越弱），执行魔法、写冷却、累积经验。
 */
public final class SpellCastManager {
	private SpellCastManager() {
	}

	/** 玩家当前佩戴的魔法书（未装备返回 null）。 */
	public static ItemStack getEquippedBook(ServerPlayerEntity player) {
		return TrinketUtils.findFirstEquipped(player, s -> s.getItem() == SscAddon.MOON_DUST_SPELLBOOK);
	}

	/** 释放书内指定槽的魔法。 */
	public static void cast(ServerPlayerEntity player, int slot) {
		if (net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager.cancelByKey(player)) return;
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty()) {
			return;
		}
		int count = SpellbookData.getSlotCount(book);
		if (slot < 0 || slot >= count) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		if (scroll.isEmpty()) {
			return;
		}
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return;
		}
		World world = player.getWorld();
		if (ScrollData.isOnCooldown(scroll, world)) {
			return;
		}
		int level = ScrollData.getLevel(scroll);              // 魔法等级（1-5，开箱固定）
		// 法阵加成：耗蓝倍率（全魔法每级 +10%）+ 形态亲和耗蓝乘区（使魔系 ×0.85）+ 每级耗蓝倍率
		// （耗蓝按卷轴原始等级算——召唤亲和 +1 级只加强施法效果，不推高耗蓝）
		int manaCost = Math.round(spell.getManaCost() * FormationData.sumManaCostMultiplier(book)
				* FormAffinity.manaCostMultiplier(player)
				* spell.getConfig().manaCostMultiplier(level));
		if (SpellbookData.getMana(book) < manaCost) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
			return;
		}

		// 施法前置校验（如陨火要求准星命中方块）：失败拒绝施法、不耗法力/CD（仿契灵传送失败不消耗）
		if (!spell.canCast(player)) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_target").formatted(Formatting.RED), true);
			return;
		}

		FormationElement spellElement = spell.getElement();
		float ratio = ScrollData.getDurabilityRatio(scroll);   // 1=满次数, 越低惩罚越大
		// 形态亲和等级加成（荧光幼灵/阿澪：召唤系 +1，上限 5）
		level = FormAffinity.bonusSpellLevel(player, spellElement, level);
		// 法阵加成：同系伤 +12%/级、对立系伤 -12%/级；同系 cd -5%/级；空间法阵只缩空间系 cd
		// 形态亲和：伤害/耗蓝/CD 三乘区（与法阵独立叠加）
		float damage = spell.getBaseDamage() * ratio * spell.getDamageMultiplier(level)
				* FormationData.sumDamageMultiplier(book, spellElement)
				* FormAffinity.damageMultiplier(player, spellElement);
		int cd = Math.round(spell.getBaseCooldownTicks() * (2.0f - ratio) * spell.getCooldownMultiplier(level)
				* FormationData.sumCooldownMultiplier(book, spellElement)
				* FormAffinity.cooldownMultiplier(player, spellElement));

		net.minecraft.nbt.NbtCompound previousNbt = scroll.getNbt() == null ? null : scroll.getNbt().copy();
		if (!spell.prepareScroll(player, scroll)) return;
		if (!java.util.Objects.equals(previousNbt, scroll.getNbt())) {
			SpellbookData.setScroll(book, slot, scroll);
		}
		SpellbookData.consumeMana(book, manaCost);
		// 经验机制（exp_mode，2026-09-15）——baseExp 按「实际耗蓝 × 有效技能等级」计算
		// （蓝色品质中位 6.0 exp/次），再乘经验法阵（通用系 exp 变体）倍率：每级 +10%（Lv5=×1.5）：
		//   0 = 释放即得全额；1 = 命中才得（释放时 0，全额挂起）；2 = 释放得 20%、命中补 80%。
		// 挂起部分经 Spell 桥在 cast 调用前装入：弹射物法术在 cast 内取走存进实体（NBT 持久化，
		// 命中结算时发放）；AOE 法术在 damage 成功后取走发放（首目标取全额、后续取 0，天然幂等）。
		int expMode = spell.getExpMode();
		int baseExpTen = manaCost * level;
		int bestExpFormation = FormationData.getBestUniversalVariantLevel(book, FormationData.VARIANT_EXP);
		if (bestExpFormation > 0) {
			baseExpTen = Math.round(baseExpTen * FormationData.universalExpMultiplier(bestExpFormation));
		}
		int pendingTen;
		switch (expMode) {
			case 1 -> pendingTen = baseExpTen;                // 命中才得：释放时全额挂起
			case 2 -> pendingTen = baseExpTen * 8 / 10;       // 释放 20% + 命中补 80%（整数截断）
			default -> pendingTen = 0;                        // 释放即得：无挂起
		}
		if (pendingTen > 0) {
			spell.ssc_addon$setPendingExp(pendingTen);
		}
		// 统一四参入口：法术内部自行决定是否按等级缩放速度/外观/范围（无 instanceof 特判）
		spell.cast(player, damage, false, level, scroll);
		spell.ssc_addon$clearPendingExp(); // 残留清理：法术未取走（如 AOE 全空放）则丢弃挂起部分
		SpellbookData.addExpTen(book, baseExpTen - pendingTen);
		long cooldownEnd = world.getTime() + cd;
		ScrollData.setCooldownEnd(scroll, cooldownEnd); // CD 跟卷轴走：换卷轴不继承同槽 CD
		if (!java.util.Objects.equals(previousNbt, scroll.getNbt())) {
			SpellbookData.setScroll(book, slot, scroll);
		}
		spell.onCooldownStarted(player, () -> {
			ItemStack current = SpellbookData.getScroll(book, slot);
			if (!current.isEmpty() && ScrollData.getCooldownEnd(current) == cooldownEnd) {
				ScrollData.setCooldownEnd(current,
						net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketChannelRules.refundCooldownEnd(
								world.getTime(), cooldownEnd, cd));
				SpellbookData.setScroll(book, slot, current);
			}
		});
	}

	/** 更新当前选中槽（存书 NBT，持久化 + 服务端一致）。 */
	public static void setSelected(ServerPlayerEntity player, int slot) {
		ItemStack book = getEquippedBook(player);
		if (book == null || book.isEmpty()) {
			return;
		}
		SpellbookData.setSelectedSlot(book, slot);
	}
}
