package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

/**
 * 月尘魔法书的 NBT 数据读写工具（jackcooper）。全部数据存魔法书 ItemStack 自身 NBT，
 * 随饰品同步、跨形态可用。字段：Level、Exp、Mana、Items(卷轴槽)、Cooldowns(各槽 cd 结束世界时间)、Selected(当前选中槽)。
 */
public final class SpellbookData {
	public static final int MAX_LEVEL = 3;
	/** 各等级卷轴槽数（index = level-1）。 */
	private static final int[] LEVEL_SLOTS = {3, 5, 7};
	/** 各等级法力上限。 */
	private static final int[] LEVEL_MAX_MANA = {100, 200, 300};
	/** 升到下一级所需经验（index = 当前 level-1；lv3 满级）。 */
	private static final int[] EXP_THRESHOLD = {30, 80};
	/** 最大卷轴槽数（= 魔法释放快捷键数量）。 */
	public static final int MAX_SLOTS = 7;

	public static final String NBT_LEVEL = "Level";
	public static final String NBT_EXP = "Exp";
	public static final String NBT_MANA = "Mana";
	public static final String NBT_ITEMS = "Items";
	public static final String NBT_COOLDOWNS = "Cooldowns";
	public static final String NBT_SELECTED = "Selected";

	private SpellbookData() {
	}

	public static int getLevel(ItemStack book) {
		NbtCompound nbt = book.getNbt();
		int lv = (nbt != null && nbt.contains(NBT_LEVEL)) ? nbt.getInt(NBT_LEVEL) : 1;
		return Math.max(1, Math.min(MAX_LEVEL, lv));
	}

	public static void setLevel(ItemStack book, int level) {
		book.getOrCreateNbt().putInt(NBT_LEVEL, Math.max(1, Math.min(MAX_LEVEL, level)));
	}

	public static int getSlotCount(ItemStack book) {
		return LEVEL_SLOTS[getLevel(book) - 1];
	}

	public static int getMaxMana(ItemStack book) {
		return LEVEL_MAX_MANA[getLevel(book) - 1];
	}

	public static int getMana(ItemStack book) {
		NbtCompound nbt = book.getNbt();
		// 新书（无 Mana 字段）默认满法力
		if (nbt == null || !nbt.contains(NBT_MANA)) {
			return getMaxMana(book);
		}
		return Math.max(0, Math.min(getMaxMana(book), nbt.getInt(NBT_MANA)));
	}

	public static void setMana(ItemStack book, int mana) {
		book.getOrCreateNbt().putInt(NBT_MANA, Math.max(0, Math.min(getMaxMana(book), mana)));
	}

	/** 尝试消耗法力，够则扣除返回 true。 */
	public static boolean consumeMana(ItemStack book, int cost) {
		int mana = getMana(book);
		if (mana < cost) {
			return false;
		}
		setMana(book, mana - cost);
		return true;
	}

	/** 充能（不超过上限）。返回实际增加量。 */
	public static int addMana(ItemStack book, int amount) {
		int before = getMana(book);
		int after = Math.min(getMaxMana(book), before + amount);
		setMana(book, after);
		return after - before;
	}

	public static int getExp(ItemStack book) {
		NbtCompound nbt = book.getNbt();
		return (nbt != null && nbt.contains(NBT_EXP)) ? nbt.getInt(NBT_EXP) : 0;
	}

	public static void setExp(ItemStack book, int exp) {
		book.getOrCreateNbt().putInt(NBT_EXP, Math.max(0, exp));
	}

	public static void addExp(ItemStack book, int amount) {
		setExp(book, getExp(book) + amount);
	}

	/** 升到下一级所需经验；满级返回 -1。 */
	public static int getExpToNext(ItemStack book) {
		int lv = getLevel(book);
		if (lv >= MAX_LEVEL) {
			return -1;
		}
		return EXP_THRESHOLD[lv - 1];
	}

	/** 是否已达到可升级条件（未满级且经验够）。 */
	public static boolean canLevelUp(ItemStack book) {
		int need = getExpToNext(book);
		return need > 0 && getExp(book) >= need;
	}

	public static int getSelectedSlot(ItemStack book) {
		NbtCompound nbt = book.getNbt();
		int sel = (nbt != null && nbt.contains(NBT_SELECTED)) ? nbt.getInt(NBT_SELECTED) : 0;
		int count = getSlotCount(book);
		if (count <= 0) {
			return 0;
		}
		return ((sel % count) + count) % count; // 环绕，防越界
	}

