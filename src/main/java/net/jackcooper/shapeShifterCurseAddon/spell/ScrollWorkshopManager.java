package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 卷轴工坊服务端核心（jackcooper，阶段 C / 计划书 §8）：研究台第三页签的四类操作，
 * 全部 C2S 包触发、服务端权威重验。材料复用现有链路（纸槽/墨槽/尘槽/触媒槽）。
 *
 * <p><b>定向制作（craft）</b>：已记录图谱 + 纸×1 + 对应系油墨×等级 + 未加工月尘×等级 →
 * 产出 Lv1-2 卷轴（低门槛入门循环）；Lv3+ 走升级线（触媒门槛）。</p>
 *
 * <p><b>升级（upgrade）</b>（已拍板主方案：图谱 + 主卷轴 + 触媒）：产出槽放低于目标等级的
 * 同法术主卷轴 + 对应系油墨×目标等级 + 月尘纯晶（触媒）×(目标等级-2) → 主卷轴等级 +1
 * （最高 Lv5）。保留主卷轴 Uses/Cd/口袋空间绑定 NBT（只 bump Level；CastLevel 若超出新等级自动清除）。
 * 同级重复卷轴可抵月尘（每张抵 2，见 §8.3）——本版以触媒直购为主，抵扣后续按实机反馈加。</p>
 *
 * <p><b>修复（repair）</b>：产出槽放未满次数卷轴 + 对应系油墨×1 + 月尘×2 → Uses 回满。
 * 修复成本高于免费施法价值（月尘获取成本高于次数差对应的法力价值）。</p>
 *
 * <p><b>拆解（salvage）</b>：产出槽放任意卷轴 → 返还月尘×(等级/2 向上取整)。
 * 口袋空间卷轴拆解前提示绑定空间保留（不自动删除世界空间）。</p>
 */
public final class ScrollWorkshopManager {
	private ScrollWorkshopManager() {}

	/** 制作等级上限（Lv3+ 走升级线，防止绕过触媒门槛直接造高阶）。 */
	public static final int CRAFT_MAX_LEVEL = 2;

	/** 油墨需求数 = 目标等级（与法阵抄写同惯例）。 */
	private static int inkCost(int level) {
		return level;
	}

	/** 月尘需求数 = 等级（比法阵学习 2×/级 温和，卷轴是消耗品）。 */
	private static int dustCost(int level) {
		return level;
	}

	/** 触媒（月尘纯晶）需求数：Lv3=1、Lv4=2、Lv5=3（目标等级-2）。 */
	private static int catalystCost(int targetLevel) {
		return Math.max(0, targetLevel - 2);
	}

	/** 卷轴系别对应油墨类型（法术 element → 油墨 Type）。 */
	private static FormationInkItem.Type inkTypeOf(Spell spell) {
		FormationElement element = spell.getElement();
		if (element == null) {
			return FormationInkItem.Type.NORMAL;
		}
		for (FormationInkItem.Type t : FormationInkItem.Type.values()) {
			if (t.element == element) {
				return t;
			}
		}
		return FormationInkItem.Type.NORMAL;
	}

	/** 研究台上下文（打开的界面 + 方块实体；不在研究台前 = 静默失败）。 */
	private static SpellResearchTableBlockEntity context(ServerPlayerEntity player) {
		if (player.currentScreenHandler instanceof SpellResearchTableScreenHandler sh
				&& sh.getInventory() instanceof SpellResearchTableBlockEntity be) {
			return be;
		}
		return null;
	}

	/** 油墨槽是否为指定类型且数量足够。 */
	private static boolean inkMatches(ItemStack ink, FormationInkItem.Type type, int count) {
		return ink.getItem() instanceof FormationInkItem inkItem
				&& inkItem.getType() == type && ink.getCount() >= count;
	}

	// ==================== 定向制作 ====================

