package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * 魔法卷轴的 NBT 数据读写工具（jackcooper）。卷轴 NBT：
 * <ul>
 *   <li>{@code Spell}（String）：魔法 id 的 path（命名空间恒 ssc_addon）；</li>
 *   <li>{@code Uses}（int）：单独使用剩余次数（放入魔法书后不消耗，仅决定书内效果的耐久缩放比）；</li>
 *   <li>{@code Cd}（long）：单独使用时的冷却结束世界时间（放书内不用，书内 cd 存在书 NBT）。</li>
 * </ul>
 */
public final class ScrollData {
	public static final String NBT_SPELL = "Spell";
	public static final String NBT_USES = "Uses";
	public static final String NBT_CD = "Cd";

	private ScrollData() {
	}

	/** 读取卷轴绑定的魔法（无绑定或未注册返回 null）。 */
	public static Spell getSpell(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_SPELL)) {
			return null;
		}
		return SpellRegistry.get(nbt.getString(NBT_SPELL));
	}

	/** 剩余单独使用次数（未初始化时按稀有度上限）。 */
	public static int getUses(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(NBT_USES)) {
			return nbt.getInt(NBT_USES);
		}
		Spell spell = getSpell(stack);
		return spell == null ? 0 : spell.getRarity().soloUses;
	}

	public static void setUses(ItemStack stack, int uses) {
		stack.getOrCreateNbt().putInt(NBT_USES, Math.max(0, uses));
	}

	/** 该卷轴稀有度的单独使用次数上限。 */
	public static int getMaxUses(ItemStack stack) {
		Spell spell = getSpell(stack);
		return spell == null ? 0 : spell.getRarity().soloUses;
	}

	/**
	 * 耐久比（0~1），用于装书内时的效果缩放：满次数=1（正常），用过则按剩余比例衰减。
	 * 红色（不可单独使用、上限 0）恒 1（书内满效果）。
	 */
	public static float getDurabilityRatio(ItemStack stack) {
		int max = getMaxUses(stack);
		if (max <= 0) {
			return 1.0f; // 红色恒满
		}
		int uses = Math.min(getUses(stack), max);
		return Math.max(0f, Math.min(1f, uses / (float) max));
	}

	/** 消耗 1 次单独使用次数，返回消耗后是否已耗尽（应销毁卷轴）。 */
	public static boolean consumeSoloUse(ItemStack stack) {
		int uses = getUses(stack) - 1;
		setUses(stack, uses);
		return uses <= 0;
	}

	// ---- 单独使用冷却（卷轴自身 NBT 时间戳，双端一致）----

	public static long getCooldownEnd(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return (nbt != null && nbt.contains(NBT_CD)) ? nbt.getLong(NBT_CD) : 0L;
	}

	public static void setCooldownEnd(ItemStack stack, long endTime) {
		stack.getOrCreateNbt().putLong(NBT_CD, endTime);
	}

	public static boolean isOnCooldown(ItemStack stack, World world) {
		return world.getTime() < getCooldownEnd(stack);
	}

	/** 是否为魔法卷轴（绑定了有效魔法）。 */
	public static boolean isScroll(ItemStack stack) {
		return getSpell(stack) != null;
	}

	/** 新建一张绑定指定魔法的卷轴（次数按稀有度上限）。 */
	public static ItemStack create(String spellPath) {
		ItemStack stack = new ItemStack(net.jackcooper.shapeShifterCurseAddon.SscAddon.MAGIC_SCROLL);
		Spell spell = SpellRegistry.get(spellPath);
		stack.getOrCreateNbt().putString(NBT_SPELL, spellPath);
		stack.getOrCreateNbt().putInt(NBT_USES, spell == null ? 0 : spell.getRarity().soloUses);
		return stack;
	}
}
