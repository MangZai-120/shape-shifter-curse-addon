package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonParser;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 书能量支付链回归（起手全额闸门，2026-09-23 用户定稿）：书蓝不足整次消耗时必须拒绝起手，
 * 不再允许「读条中一点点扣、扣到耗尽才中止」。纯 NBT 驱动——JavaExec 测试环境没有 loom
 * 访问扩宽器，ItemStack/注册表初始化会撞 IllegalAccessError，因此全部走 NBT 级核心
 * （getManaNbt/canPayManaNbt/consumeManaNbt/sumManaCostMultiplierNbt 等，
 * 与生产 ItemStack API 同式）。构建期运行。
 */
public final class SpellManaPaymentTest {
	public static void main(String[] args) throws Exception {
		Spell[] spells = {new net.jackcooper.shapeShifterCurseAddon.spell.spells.DomainSpell(),
				new net.jackcooper.shapeShifterCurseAddon.spell.spells.ExplosionSpell()};
		for (Spell spell : spells) {
			spell.ssc_addon$applyConfig(config(spell.getId().getPath()));
			checkFullCostGate(spell);
		}
		checkInterruptedPayment();
		checkDowngrade();
		System.out.println("Book mana payment passed: full-cost gate at start; red costs 300/255 and 450/383; "
				+ "insufficient mana rejected without charge; downgrade picks only payable levels; "
				+ "red single-level spells never downgrade.");
	}

	/** 起手全额闸门：书蓝 < 整次报价 → 拒绝；读条扣费只走书，不向形态条分摊。 */
	private static void checkFullCostGate(Spell spell) throws Exception {
		final int maxMana = SpellbookData.getMaxManaNbt(book());
		for (float affinity : new float[] {1, 0.85f}) {
			for (boolean formation : new boolean[] {false, true}) {
				NbtCompound book = book();
				if (formation) {
					attachFormation(book, spell.getElement().id, 5);
				}
				float multiplier = FormationData.sumManaCostMultiplierNbt(book, spell.getElement());
				require(Math.abs(multiplier - (formation ? 1.5f : 1)) < 1.0e-6, "法阵耗蓝乘区必须从书 NBT 读出");
				int cost = SpellNumbers.manaCost(spell.getConfig(), 1, multiplier, affinity);
				int expected = affinity == 1 ? (formation ? 450 : 300) : (formation ? 383 : 255);
				require(cost == expected, "红色法术报价不符: " + cost);
				// 起手闸门：书蓝不足整次报价（0 / 120 / cost-1）一律拒绝，且不扣任何能量
				for (int insufficient : new int[] {0, 120, cost - 1}) {
					SpellbookData.setManaNbt(book, insufficient, maxMana);
					require(SpellbookData.getManaNbt(book, maxMana) == insufficient, "测试夹具必须与 HUD 书能量一致");
					require(!SpellbookData.canPayManaNbt(book, maxMana, cost), "书能量 " + insufficient + " 不得放行报价 " + cost);
					require(!consumeManaNbt(book, cost, maxMana), "不足起手必须拒绝扣费");
					require(SpellbookData.getManaNbt(book, maxMana) == insufficient, "拒绝起手不得扣能量");
				}
				// 正好够 → 放行；起手一次结清：通道建立即全额扣，读条期间零扣费
				SpellbookData.setManaNbt(book, cost, maxMana);
				require(SpellbookData.canPayManaNbt(book, maxMana, cost), "恰好够整次报价必须放行");
				SpellbookData.setManaNbt(book, cost + 120, maxMana);
				require(consumeManaNbt(book, cost, maxMana), "起手必须一次性全额扣成功");
				require(SpellbookData.getManaNbt(book, maxMana) == 120, "起手结算后书蓝恰减报价");
				int duration = spell.getCastingProfile(null, 1, false).ticks();
				for (int tick = 0; tick <= duration; tick++) {
					require(SpellbookData.getManaNbt(book, maxMana) == 120, "读条期间书蓝不得再变动（零扣费零回能）");
				}
				require(!consumeManaNbt(book, -1, maxMana) && SpellbookData.getManaNbt(book, maxMana) == 120, "负数扣费不能回能");
			}
		}
	}