	/**
	 * 定向制作：产出 Lv1-2 卷轴。服务端重验：图谱已记录 + 纸 + 对应系墨×等级 + 尘×等级 + 产出槽空。
	 *
	 * @param spellPath 法术 id path（须已记录图谱）
	 * @param level     目标等级（1-2）
	 */
	public static void craft(ServerPlayerEntity player, String spellPath, int level) {
		SpellResearchTableBlockEntity be = context(player);
		Spell spell = SpellRegistry.get(spellPath);
		if (be == null || spell == null || level < 1 || level > CRAFT_MAX_LEVEL) {
			return;
		}
		FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
		if (!knowledge.hasSpell(spellPath)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.not_recorded",
					Text.translatable(spell.getNameKey())).formatted(Formatting.RED), true);
			return;
		}
		// 材料：纸×1（复用纸槽）+ 对应系墨×等级 + 尘×等级
		if (!(be.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).getItem()
				instanceof net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem)) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.no_paper").formatted(Formatting.RED), true);
			return;
		}
		FormationInkItem.Type inkType = inkTypeOf(spell);
		ItemStack ink = be.getStack(SpellResearchTableBlockEntity.SLOT_INK);
		if (!inkMatches(ink, inkType, inkCost(level))) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_ink",
					Text.translatable(inkType.element == null
							? "element.ssc_addon.universal" : inkType.element.getNameKey()),
					inkCost(level)).formatted(Formatting.RED), true);
			return;
		}
		ItemStack dust = be.getStack(SpellResearchTableBlockEntity.SLOT_MOONDUST);
		if (dust.getItem() != RegCustomItem.UNTREATED_MOONDUST || dust.getCount() < dustCost(level)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_dust", dustCost(level))
					.formatted(Formatting.RED), true);
			return;
		}
		if (!be.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT).isEmpty()) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.output_full").formatted(Formatting.RED), true);
			return;
		}
		// 扣料产出
		be.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).decrement(1);
		ink.decrement(inkCost(level));
		dust.decrement(dustCost(level));
		be.setStack(SpellResearchTableBlockEntity.SLOT_OUTPUT, ScrollData.create(spellPath, level));
		be.markDirty();
		player.getWorld().playSound(null, be.getPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1.0f, 0.8f);
		player.sendMessage(Text.translatable("message.ssc_addon.workshop.crafted",
				Text.translatable(spell.getNameKey()), level).formatted(Formatting.GREEN), true);
	}

	// ==================== 升级（图谱 + 主卷轴 + 触媒） ====================

	/**
	 * 升级主卷轴一级。服务端重验：产出槽放低于目标的同法术主卷轴 + 图谱已记录 +
	 * 对应系墨×目标等级 + 月尘纯晶×(目标-2)。保留主卷轴 NBT（Uses/Cd/绑定）。
	 */
	public static void upgrade(ServerPlayerEntity player, String spellPath, int targetLevel) {
		SpellResearchTableBlockEntity be = context(player);
		Spell spell = SpellRegistry.get(spellPath);
		if (be == null || spell == null || targetLevel < 2 || targetLevel > ScrollData.MAX_SPELL_LEVEL) {
			return;
		}
		FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
		if (!knowledge.hasSpell(spellPath)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.not_recorded",
					Text.translatable(spell.getNameKey())).formatted(Formatting.RED), true);
			return;
		}
		// 主卷轴：产出槽内同法术、等级 = 目标-1
		ItemStack main = be.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT);
		if (main.isEmpty() || !(main.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem)
				|| ScrollData.getSpell(main) != spell || ScrollData.getLevel(main) != targetLevel - 1) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_main_scroll",
					targetLevel - 1).formatted(Formatting.RED), true);
			return;
		}
		FormationInkItem.Type inkType = inkTypeOf(spell);
		ItemStack ink = be.getStack(SpellResearchTableBlockEntity.SLOT_INK);
		if (!inkMatches(ink, inkType, inkCost(targetLevel))) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_ink",
					Text.translatable(inkType.element == null
							? "element.ssc_addon.universal" : inkType.element.getNameKey()),
					inkCost(targetLevel)).formatted(Formatting.RED), true);
			return;
		}
		int need = catalystCost(targetLevel);
		ItemStack catalyst = be.getStack(SpellResearchTableBlockEntity.SLOT_CATALYST);
		if (need > 0 && (!catalyst.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD) || catalyst.getCount() < need)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_catalyst", need)
					.formatted(Formatting.RED), true);
			return;
		}
		ink.decrement(inkCost(targetLevel));
		catalyst.decrement(need);
		ScrollData.setLevel(main, targetLevel);
		ScrollData.setCastLevel(main, 0); // 升级后重置施放档位（档位≤新等级仍需玩家重设，简化语义）
		be.markDirty();
		player.getWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
				SoundCategory.BLOCKS, 1.0f, 1.2f);
		player.sendMessage(Text.translatable("message.ssc_addon.workshop.upgraded",
				Text.translatable(spell.getNameKey()), targetLevel).formatted(Formatting.GREEN), true);
	}

	// ==================== 修复 ====================

	/**
	 * 修复卷轴完整度：产出槽放未满次数卷轴 + 对应系墨×1 + 尘×2 → Uses 回满。
	 * 红档（maxUses=0）无完整度语义，不参与修复。
	 */
	public static void repair(ServerPlayerEntity player) {
		SpellResearchTableBlockEntity be = context(player);
		if (be == null) {
			return;
		}
		ItemStack scroll = be.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT);
		if (scroll.isEmpty() || ScrollData.getSpell(scroll) == null) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_scroll").formatted(Formatting.RED), true);
			return;
		}
		int max = ScrollData.getMaxUses(scroll);
		if (max <= 0) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.cannot_repair").formatted(Formatting.YELLOW), true);
			return;
		}
		int uses = ScrollData.getUses(scroll);
		if (uses >= max) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.already_full").formatted(Formatting.YELLOW), true);
			return;
		}
		Spell spell = ScrollData.getSpell(scroll);
		FormationInkItem.Type inkType = inkTypeOf(spell);
		ItemStack ink = be.getStack(SpellResearchTableBlockEntity.SLOT_INK);
		if (!inkMatches(ink, inkType, 1)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_ink",
					Text.translatable(inkType.element == null
							? "element.ssc_addon.universal" : inkType.element.getNameKey()), 1)
					.formatted(Formatting.RED), true);
			return;
		}
		ItemStack dust = be.getStack(SpellResearchTableBlockEntity.SLOT_MOONDUST);
		if (dust.getItem() != RegCustomItem.UNTREATED_MOONDUST || dust.getCount() < 2) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_dust", 2)
					.formatted(Formatting.RED), true);
			return;
		}
		ink.decrement(1);
		dust.decrement(2);
		ScrollData.setUses(scroll, max);
		be.markDirty();
		player.getWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
				SoundCategory.BLOCKS, 0.8f, 1.6f);
		player.sendMessage(Text.translatable("message.ssc_addon.workshop.repaired",
				Text.translatable(spell.getNameKey())).formatted(Formatting.GREEN), true);
	}

	// ==================== 拆解 ====================

	/**
	 * 拆解卷轴：返还月尘×ceil(等级/2)（约普通材料价值的 25%，§8.4「20-30%」区间内）。
	 * 不返毕业凭证（当前无凭证物品，触媒不返）。口袋空间卷轴给绑定保留提示。
	 */
	public static void salvage(ServerPlayerEntity player) {
		SpellResearchTableBlockEntity be = context(player);
		if (be == null) {
			return;
		}
		ItemStack scroll = be.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT);
		if (scroll.isEmpty() || ScrollData.getSpell(scroll) == null) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_scroll").formatted(Formatting.RED), true);
			return;
		}
		Spell spell = ScrollData.getSpell(scroll);
		int level = ScrollData.getLevel(scroll);
		// 口袋空间绑定提示（不阻止拆解；空间保留不自动删除）
		if (spell.getId().getPath().equals("pocket_space")) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.salvage_pocket_hint")
					.formatted(Formatting.YELLOW), false);
		}
		int refund = (level + 1) / 2;
		scroll.decrement(1);
		ItemStack dust = new ItemStack(RegCustomItem.UNTREATED_MOONDUST, refund);
		if (!player.getInventory().insertStack(dust)) {
			player.dropItem(dust, false);
		}
		be.markDirty();
		player.getWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_GRINDSTONE_USE,
				SoundCategory.BLOCKS, 1.0f, 0.8f);
		player.sendMessage(Text.translatable("message.ssc_addon.workshop.salvaged",
				Text.translatable(spell.getNameKey()), refund).formatted(Formatting.GREEN), true);
	}

}
