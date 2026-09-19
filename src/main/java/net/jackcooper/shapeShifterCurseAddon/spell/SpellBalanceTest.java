package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 21 法术数值回归用例（阶段 B / 计划书 §20.1 数值验收，构建期自检；不打进发行 jar）。
 *
 * <p>断言（对 data/ssc_addon/spells/*.json 全部文件）：</p>
 * <ul>
 *   <li>每法术 5 级（levels 长度 = 5，首项存在）；</li>
 *   <li>耗蓝逐级严格递增（含缺省 1.0 折算后）；</li>
 *   <li>冷却倍率逐级不增、Lv5 严格小于 Lv1；</li>
 *   <li>伤害倍率不随等级下降；</li>
 *   <li>cooldown_floor_ticks ≥ 0 且 ≤ Lv5 实际 CD（floor 不高于最低档实际冷却）。</li>
 * </ul>
 *
 * <p>运行方式：gradle 任务 {@code spellBalanceTest}（check 依赖），工作目录 build/spell-balance-test；
 * 资源从 classpath（main sourceSet）读取，与打包进 jar 的内容一致。</p>
 */
public final class SpellBalanceTest {
	private static int failures = 0;

	public static void main(String[] args) throws Exception {
		checkCastingRules();
		checkRefundLedger();
		checkFormRules();
		// classpath 无目录列举能力：用已知 21 法术 id 清单（与 SpellRegistry 注册序一致）
		String[] ids = {
				"fire_bolt", "flame_nova", "meteor",
				"frost_spike", "ice_barrage", "frost_nova", "frost_armor",
				"moonlight_arrow", "lunar_mend", "lunar_veil",
				"curse_mark", "dread_whisper", "corrupt_mist",
				"summon_lunar_spirit", "companion_resonance",
				"void_devour", "void_erosion",
				"space_blink", "space_stride", "space_recall", "pocket_space"
		};
		for (String id : ids) {
			checkSpell(id);
		}
		if (failures > 0) {
			throw new IllegalStateException("法术数值回归失败 " + failures + " 项，详见上方输出");
		}
		System.out.println("Spell balance checks passed (" + ids.length + " spells).");
	}

