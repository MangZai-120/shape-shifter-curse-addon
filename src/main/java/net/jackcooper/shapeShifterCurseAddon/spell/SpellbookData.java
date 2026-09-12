package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

/**
 * 月尘魔法书的 NBT 数据读写工具（jackcooper）。全部数据存魔法书 ItemStack 自身 NBT，
 * 随饰品同步、跨形态可用。字段：Level、Exp（ExpTen）、Mana、Items(卷轴槽)、Cooldowns(各槽 cd 结束世界时间)、Selected(当前选中槽)。
 *
 * <p><b>经验系统（2026-09-12 重做）</b>：施法按「实际耗蓝 × 有效技能等级 ÷ 10」获得经验
 * （见 {@link SpellCastManager}），精确到小数点后 1 位。为杜绝浮点漂移，NBT 内部以
 * <b>×10 的整数</b>存储（{@code ExpTen}：6.0 exp = 60）；旧档 {@code Exp}（int）读档时自动迁移（×10）。
 * 升级阈值 600.0 / 900.0（蓝色品质中位法术约 100 次升一级）。</p>
 *
 * <p><b>满级成长</b>：Lv3 满级后经验继续累积，每满 {@link #MASTERY_EXP_PER_TIER}（600.0）
 * 提升一档书法力上限 {@link #MASTERY_MANA_PER_TIER}（+50），封顶 {@link #MASTERY_MAX_BONUS}（+600，
 * 即满级满档 300+600=900）。档位不落级：经验只增不减（升级清零只发生在 Lv1→2 / Lv2→3）。</p>
 */
public final class SpellbookData {
	public static final int MAX_LEVEL = 3;
	/** 各等级卷轴槽数（index = level-1）。 */
	private static final int[] LEVEL_SLOTS = {3, 5, 7};
	/** 各等级基础法力上限（未含满级精通档加成）。 */
	private static final int[] LEVEL_MAX_MANA = {100, 200, 300};
	/** 升到下一级所需经验（index = 当前 level-1；lv3 满级）。经验 ×10 整数存储：600.0 / 900.0。 */
	private static final int[] EXP_THRESHOLD = {600 * 10, 900 * 10};
	/** 最大卷轴槽数（= 魔法释放快捷键数量）。 */
	public static final int MAX_SLOTS = 7;

	/** 满级后每提升一档法力上限所需经验（×10 整数 = 600.0 exp）。 */
	public static final int MASTERY_EXP_PER_TIER = 600 * 10;
	/** 满级后每档增加的法力上限。 */
	public static final int MASTERY_MANA_PER_TIER = 50;
	/** 满级法力上限加成封顶（档数 = 600*10/50 → 12 档 ×50 = +600）。 */
	public static final int MASTERY_MAX_BONUS = 600;

	public static final String NBT_LEVEL = "Level";
	/** 经验 ×10 整数（6.0 exp = 60）；旧键 {@code Exp} 读档自动迁移。 */
	public static final String NBT_EXP = "ExpTen";
	public static final String NBT_MANA = "Mana";
	public static final String NBT_ITEMS = "Items";
	public static final String NBT_COOLDOWNS = "Cooldowns";
	public static final String NBT_SELECTED = "Selected";
	/** 增强法阵列表（NbtList，结构同 Items：{Slot:byte, ...stack}）。 */
	public static final String NBT_FORMATIONS = "Formations";

	/** 五角星法阵槽最大数（一级书 1 / 二级 3 / 三级 5）。 */
	public static final int MAX_FORMATION_SLOTS = 5;

	/** 各等级可装备法阵数（index = level-1）。 */
	private static final int[] LEVEL_FORMATION_SLOTS = {1, 3, 5};

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

	/** 当前书等级可装备的法阵数（注魔台五角星解锁角数）。 */
	public static int getFormationSlotCount(ItemStack book) {
		return LEVEL_FORMATION_SLOTS[getLevel(book) - 1];
	}

	/** 指定法阵槽（五角星角位）是否已解锁。 */
	public static boolean isFormationSlotUnlocked(ItemStack book, int slot) {
		return slot >= 0 && slot < getFormationSlotCount(book);
	}

	public static int getMaxMana(ItemStack book) {
		return LEVEL_MAX_MANA[getLevel(book) - 1] + getMasteryManaBonus(book);
	}

	/** 满级精通档位（第几档，0 = 未满档）。每 {@link #MASTERY_EXP_PER_TIER} 经验一档，封顶不超上限加成。 */
	public static int getMasteryTier(ItemStack book) {
		if (getLevel(book) < MAX_LEVEL) {
			return 0;
		}
		int maxTier = MASTERY_MAX_BONUS / MASTERY_MANA_PER_TIER;
		return Math.min(maxTier, getExpTen(book) / MASTERY_EXP_PER_TIER);
	}