	public static void setSelectedSlot(ItemStack book, int slot) {
		int count = getSlotCount(book);
		int s = count <= 0 ? 0 : ((slot % count) + count) % count;
		book.getOrCreateNbt().putInt(NBT_SELECTED, s);
	}

	// ---- 卷轴槽读写（与 PotionBag 相同的 Items NbtList 结构）----

	/** 指定槽是否装有卷轴（非空即算）。 */
	public static boolean hasScroll(ItemStack book, int slot) {
		return !getScroll(book, slot).isEmpty();
	}

	/**
	 * 从 from 槽出发沿 dir 方向找下一个非空槽（只在已装备卷轴的槽间循环，跳过空槽）。
	 * <p>无任何卷轴返回 -1；from 自身非空且 dir 绕一圈无其它非空槽时返回 from（单技能自循环）。</p>
	 */
	public static int nextFilledSlot(ItemStack book, int from, int dir) {
		int count = getSlotCount(book);
		if (count <= 0) {
			return -1;
		}
		int cur = ((from % count) + count) % count;
		for (int i = 0; i < count; i++) {
			cur = ((cur + dir) % count + count) % count;
			if (hasScroll(book, cur)) {
				return cur;
			}
		}
		return -1; // 全空
	}

	/** 第一个非空槽（无任何卷轴返回 -1）。用于选中槽被取空后归位。 */
	public static int firstFilledSlot(ItemStack book) {
		int count = getSlotCount(book);
		for (int i = 0; i < count; i++) {
			if (hasScroll(book, i)) {
				return i;
			}
		}
		return -1;
	}

	public static ItemStack getScroll(ItemStack book, int slot) {
		NbtCompound nbt = book.getNbt();
		if (nbt == null || !nbt.contains(NBT_ITEMS, 9)) {
			return ItemStack.EMPTY;
		}
		NbtList list = nbt.getList(NBT_ITEMS, 10);
		for (int i = 0; i < list.size(); ++i) {
			NbtCompound tag = list.getCompound(i);
			if ((tag.getByte("Slot") & 255) == slot) {
				return ItemStack.fromNbt(tag);
			}
		}
		return ItemStack.EMPTY;
	}

	public static void setScroll(ItemStack book, int slot, ItemStack scroll) {
		NbtCompound nbt = book.getOrCreateNbt();
		NbtList list = nbt.contains(NBT_ITEMS, 9) ? nbt.getList(NBT_ITEMS, 10) : new NbtList();
		for (int i = list.size() - 1; i >= 0; --i) {
			if ((list.getCompound(i).getByte("Slot") & 255) == slot) {
				list.remove(i);
			}
		}
		if (!scroll.isEmpty()) {
			NbtCompound tag = new NbtCompound();
			tag.putByte("Slot", (byte) slot);
			scroll.writeNbt(tag);
			list.add(tag);
		}
		nbt.put(NBT_ITEMS, list);
	}

	// ---- 每槽冷却（世界时间戳，双端一致）----

	public static long getCooldownEnd(ItemStack book, int slot) {
		NbtCompound nbt = book.getNbt();
		if (nbt == null || !nbt.contains(NBT_COOLDOWNS)) {
			return 0L;
		}
		NbtCompound cds = nbt.getCompound(NBT_COOLDOWNS);
		String key = String.valueOf(slot);
		return cds.contains(key) ? cds.getLong(key) : 0L;
	}

	public static void setCooldownEnd(ItemStack book, int slot, long endTime) {
		NbtCompound nbt = book.getOrCreateNbt();
		NbtCompound cds = nbt.contains(NBT_COOLDOWNS) ? nbt.getCompound(NBT_COOLDOWNS) : new NbtCompound();
		cds.putLong(String.valueOf(slot), endTime);
		nbt.put(NBT_COOLDOWNS, cds);
	}

	public static boolean isOnCooldown(ItemStack book, int slot, World world) {
		return world.getTime() < getCooldownEnd(book, slot);
	}

	public static long getCooldownRemaining(ItemStack book, int slot, World world) {
		return Math.max(0L, getCooldownEnd(book, slot) - world.getTime());
	}
}