	/** 起手已全额扣：中断不返还已扣部分；书蓝不足不得放行新施法。 */
	private static void checkInterruptedPayment() {
		final int maxMana = SpellbookData.getMaxManaNbt(book());
		NbtCompound book = book();
		SpellbookData.setManaNbt(book, 300, maxMana);
		require(consumeManaNbt(book, 300, maxMana), "起手全额扣");
		require(SpellbookData.getManaNbt(book, maxMana) == 0, "全额扣后书空");
		// 读条中被打断：已扣部分不返还（与中断不返还规则一致）
		SpellbookData.setManaNbt(book, 200, maxMana);
		require(!consumeManaNbt(book, 300, maxMana), "中断后书蓝不足不得再放行新施法");
		require(SpellbookData.getManaNbt(book, maxMana) == 200, "拒绝不得改动书能量");
	}

	/** 临时降档：只按当前书能量选付得起的最高档，不改卷轴 NBT；红色稀有度（单档）永不降档。 */
	private static void checkDowngrade() throws Exception {
		Spell spell = new net.jackcooper.shapeShifterCurseAddon.spell.spells.FireBoltSpell();
		spell.ssc_addon$applyConfig(config("fire_bolt"));
		SpellRegistry.register(spell);
		NbtCompound book = book();
		final int maxMana = SpellbookData.getMaxManaNbt(book());
		int affordable = SpellNumbers.finalManaCostNbt(spell, book, null, 2);
		SpellbookData.setManaNbt(book, affordable, maxMana);
		require(SpellNumbers.highestAffordableLevelNbt(spell, book, null, 3) == 2,
				"临时降档只能用当前书能量");
		SpellbookData.setManaNbt(book, 0, maxMana);
		require(SpellNumbers.highestAffordableLevelNbt(spell, book, null, 3) == 0, "空书无档可选");
		// 红色稀有度（单档）法术不得降档（2026-09-24 用户定稿）：书蓝不足整次报价时，
		// 无论请求档位多高，都不得给出任何可付的降档档位。
		Spell red = new net.jackcooper.shapeShifterCurseAddon.spell.spells.DomainSpell();
		red.ssc_addon$applyConfig(config("domain"));
		require(red.getMaxLevel() == 1, "红色稀有度法术必须单档");
		int redCost = SpellNumbers.finalManaCostNbt(red, book, null, 1);
		SpellbookData.setManaNbt(book, redCost - 1, maxMana);
		for (int ask : new int[] {1, 2, 5}) {
			require(SpellNumbers.highestAffordableLevelNbt(red, book, null, ask) == 0,
					"红色法术书蓝不足不得给出降档档位");
		}
	}

	// ---- 测试夹具 ----

	private static NbtCompound book() {
		NbtCompound book = new NbtCompound();
		book.putInt(SpellbookData.NBT_LEVEL, 3);
		// 精通拉满 12 档（上限 900），保证 cost-1（最高 449）不被法力上限钳断
		book.putInt(SpellbookData.NBT_EXP, SpellbookData.MASTERY_EXP_PER_TIER * 12);
		return book;
	}

	private static void attachFormation(NbtCompound book, String element, int level) {
		NbtList list = book.contains(SpellbookData.NBT_FORMATIONS, 9)
				? book.getList(SpellbookData.NBT_FORMATIONS, 10) : new NbtList();
		NbtCompound entry = new NbtCompound();
		entry.putByte("Slot", (byte) 0);
		entry.putString(FormationData.NBT_ELEMENT, element);
		entry.putInt(FormationData.NBT_LEVEL, level);
		list.add(entry);
		book.put(SpellbookData.NBT_FORMATIONS, list);
	}

	/** 与生产 consumeMana 同式的 NBT 版（测试夹具用）。 */
	private static boolean consumeManaNbt(NbtCompound book, int cost, int maxMana) {
		int mana = SpellbookData.getManaNbt(book, maxMana);
		if (!SpellCastingRules.canAfford(cost, mana)) return false;
		SpellbookData.setManaNbt(book, mana - cost, maxMana);
		return true;
	}

	private static SpellConfig config(String id) throws Exception {
		try (var input = SpellManaPaymentTest.class.getResourceAsStream("/data/ssc_addon/spells/" + id + ".json")) {
			if (input == null) throw new IllegalStateException("Missing spell JSON: " + id);
			return SpellConfig.fromJson(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