	/** 满级精通带来的法力上限加成（+50/档，封顶 +600）。 */
	public static int getMasteryManaBonus(ItemStack book) {
		return Math.min(MASTERY_MAX_BONUS, getMasteryTier(book) * MASTERY_MANA_PER_TIER);
	}

	/** 距下一档精通还差多少经验（×10 整数；已满档或未满级返回 -1）。 */
	public static int getMasteryExpToNextTier(ItemStack book) {
		if (getLevel(book) < MAX_LEVEL || getMasteryTier(book) >= MASTERY_MAX_BONUS / MASTERY_MANA_PER_TIER) {
			return -1;
		}
		return MASTERY_EXP_PER_TIER - getExpTen(book) % MASTERY_EXP_PER_TIER;
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

	public static int getExpTen(ItemStack book) {
		NbtCompound nbt = book.getNbt();
		if (nbt != null && nbt.contains(NBT_EXP)) {
			return Math.max(0, nbt.getInt(NBT_EXP));
		}
		// 旧档迁移：旧 Exp 为 int（每次施法 +1），×10 折算成新精度并移除旧键
		if (nbt != null && nbt.contains("Exp")) {
			int legacy = Math.max(0, nbt.getInt("Exp"));
			nbt.remove("Exp");
			nbt.putInt(NBT_EXP, legacy * 10);
			return legacy * 10;
		}
		return 0;
	}

	/** 当前经验（浮点，精确到 0.1）。 */
	public static float getExpFloat(ItemStack book) {
		return getExpTen(book) / 10.0f;
	}

	/** 写入经验（×10 整数内部值）。 */
	public static void setExpTen(ItemStack book, int expTen) {
		NbtCompound nbt = book.getOrCreateNbt();
		nbt.remove("Exp"); // 顺带清旧键，防脏读回退
		nbt.putInt(NBT_EXP, Math.max(0, expTen));
	}

	/** 累积经验（×10 整数增量，如 6.0 exp 传 60）。 */
	public static void addExpTen(ItemStack book, int amountTen) {
		setExpTen(book, getExpTen(book) + amountTen);
	}

	/** 升到下一级所需经验（×10 整数）；满级返回 -1。 */
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
		return need > 0 && getExpTen(book) >= need;
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

	// ---- 增强法阵槽读写（五角星，与 Items 同构的 NbtList） ----

	/** 读取书内全部法阵（跳过无效条目；无数据返回空列表）。 */
	public static java.util.List<ItemStack> getFormations(ItemStack book) {
		java.util.List<ItemStack> result = new java.util.ArrayList<>();
		NbtCompound nbt = book.getNbt();
		if (nbt == null || !nbt.contains(NBT_FORMATIONS, 9)) {
			return result;
		}
		NbtList list = nbt.getList(NBT_FORMATIONS, 10);
		for (int i = 0; i < list.size(); ++i) {
			ItemStack stack = ItemStack.fromNbt(list.getCompound(i));
			if (!stack.isEmpty()) {
				result.add(stack);
			}
		}
		return result;
	}

	/** 读取指定法阵槽（空返回 EMPTY）。 */
	public static ItemStack getFormation(ItemStack book, int slot) {
		NbtCompound nbt = book.getNbt();
		if (nbt == null || !nbt.contains(NBT_FORMATIONS, 9)) {
			return ItemStack.EMPTY;
		}
		NbtList list = nbt.getList(NBT_FORMATIONS, 10);
		for (int i = 0; i < list.size(); ++i) {
			NbtCompound tag = list.getCompound(i);
			if ((tag.getByte("Slot") & 255) == slot) {
				return ItemStack.fromNbt(tag);
			}
		}
		return ItemStack.EMPTY;
	}

	/** 写入指定法阵槽（空 stack = 移除该槽）。 */
	public static void setFormation(ItemStack book, int slot, ItemStack formation) {
		NbtCompound nbt = book.getOrCreateNbt();
		NbtList list = nbt.contains(NBT_FORMATIONS, 9) ? nbt.getList(NBT_FORMATIONS, 10) : new NbtList();
		for (int i = list.size() - 1; i >= 0; --i) {
			if ((list.getCompound(i).getByte("Slot") & 255) == slot) {
				list.remove(i);
			}
		}
		if (!formation.isEmpty()) {
			NbtCompound tag = new NbtCompound();
			tag.putByte("Slot", (byte) slot);
			formation.writeNbt(tag);
			list.add(tag);
		}
		nbt.put(NBT_FORMATIONS, list);
	}
}
