package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonParser;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 书能量支付链回归：起手仍要求付得起完整消耗，施法期间每 2 tick 线性扣费。
 * 纯 NBT 驱动——JavaExec 测试环境没有 loom
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
		checkPaymentCadenceAndRemainders();
		checkDowngrade();
		checkFormationNbtFormats();
		System.out.println("Book mana payment passed: full-cost gate at start; 2-tick progressive deduction and exact remainders; red costs 300/255 and 450/383; "
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
				// 正好够 → 放行；报价锁定，扣费随施法进度推进，完成时精确结清。
				SpellbookData.setManaNbt(book, cost, maxMana);
				require(SpellbookData.canPayManaNbt(book, maxMana, cost), "恰好够整次报价必须放行");
				SpellbookData.setManaNbt(book, cost + 120, maxMana);
				int duration = spell.getCastingProfile(null, 1, false).ticks();
				SpellManaPayment payment = new SpellManaPayment(cost, duration);
				for (int tick = 0; tick <= duration; tick++) {
					require(payment.advance(tick, due -> consumeManaNbt(book, due, maxMana)), "分段支付不得失败");
					int billedTick = tick == duration ? duration : tick / 2 * 2;
					int expectedPaid = SpellCastingRules.cumulativeMana(cost, billedTick, duration);
					require(SpellbookData.getManaNbt(book, maxMana) == cost + 120 - expectedPaid,
							"施法只能每 2 tick 按进度扣费，不能起手全扣或重复扣费");
				}
				require(payment.paid() == cost && SpellbookData.getManaNbt(book, maxMana) == 120, "完成时恰好扣完整报价");
				require(!consumeManaNbt(book, -1, maxMana) && SpellbookData.getManaNbt(book, maxMana) == 120, "负数扣费不能回能");
			}
		}
	}

	/** 中断只保留已经支付的部分；未施放部分不继续扣，报价不足仍不能起手。 */
	private static void checkInterruptedPayment() {
		final int maxMana = SpellbookData.getMaxManaNbt(book());
		NbtCompound book = book();
		SpellbookData.setManaNbt(book, 300, maxMana);
		SpellManaPayment payment = new SpellManaPayment(300, 100);
		for (int tick = 0; tick <= 40; tick++) {
			require(payment.advance(tick, due -> consumeManaNbt(book, due, maxMana)), "中断前分段支付成功");
		}
		require(payment.paid() == 120 && SpellbookData.getManaNbt(book, maxMana) == 180, "40% 进度只扣 40% 报价");
		require(!SpellbookData.canPayManaNbt(book, maxMana, 300), "中断后书蓝不足不得放行新施法");
		require(SpellbookData.getManaNbt(book, maxMana) == 180, "检查新施法不得改动剩余蓝量");
	}

	private static void checkPaymentCadenceAndRemainders() {
		for (int cost : new int[]{0, 1, 7, 100, 383, 450}) {
			for (int duration : new int[]{0, 1, 3, 8, 30, 100, 101, 260, 700}) {
				NbtCompound book = book();
				int maxMana = SpellbookData.getMaxManaNbt(book);
				SpellbookData.setManaNbt(book, cost, maxMana);
				SpellManaPayment payment = new SpellManaPayment(cost, duration);
				for (int tick = 0; tick <= duration + 4; tick++) {
					int before = SpellbookData.getManaNbt(book, maxMana);
					require(payment.advance(tick, due -> consumeManaNbt(book, due, maxMana)), "所有取整组合都应付清");
					int after = SpellbookData.getManaNbt(book, maxMana);
					if (tick < duration && tick % 2 != 0) require(after == before, "奇数 tick 不扣费");
					require(payment.advance(tick, due -> { throw new AssertionError("同 tick 重入重复扣费"); }), "同 tick 支付幂等");
					if (cost == 100 && duration == 100) require(after == 100 - Math.min(tick / 2 * 2, 100), "5秒100蓝：每2tick恰扣2蓝");
				}
				require(payment.paid() == cost && SpellbookData.getManaNbt(book, maxMana) == 0, "完成与松手等待都不多扣或少扣");
			}
		}
		SpellManaPayment failed = new SpellManaPayment(100, 100);
		require(!failed.advance(2, due -> false) && failed.paid() == 0, "外部耗蓝导致支付失败时不能记成已支付");
		require(failed.advance(2, due -> due == 2) && failed.paid() == 2, "同一步重新成功支付只扣一次");
		NbtCompound insufficient = book();
		int maxMana = SpellbookData.getMaxManaNbt(insufficient);
		SpellbookData.setManaNbt(insufficient, 60, maxMana);
		require(!SpellbookData.canPayManaNbt(insufficient, maxMana, 100)
				&& SpellbookData.getManaNbt(insufficient, maxMana) == 60, "60蓝不得起手100蓝法术，拒绝不扣费");
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

	private static void checkFormationNbtFormats() {
		NbtCompound legacy = book();
		attachFormation(legacy, FormationElement.FIRE.id, 5);
		NbtCompound actual = book();
		NbtList serialized = new NbtList();
		NbtCompound wrapper = new NbtCompound();
		wrapper.putString("id", "ssc_addon:spell_formation");
		wrapper.putByte("Count", (byte) 1);
		wrapper.putByte("Slot", (byte) 0);
		wrapper.put("tag", legacy.getList(SpellbookData.NBT_FORMATIONS, 10).getCompound(0).copy());
		serialized.add(wrapper);
		actual.put(SpellbookData.NBT_FORMATIONS, serialized);
		for (NbtCompound value : new NbtCompound[]{legacy, actual}) {
			NbtCompound before = value.copy();
			require(Math.abs(FormationData.sumManaCostMultiplierNbt(value, FormationElement.FIRE) - 1.5f) < 1.0e-6,
					"actual ItemStack tag and legacy flat properties must apply the same mana cost");
			require(value.equals(before), "normalizing formation properties must not rewrite the save");
		}
		NbtCompound manaProperties = new NbtCompound();
		manaProperties.putString(FormationData.NBT_ELEMENT, FormationElement.UNIVERSAL.id);
		manaProperties.putInt(FormationData.NBT_LEVEL, 5);
		manaProperties.putString(FormationData.NBT_VARIANT, FormationData.VARIANT_MANA);
		NbtCompound manaWrapper = new NbtCompound();
		manaWrapper.putString("id", "ssc_addon:spell_formation");
		manaWrapper.putByte("Count", (byte) 1);
		manaWrapper.putByte("Slot", (byte) 1);
		manaWrapper.put("tag", manaProperties);
		serialized.add(manaWrapper);
		require(SpellbookData.getMaxManaNbt(actual) == 1080, "serialized mana formation must add 180 to the mastered book");
		serialized.add(manaProperties.copy());
		require(SpellbookData.getMaxManaNbt(actual) == 1080, "mixed legacy and serialized mana formations use the highest level without stacking");
	}

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

	/** 使用生产支付核心，不在测试中复制支付规则。 */
	private static boolean consumeManaNbt(NbtCompound book, int cost, int maxMana) {
		return SpellbookData.consumeManaNbt(book, maxMana, cost);
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
