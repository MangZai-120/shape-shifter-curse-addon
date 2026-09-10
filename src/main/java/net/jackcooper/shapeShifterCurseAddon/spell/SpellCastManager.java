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
		if (SpellbookData.isOnCooldown(book, slot, world)) {
			return;
		}
		// 法阵加成：耗蓝倍率（全魔法每级 +10%）
		int manaCost = Math.round(spell.getManaCost() * FormationData.sumManaCostMultiplier(book));
		if (SpellbookData.getMana(book) < manaCost) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
			return;
		}

		boolean spellIsIce = spell.isIceSpell();
		float ratio = ScrollData.getDurabilityRatio(scroll);   // 1=满次数, 越低惩罚越大
		int level = ScrollData.getLevel(scroll);              // 魔法等级（1-5，开箱固定）
		// 法阵加成：同系伤 +12%/级、对立系伤 -12%/级；同系 cd -5%/级
		float damage = spell.getBaseDamage() * ratio * spell.getDamageMultiplier(level)
				* FormationData.sumDamageMultiplier(book, spellIsIce);
		int cd = Math.round(spell.getBaseCooldownTicks() * (2.0f - ratio) * spell.getCooldownMultiplier(level)
				* FormationData.sumCooldownMultiplier(book, spellIsIce));

		SpellbookData.consumeMana(book, manaCost);
		// 统一四参入口：法术内部自行决定是否按等级缩放速度/外观/范围（无 instanceof 特判）
		spell.cast(player, damage, false, level);
		SpellbookData.setCooldownEnd(book, slot, world.getTime() + cd);
		SpellbookData.addExp(book, 1);
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