	private static void checkSpell(String id) throws Exception {
		String path = "/data/ssc_addon/spells/" + id + ".json";
		// 判空必须在构造 InputStreamReader 之前：try-with-resources 里 new InputStreamReader(null) 会先抛 NPE，
		// 原先的 if (r == null) 永远不可达（IDE 死代码警告所指）
		java.io.InputStream in = SpellBalanceTest.class.getResourceAsStream(path);
		if (in == null) {
			fail(id, "资源缺失: " + path);
			return;
		}
		try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
			if (!o.has("spell_tier") || !o.has("interrupt_mode")) fail(id, "缺施法档位或打断策略");
			else {
				String tierId = o.get("spell_tier").getAsString();
				if (!SpellCastingRules.Tier.byId(tierId).name().equalsIgnoreCase(tierId)) fail(id, "非法施法档位");
				int mode = o.get("interrupt_mode").getAsInt();
				if (mode < 0 || mode > 3) fail(id, "非法打断策略");
			}
			int baseCd = o.has("base_cooldown_ticks") ? o.get("base_cooldown_ticks").getAsInt() : 20;
			int floor = o.has("cooldown_floor_ticks") ? o.get("cooldown_floor_ticks").getAsInt() : 0;
			float baseMana = o.has("mana_cost") ? o.get("mana_cost").getAsFloat() : 0;
			if (!o.has("levels") || !o.get("levels").isJsonArray()) {
				fail(id, "缺 levels");
				return;
			}
			JsonArray levels = o.getAsJsonArray("levels");
			if (levels.size() != 5) {
				fail(id, "levels 长度 != 5 (" + levels.size() + ")");
				return;
			}
			// 逐级取倍率（缺省 1.0）
			float[] manaMul = new float[5];
			float[] cdMul = new float[5];
			float[] dmgMul = new float[5];
			for (int i = 0; i < 5; i++) {
				JsonObject lv = levels.get(i).isJsonObject() ? levels.get(i).getAsJsonObject() : null;
				manaMul[i] = lv != null && lv.has("mana_cost_multiplier") ? lv.get("mana_cost_multiplier").getAsFloat() : 1f;
				cdMul[i] = lv != null && lv.has("cooldown_multiplier") ? lv.get("cooldown_multiplier").getAsFloat() : 1f;
				dmgMul[i] = lv != null && lv.has("damage_multiplier") ? lv.get("damage_multiplier").getAsFloat() : 1f;
			}
			// 断言 1：耗蓝逐级严格递增（mana>0 的法术）
			if (id.equals("summon_lunar_spirit") || id.equals("companion_resonance")) {
				for (int selected = 1; selected <= 5; selected++) {
					int billed = SpellCastingRules.summonManaLevel(selected, true);
					if (billed != Math.min(selected, 4)
							|| SpellCastingRules.summonManaLevel(selected, false) != selected) {
						fail(id, "召唤亲和收费档位错误");
					}
					int cost = Math.round(baseMana * manaMul[billed - 1]);
					if (selected == 5 && cost != Math.round(baseMana * manaMul[3])) {
						fail(id, "召唤封顶后同效果多收费");
					}
				}
			}
			if (baseMana > 0) {
				for (int i = 1; i < 5; i++) {
					if (manaMul[i] <= manaMul[i - 1]) {
						fail(id, "耗蓝倍率 Lv" + (i + 1) + "(" + manaMul[i] + ") 未高于 Lv" + i + "(" + manaMul[i - 1] + ")");
					}
				}
			}
			// 断言 2：CD 倍率逐级不增 + Lv5 严格 < Lv1
			for (int i = 1; i < 5; i++) {
				if (cdMul[i] > cdMul[i - 1] + 1e-6) {
					fail(id, "CD 倍率 Lv" + (i + 1) + "(" + cdMul[i] + ") 高于 Lv" + i + "(" + cdMul[i - 1] + ")");
				}
			}
			if (cdMul[4] >= cdMul[0] - 1e-6) {
				fail(id, "Lv5 CD 倍率(" + cdMul[4] + ") 未严格低于 Lv1(" + cdMul[0] + ")");
			}
			// 断言 3：伤害倍率不随等级下降
			for (int i = 1; i < 5; i++) {
				if (dmgMul[i] < dmgMul[i - 1] - 1e-6) {
					fail(id, "伤害倍率 Lv" + (i + 1) + "(" + dmgMul[i] + ") 低于 Lv" + i + "(" + dmgMul[i - 1] + ")");
				}
			}
			// 断言 4：floor 合法（≥0；且不超过 Lv5 实际 CD——floor 高于最低档会让 floor 反成主导）
			if (floor < 0) {
				fail(id, "cooldown_floor_ticks < 0");
			}
			int lv5Cd = Math.round(baseCd * cdMul[4]);
			if (floor > lv5Cd) {
				fail(id, "floor(" + floor + "t) 高于 Lv5 实际 CD(" + lv5Cd + "t)——floor 会吞掉等级收益");
			}
		}
	}

	private static void fail(String id, String msg) {
		failures++;
		System.out.println("[FAIL] " + id + ": " + msg);
	}

	private static void checkRefundLedger() {
		var ledger = new SpellCastingRules.RefundLedger();
		var owner = java.util.UUID.randomUUID();
		var other = java.util.UUID.randomUUID();
		var oldCast = ledger.open(owner, 100, 0);
		var newCast = ledger.open(owner, 20, 10);
		if (ledger.find(other, oldCast, 20) != null || ledger.find(owner, null, 20) != null) {
			fail("refund", "跨玩家或无施法编号取得了额度");
		}
		if (ledger.find(owner, oldCast, 20).grant(0.2f) != 20
				|| ledger.find(owner, newCast, 20).grant(0.5f) != 10
				|| ledger.find(owner, oldCast, 40).grant(0.5f) != 30
				|| ledger.find(owner, oldCast, 60).grant(0.5f) != 0) {
			fail("refund", "延迟命中串账或同次多段返还超过50%");
		}
		var otherCast = ledger.open(other, 30, 20);
		ledger.clearPlayer(owner);
		if (ledger.find(owner, newCast, 30) != null || ledger.find(other, otherCast, 30) == null) {
			fail("refund", "退出清理影响了其它玩家");
		}
		long expires = 20 + SpellCastingRules.RefundLedger.LIFETIME_TICKS;
		if (ledger.find(other, otherCast, expires - 1) == null || ledger.find(other, otherCast, expires) != null) {
			fail("refund", "返还有效期边界错误");
		}
		ledger.expire(expires);
		if (ledger.find(other, otherCast, 20) != null) fail("refund", "过期额度未清理");
		for (int cost = 0; cost <= 300; cost++) {
			var castId = ledger.open(owner, cost, 0);
			int total = 0;
			for (int hit = 0; hit < 10; hit++) total += ledger.find(owner, castId, hit).grant(0.25f);
			int expected = Math.min(Math.round(cost * 0.5f), 10 * Math.round(cost * 0.25f));
			if (total != expected) fail("refund", "返还取整或封顶错误");
		}
		ledger.clear();
		System.out.println("Refund ledger passed (cast isolation, owner isolation, shared cap, expiry, cleanup).");
	}

	private static void checkFormRules() {
		for (long elapsed : new long[]{0, 139, 140, 10000}) {
			if (SpellCastingRules.naturalRegenAllowed(true, elapsed, 140)
					|| SpellCastingRules.naturalRegenAllowed(false, elapsed, 140) != (elapsed >= 140)) {
				fail("form", "施法活动状态或自然回蓝延迟错误");
			}
		}
		if (SpellCastingRules.curseDurationTicks(160, true, false) != 184
				|| SpellCastingRules.curseDurationTicks(160, false, true) != 176
				|| SpellCastingRules.curseDurationTicks(160, false, false) != 160
				|| SpellCastingRules.curseDurationTicks(80, false, true) + 60 <= 80) {
			fail("form", "诅咒时长亲和或跳蛛淬毒未生效");
		}
		System.out.println("Form rules passed (active-cast regeneration lock, recovery delay, curse duration).");
	}

	private static void checkCastingRules() {
		checkCastingLifecycle();
		int[] durations = {0, 8, 16, 30, 50, 80, 120, 160, 240, 20};
		int index = 0;
		for (SpellCastingRules.Tier tier : SpellCastingRules.Tier.values()) {
			if (tier.profile.ticks() != durations[index++]) fail("casting", "档位时长不匹配");
		}
		for (int mode = 0; mode < 4; mode++) {
			if (SpellCastingRules.allowsExternal(mode) != (mode == 1 || mode == 3)
					|| SpellCastingRules.allowsSelf(mode) != (mode == 2 || mode == 3)) {
				fail("casting", "打断模式错误");
			}
		}
		for (int total = 0; total <= 300; total++) {
			for (int duration : durations) {
				int previous = 0;
				for (int tick = 0; tick <= duration + 5; tick++) {
					int paid = SpellCastingRules.cumulativeMana(total, tick, duration);
					if (paid < previous || paid > total || tick >= duration && paid != total) {
						fail("casting", "分段耗蓝未精确封顶");
					}
					previous = paid;
				}
			}
		}
		if (SpellCastingRules.cumulativeMana(30, 40, 40) != 30
				|| SpellCastingRules.cumulativeMana(30, 20, 40) != 15
				|| SpellCastingRules.interruptedCooldown(100) != 80) fail("casting", "扣蓝/CD边界错误");
		System.out.println("Casting rules passed (10 tiers, 4 interruption modes, exact progressive mana, 20% cooldown refund).");
	}

	private static void checkCastingLifecycle() {
		var early = new SpellCastingRules.Progress<String>(SpellCastingRules.Mode.RELEASE, 40, 12);
		for (int tick = 0; tick < 10; tick++) early.tick();
		if (early.release(11, "wrong") || !early.release(12, "locked") || early.beginEffect()) {
			fail("casting", "提前松手或过期令牌错误");
		}
		if (early.release(12, "changed") || !"locked".equals(early.target())) fail("casting", "目标快照被覆盖");
		for (int tick = 10; tick < 40; tick++) early.tick();
		if (!early.beginEffect() || early.beginEffect()) fail("casting", "提前松手没有等到读条结束或重复释放");
		var held = new SpellCastingRules.Progress<String>(SpellCastingRules.Mode.RELEASE, 8, 13);
		for (int tick = 0; tick < 200; tick++) held.tick();
		if (held.elapsed() != 8 || held.beginEffect()) fail("casting", "蓄满时自动释放或进度溢出");
		if (!held.release(13, "late") || !held.beginEffect()) fail("casting", "蓄满松手未释放");
		var instant = new SpellCastingRules.Progress<String>(SpellCastingRules.Mode.RELEASE, 0, 14);
		if (instant.beginEffect() || !instant.release(14, "blink") || !instant.beginEffect()) fail("casting", "零读条瞄准规则错误");
		for (SpellCastingRules.Mode mode : new SpellCastingRules.Mode[]{SpellCastingRules.Mode.AUTOMATIC, SpellCastingRules.Mode.CONTINUOUS}) {
			var progress = new SpellCastingRules.Progress<String>(mode, 8, 15);
			if (progress.release(15, "ignored") || progress.beginEffect()) fail("casting", "非松手模式错误");
			for (int tick = 0; tick < 8; tick++) progress.tick();
			if (!progress.beginEffect() || progress.beginEffect()) fail("casting", "自动/持续模式起手错误");
		}
		var guard = new SpellCastingRules.InputGuard();
		if (guard.consume(true)) fail("casting", "初始输入被误拦截");
		guard.block();
		for (int tick = 0; tick < 30; tick++) {
			if (!guard.consume(true)) fail("casting", "切换后旧长按触发新施法");
		}
		if (!guard.consume(false) || guard.consume(true)) fail("casting", "松开后新按键未重新解锁");
		System.out.println("Casting lifecycle passed (early release, target snapshot, charge hold, instant, continuous start, stale tokens, selection latch).");
	}
}
