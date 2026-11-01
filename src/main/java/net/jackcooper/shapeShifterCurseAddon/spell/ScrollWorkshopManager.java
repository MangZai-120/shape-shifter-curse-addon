package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.block.InfusionAltarBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/** Scroll maintenance belongs to the preparation table; production and upgrading require runes. */
public final class ScrollWorkshopManager {
	private ScrollWorkshopManager() {}

	// 平衡迁移（systems.workshop）：工坊成本/上限运行时从快照读取；快照未初始化回退默认常量
	private static final BalanceReader BAL = new BalanceReader("systems.workshop");

	/** 制作等级上限（Lv3+ 走升级线，防止绕过触媒门槛直接造高阶）；默认值，balance 可调。 */
	public static final int CRAFT_MAX_LEVEL = 2;

	/** 油墨需求数 = 每级油墨系数 × 目标等级（与法阵抄写同惯例；默认 1/级）。 */
	public static int upgradeInkCost(int level) {
		return (int) Math.round(BAL.d("ink_per_level", 1.0) * level);
	}

	/** 触媒（月尘纯晶）需求数：目标等级 - 偏移（默认 2，即 Lv3=1、Lv4=2、Lv5=3）。 */
	public static int upgradeCatalystCost(int targetLevel) {
		return Math.max(0, targetLevel - BAL.i("catalyst_offset", 2));
	}
    public static int repairInkCost() { return BAL.i("repair_ink",1); }
    public static int repairDustCost() { return BAL.i("repair_dust",2); }

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
	private static InfusionAltarBlockEntity context(ServerPlayerEntity player) {
		if (player.currentScreenHandler instanceof InfusionAltarScreenHandler sh
				&& sh.isMaintenance() && sh.canUse(player) && sh.getInventory() instanceof InfusionAltarBlockEntity be) {
			return be;
		}
		return null;
	}

	/** 油墨槽是否为指定类型且数量足够。 */
	private static boolean inkMatches(ItemStack ink, FormationInkItem.Type type, int count) {
		return ink.getItem() instanceof FormationInkItem inkItem
				&& inkItem.getType() == type && ink.getCount() >= count;
	}

    /** Retired packet entrypoints remain inert for older clients. */
    public static void craft(ServerPlayerEntity player, String spellPath, int level) { requireRunes(player); }
    public static void upgrade(ServerPlayerEntity player, String spellPath, int level) { requireRunes(player); }
    private static void requireRunes(ServerPlayerEntity player) {
        if (player != null) player.sendMessage(Text.translatable("research.ssc_addon.slotted.manual_required"), true);
    }

	// ==================== 修复 ====================

	/**
	 * 修复卷轴完整度：产出槽放未满次数卷轴 + 对应系墨×1 + 尘×2 → Uses 回满。
	 * 红档（maxUses=0）无完整度语义，不参与修复。
	 */
	public static void repair(ServerPlayerEntity player) {
		InfusionAltarBlockEntity be = context(player);
		if (be == null) {
			return;
		}
		ItemStack scroll = be.getStack(InfusionAltarScreenHandler.MAINTENANCE_SCROLL);
		if (ArcaneAnalysis.isUnanalyzed(scroll)) {
			player.sendMessage(Text.translatable("message.ssc_addon.analysis.required").formatted(Formatting.RED), true);
			return;
		}
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
		int repairInk = repairInkCost();
		ItemStack ink = be.getStack(InfusionAltarScreenHandler.MAINTENANCE_INK);
		if (!inkMatches(ink, inkType, repairInk)) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_ink",
					Text.translatable(inkType.element == null
							? "element.ssc_addon.universal" : inkType.element.getNameKey()), repairInk)
					.formatted(Formatting.RED), true);
			return;
		}
		int repairDust = repairDustCost();
		ItemStack dust = be.getStack(1);
		if (dust.getItem() != RegCustomItem.UNTREATED_MOONDUST || dust.getCount() < repairDust) {
			player.sendMessage(Text.translatable("message.ssc_addon.workshop.no_dust", repairDust)
					.formatted(Formatting.RED), true);
			return;
		}
		ink.decrement(repairInk);
		dust.decrement(repairDust);
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
		InfusionAltarBlockEntity be = context(player);
		if (be == null) {
			return;
		}
		ItemStack scroll = be.getStack(InfusionAltarScreenHandler.MAINTENANCE_SCROLL);
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
		int divisor = Math.max(1, BAL.i("salvage_divisor", 2));
		int refund = (level + divisor - 1) / divisor;
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

    public static void imprint(ServerPlayerEntity player){
        var table=context(player);if(table==null)return;
        int result=net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme.imprint(table,net.jackcooper.shapeShifterCurseAddon.spell.research.WorldRuneState.get(player.getServer()),InfusionAltarScreenHandler.MAINTENANCE_SCHEME,InfusionAltarScreenHandler.MAINTENANCE_SCROLL);
        player.sendMessage(Text.translatable("research.ssc_addon.runes.imprint."+result),true);
        if(result==0){player.currentScreenHandler.sendContentUpdates();player.getWorld().playSound(null,table.getPos(),SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.BLOCKS,.8f,1.1f);}
    }
}
