package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.Vec3d;

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
		checkDomainRules();
		checkDomainSound();
		checkExplosionRules();
		checkAttachedEffectScope();
		// classpath 无目录列举能力：用已知 21 法术 id 清单（与 SpellRegistry 注册序一致）
		String[] ids = {
				"fire_bolt", "flame_nova", "meteor", "explosion",
				"frost_spike", "ice_barrage", "frost_nova", "frost_armor",
				"moonlight_arrow", "lunar_mend", "lunar_veil",
				"curse_mark", "dread_whisper", "corrupt_mist",
				"summon_lunar_spirit", "companion_resonance",
				"void_devour", "void_erosion",
				"space_blink", "space_stride", "space_recall", "pocket_space", "domain"
		};
		for (String id : ids) {
			checkSpell(id);
		}
		if (failures > 0) {
			throw new IllegalStateException("法术数值回归失败 " + failures + " 项，详见上方输出");
		}
		System.out.println("Spell balance checks passed (" + ids.length + " spells).");
	}

	private static void checkExplosionRules() {
		double[] distances = {0, 16, 32, 32.000001, 48, 64, 65};
		double[] damages = {1000, 700, 400, 100, 50, 0, 0};
		for (int i = 0; i < distances.length; i++) {
			if (Math.abs(1000 * ExplosionRules.damageFactor(distances[i]) - damages[i]) > 0.00001)
				fail("explosion", "damage band boundary: " + distances[i]);
		}
		double[] fireDistances = {0, 32, 32.000001, 42, 42.000001, 50, 50.000001, 64};
		int[] fireTicks = {0, 0, 200, 200, 100, 100, 0, 0};
		for (int i = 0; i < fireDistances.length; i++) {
			if (ExplosionRules.fireTicks(fireDistances[i]) != fireTicks[i]) fail("explosion", "ignition boundary: " + fireDistances[i]);
		}
		// 时间线（2026-09-22 音频驱动版）：T-8s=540t 主题音频起播+光柱启动；32 格/s=1.6 格/t，
		// 128 格 4 秒到顶；途中法阵高度按新速度核验；682t 红白球、700t 引爆（音频 8s 处爆炸音）。
		if (ExplosionRules.beamHeight(540) != 0 || ExplosionRules.beamHeight(560) != 32
				|| ExplosionRules.beamHeight(620) != 128 || ExplosionRules.beamHeight(690) != 128) fail("explosion", "audio-driven 35s timeline");
		// 常量关系校验：两侧均为编译期常量的比较会被折叠成恒假（「identical expressions / dead code」
		// 警告所指，断言从未生效），故改经 beamHeight()（方法调用不可折叠）验证时间线锚点关系，
		// 防手改一个常量漏改其余：① 音频起播=光柱启动（起播时光柱恰为 0，1 秒后 32 格=1.6 格/t）；
		// ② 红白球出现（682t）时光柱已到顶；③ 渐入半程音量=满量一半（SOUND_FADE_TICKS 参与行为）；
		// ④ 引爆=蓄力完成由下方 Progress 循环用 EXPLODE_TICKS 推进验证。
		if (ExplosionRules.beamHeight(ExplosionRules.SOUND_START_TICKS) != 0
				|| ExplosionRules.beamHeight(ExplosionRules.SOUND_START_TICKS + 20) != 32
				|| ExplosionRules.beamHeight(ExplosionRules.BALL_START_TICKS) != ExplosionRules.BEAM_TOP
				|| Math.abs(ExplosionRules.themeVolume(
						ExplosionRules.SOUND_START_TICKS + ExplosionRules.SOUND_FADE_TICKS / 2, 0) - 0.75f) > 1e-6)
			fail("explosion", "audio-driven timeline constants");
		for (int layer = 0; layer < ExplosionRules.CIRCLE_HEIGHTS.length; layer++) {
			double at = 540 + ExplosionRules.CIRCLE_HEIGHTS[layer] / 1.6;
			if (Math.abs(ExplosionRules.beamHeight(at) - ExplosionRules.CIRCLE_HEIGHTS[layer]) > 1e-6)
				fail("explosion", "layer reveal timing");
		}
		// 主题音量曲线：起播前 0；3 秒渐入 × 距离衰减 × 增益 1.5（2026-09-22 用户反馈音量偏小）。
		// soundVolume(100) = 0.64，故 570t@100 格 = 0.5×0.64×1.5 = 0.48；渐满后近处 = 1.5。
		if (ExplosionRules.themeVolume(539, 0) != 0 || ExplosionRules.themeVolume(540, 0) != 0
				|| Math.abs(ExplosionRules.themeVolume(570, 0) - 0.75f) > 1e-6
				|| Math.abs(ExplosionRules.themeVolume(570, 100) - 0.48f) > 1e-6
				|| Math.abs(ExplosionRules.themeVolume(600, 0) - 1.5f) > 1e-6
				|| ExplosionRules.themeVolume(600, 164) != 0)
			fail("explosion", "theme fade-in must combine with distance curve");
		if (ExplosionRules.soundVolume(0) != 1 || ExplosionRules.soundVolume(64) != 1
				|| ExplosionRules.soundVolume(114) != 0.5f || ExplosionRules.soundVolume(164) != 0
				|| ExplosionRules.soundVolume(Double.NaN) != 0 || ExplosionRules.soundVolume(Double.POSITIVE_INFINITY) != 0)
			fail("explosion", "sound range/invalid distance");
		double previous = 1;
		for (double distance = 0; distance <= 200; distance += 0.125) {
			double volume = ExplosionRules.soundVolume(distance);
			if (volume < 0 || volume > previous) fail("explosion", "sound must decrease monotonically");
			previous = volume;
		}
		var progress = new SpellCastingRules.Progress<String>(SpellCastingRules.Mode.RELEASE, ExplosionRules.CHARGE_TICKS, 9);
		progress.release(9, "locked");
		// 用 EXPLODE_TICKS 驱动推进：引爆前 1t 不可释放、恰到 EXPLODE_TICKS 可释放 →
		// 行为级锁定 EXPLODE_TICKS == CHARGE_TICKS（引爆=蓄力完成）。
		for (int tick = 0; tick < ExplosionRules.EXPLODE_TICKS - 1; tick++) progress.tick();
		if (progress.beginEffect()) fail("explosion", "early release must still charge for 35 seconds");
		progress.tick();
		if (!progress.beginEffect() || !"locked".equals(progress.target())) fail("explosion", "locked target must survive charge");
		if (SpellCastingRules.interruptedCooldown(ExplosionRules.CD_TICKS) != 2880)
			fail("explosion", "interrupt keeps 80% cooldown");
		System.out.println("Explosion checks passed (damage/ignition boundaries, timeline, sound, locked release, cooldown).");
	}

	private static void checkDomainSound() {
		double[] distances = {0, 8, 16, 16.5, 17, 40.5, 64, 80};
		float[] volumes = {1, 0.9f, 0.8f, 0.8f, 0.8f, 0.4f, 0, 0};
		for (int index = 0; index < distances.length; index++) {
			if (Math.abs(DomainRules.soundVolume(distances[index]) - volumes[index]) > 1.0e-6f) {
				fail("domain", "音量端点或区间插值不符：" + distances[index]);
			}
		}
		float previous = 1;
		for (double distance = 0; distance <= 80; distance += 0.125) {
			float volume = DomainRules.soundVolume(distance);
			if (volume < 0 || volume > previous || volume > 1) fail("domain", "音量必须在 0～1 内随距离单调递减");
			previous = volume;
		}
		for (double boundary : new double[]{16, 17, 64}) {
			if (Math.abs(DomainRules.soundVolume(boundary - 0.0001)
					- DomainRules.soundVolume(boundary + 0.0001)) > 0.00001f) fail("domain", "音量在分段边界不连续");
		}
		if (DomainRules.soundVolume(Double.NaN) != 0 || DomainRules.soundVolume(Double.POSITIVE_INFINITY) != 0) {
			fail("domain", "无效距离不得产生无效音量");
		}
		System.out.println("Domain sound checks passed (100%-80% within 16, 80% through 17, fade to zero at 64, continuous and monotonic).");
	}

	private static void checkAttachedEffectScope() throws Exception {
		var field = DomainManager.class.getDeclaredField("ATTACHED_EFFECT");
		field.setAccessible(true);
		ThreadLocal<?> context = (ThreadLocal<?>) field.get(null);
		RuntimeException expected = new IllegalStateException("test");
		try {
			DomainManager.runAttachedEffect(null, null, () -> {
				Object outer = context.get();
				if (outer == null) fail("domain", "自动效果未进入限定上下文");
				DomainManager.runAttachedEffect(null, null, () -> {
					if (context.get() == null || context.get() == outer) fail("domain", "嵌套自动效果未建立独立上下文");
				});
				if (context.get() != outer) fail("domain", "嵌套结束未恢复外层上下文");
				throw expected;
			});
		} catch (RuntimeException actual) {
			if (actual != expected) throw actual;
		}
		if (context.get() != null) fail("domain", "异常退出残留自动效果放行，可能导致后续主动技能穿墙");
	}

	private static void checkDomainRules() {
		for (double radius : new double[]{1.75, 8, 17}) {
			Vec3d inside = new Vec3d(radius - 0.01, 0, 0);
			Vec3d outside = new Vec3d(radius + 0.01, 0, 0);
			if (!DomainRules.separates(inside, outside, radius) || !DomainRules.separates(outside, inside, radius)) {
				fail("domain", "感知和主动选目标必须双向隔离，扩张期不能沿用移动单向阀");
			}
			if (DomainRules.separates(inside, Vec3d.ZERO, radius)
					|| DomainRules.separates(outside, outside.multiply(2), radius)
					|| DomainRules.separates(inside, outside, 0)) {
				fail("domain", "同侧或未成壳时不得屏蔽目标");
			}
		}
		if (!DomainRules.crosses(0, 0, 0, 16.1, 0, 0, 0)) fail("domain", "内层不能向外穿越");
		if (DomainRules.crosses(0, 0, 0, 15, 0, 0, 0)) fail("domain", "内部移动不应被挡");
		if (!DomainRules.crosses(20, 0, 0, 16.9, 0, 0, 0)) fail("domain", "外层不能向内穿越");
		if (!DomainRules.crosses(-30, 0, 0, 30, 0, 0, 0)) fail("domain", "高速穿越两端在外仍须拦截");
		if (DomainRules.crosses(-30, 20, 0, 30, 20, 0, 0)) fail("domain", "球外路径不应被挡");
		if (!DomainRules.crosses(0, 0, 0, 0, -18, 0, 0)) fail("domain", "地下边界必须封闭");
		if (!DomainRules.crosses(0, 0, 0, 15.8, 0, 0, 0.5)) fail("domain", "实体尺寸需要计入边界");
		if (!DomainRules.crosses(17.2, 0, 0, 16.5, 0, 0, 0.9)) fail("domain", "外侧碰撞箱重叠不能向内挤入");
		if (DomainRules.crosses(16.5, 0, 0, 18, 0, 0, 0.9)) fail("domain", "壳间实体必须能够退到外部");
		if (DomainRules.crosses(15.8, 0, 0, 14, 0, 0, 0.9)) fail("domain", "内侧边缘允许退向内部");
		if (DomainRules.layerProgress(59, 60) != 0 || DomainRules.layerProgress(84, 60) != 1
				|| DomainRules.layerProgress(119, 120) != 0 || DomainRules.layerProgress(144, 120) != 1) fail("domain", "法阵分层展开节奏错误");
		if (Math.abs(DomainRules.receivedMultiplier(true) * DomainRules.dealtMultiplier(false) - 0.375f) > 0.0001f
				|| Math.abs(DomainRules.receivedMultiplier(false) * DomainRules.dealtMultiplier(true) - 2.025f) > 0.0001f) {
			fail("domain", "阵营伤害乘区错误");
		}
		// 扩张曲线（2006-09-21）：10s 前无壳；10s 起点 0.75；单调生长；15s 到 16 完全体
		if (DomainRules.expansionRadius(0) != 0 || DomainRules.expansionRadius(200) != 0.75
				|| DomainRules.expansionRadius(300) != DomainRules.INNER_RADIUS
				|| DomainRules.expansionRadius(250) <= DomainRules.expansionRadius(240)
				|| DomainRules.expansionRadius(250) >= DomainRules.INNER_RADIUS) fail("domain", "扩张半径曲线错误");
		// 单向阀：扩张期壳内向外=拦，壳外向内=放行
		double mid = DomainRules.expansionRadius(250);
		if (!DomainRules.crossesOutward(0, 0, 0, mid + 1, 0, 0, mid)) fail("domain", "扩张期内部不能向外穿越");
		if (DomainRules.crossesOutward(mid + 1, 0, 0, 0, 0, 0, mid)) fail("domain", "扩张期外部应当可以进入");
		if (DomainRules.crossesOutward(0, 0, 0, mid - 1, 0, 0, mid)) fail("domain", "扩张期壳内移动不应被挡");
		if (DomainRules.crossesOutward(1, 0, 0, 2, 0, 0, 0)) fail("domain", "未成壳时不应拦截");
		// 球面滑行（2026-09-22）：径向撞墙只压径向、保留切向；壳外/纯切向原样返回（返回值=修正后位移）
		Vec3d slide = DomainRules.slideInside(new Vec3d(15.5, 0, 0), new Vec3d(0.5, 0, 2), 16);
		if (15.5 + slide.x > 16.0 + 1.0e-6 || Math.abs(slide.z - 2) > 1.0e-6) fail("domain", "滑行必须保留切向并压径向");
		Vec3d free = DomainRules.slideInside(new Vec3d(10, 0, 0), new Vec3d(0.5, 0, 2), 16);
		if (free.x != 0.5 || free.z != 2) fail("domain", "不撞墙时不得改动位移");
		Vec3d outside = DomainRules.slideInside(new Vec3d(20, 0, 0), new Vec3d(-1, 0, 0), 16);
		if (outside.x != -1) fail("domain", "起点在壳外时原样返回");
		Vec3d centerOut = DomainRules.slideInside(Vec3d.ZERO, new Vec3d(30, 0, 0), 16);
		if (centerOut.length() - 16 > 1.0e-6) fail("domain", "球心出发须夹到半径内");
		Vec3d contact = new Vec3d(15.7, 0, 0);
		Vec3d alongWall = DomainRules.slideInside(contact, new Vec3d(0.2, 0, 0.3), 15.7);
		if (contact.add(alongWall).length() > 15.7 + 1.0e-9 || alongWall.z < 0.29) {
			fail("domain", "贴墙斜走必须保持切向移动且落点仍在球内");
		}
		Vec3d jumpAtWall = DomainRules.slideInside(contact, new Vec3d(0, 0.42, 0), 15.7);
		if (contact.add(jumpAtWall).length() > 15.7 + 1.0e-9 || jumpAtWall.y < 0.4) {
			fail("domain", "贴墙跳跃必须保留上升且不能产生越界落点");
		}
		Vec3d fastSlide = DomainRules.slideInside(contact, new Vec3d(0.2, 0, 40), 15.7);
		if (contact.add(fastSlide).length() > 15.7 + 1.0e-9) {
			fail("domain", "高速切向位移也必须约束在球内");
		}
		// 穿壳回归（2026-09-22）：脚 15.9 + 身高偏移 0.9 的贴墙玩家（旧实现用碰撞箱中心
		// √(15.9²+0.9²)≈15.93 仍内侧但更高实体 15.5 偏移 1.8 → 15.6……直接测最严场景：
		// 脚 15.9 向外走，用脚锚点判定必须拦截；壳间带（16.0~17.3）向外允许（可退出语义保留）
		if (!DomainRules.crosses(15.9, 0, 0, 16.4, 0, 0, 0.3)) fail("domain", "脚锚点下贴墙向外必须拦截");
		if (DomainRules.crosses(15.9, 0, 0, 15.5, 0, 0, 0.3)) fail("domain", "贴墙向内不应拦截");
		if (DomainRules.crosses(16.5, 0, 0, 17.0, 0, 0, 0.3)) fail("domain", "壳间带向外应允许退出");
		checkDomainMovement();
		System.out.println("Domain geometry checks passed (inner/outer shells, swept paths, underground, hitbox, faction damage).");
	}

	private static void checkDomainMovement() {
		var complete = new DomainRules.Shell(Vec3d.ZERO, 16, true);
		var growing = new DomainRules.Shell(Vec3d.ZERO, 8, false);
		var walls = java.util.List.of(complete);
		Vec3d outsideStart = new Vec3d(17.3, 0, 0);
		Vec3d outsideMove = DomainRules.limitMovement(outsideStart, new Vec3d(-0.3, 0.42, 0.3), 0.3, walls);
		if (complete.blocks(outsideStart, outsideMove, 0.3) || outsideMove.z < 0.29 || outsideMove.y < 0.4) {
			fail("domain", "外侧撞墙必须封闭并允许侧移与跳跃");
		}
		Vec3d edgeStart = new Vec3d(15.9, 0, 0);
		Vec3d edgeMove = DomainRules.limitMovement(edgeStart, new Vec3d(0.3, 0, 0.3), 0.3, walls);
		if (complete.blocks(edgeStart, edgeMove, 0.3) || edgeMove.z < 0.29) {
			fail("domain", "内侧碰撞箱已贴边时仍须滑行而不能穿墙");
		}
		Vec3d retreat = new Vec3d(-0.3, 0, 0);
		if (!DomainRules.limitMovement(edgeStart, retreat, 0.3, walls).equals(retreat)) fail("domain", "内侧后退不能被锁住");
		Vec3d entryStart = new Vec3d(10, 0, 0);
		Vec3d entry = new Vec3d(-3, 0, 0);
		if (!DomainRules.limitMovement(entryStart, entry, 0.3, java.util.List.of(growing)).equals(entry)) {
			fail("domain", "扩张期必须允许外侧进入");
		}
		Vec3d fastEntry = DomainRules.limitMovement(entryStart, new Vec3d(-30, 0, 0), 0.3, java.util.List.of(growing));
		if (entryStart.add(fastEntry).length() > 8 + 1.0e-6) fail("domain", "扩张期高速进入后不能从另一侧穿出");
		Vec3d fastStart = new Vec3d(-30, 0, 0);
		Vec3d fastCross = DomainRules.limitMovement(fastStart, new Vec3d(60, 0, 0), 0.3, walls);
		if (complete.blocks(fastStart, fastCross, 0.3) || fastStart.add(fastCross).x > -17.3 + 1.0e-6) {
			fail("domain", "高速外侧穿越必须停在迎面边界");
		}
		for (DomainRules.Shell shell : java.util.List.of(complete, growing)) {
			Vec3d position = new Vec3d(shell.complete() ? 15.7 : 8, 0, 0);
			for (int tick = 0; tick < 400; tick++) {
				Vec3d normal = position.normalize();
				Vec3d wanted = normal.multiply(0.2).add(-normal.z * 0.25, 0, normal.x * 0.25);
				Vec3d limited = DomainRules.limitMovement(position, wanted, 0.3, java.util.List.of(shell));
				if (shell.blocks(position, limited, 0.3) || limited.length() < 0.2) {
					fail("domain", "连续贴墙侧移不能累积越界或卡死");
					break;
				}
				position = position.add(limited);
			}
		}
		var overlap = java.util.List.of(complete, new DomainRules.Shell(new Vec3d(8, 0, 0), 16, true));
		var random = new java.util.Random(20260922L);
		for (int sample = 0; sample < 2000; sample++) {
			Vec3d start = new Vec3d(random.nextDouble() * 60 - 30, random.nextDouble() * 40 - 20, random.nextDouble() * 60 - 30);
			Vec3d wanted = new Vec3d(random.nextDouble() * 40 - 20, random.nextDouble() * 20 - 10, random.nextDouble() * 40 - 20);
			Vec3d limited = DomainRules.limitMovement(start, wanted, 0.3, overlap);
			for (DomainRules.Shell shell : overlap) {
				if (shell.blocks(start, limited, 0.3)) {
					fail("domain", "多领域修正后整段移动仍穿墙");
					return;
				}
			}
		}
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
			if (id.equals("explosion")) {
				if (levels.size() != 1 || !levels.get(0).getAsJsonObject().get("rarity").getAsString().equals("red")) fail(id, "red spell must have one level");
				if (baseCd != ExplosionRules.CD_TICKS || baseMana != ExplosionRules.MANA_COST
						|| o.get("base_damage").getAsFloat() != 1000 || !o.get("element").getAsString().equals("fire")) fail(id, "base stats mismatch");
				if (o.get("base_cast_time_ticks").getAsInt() != ExplosionRules.CHARGE_TICKS
						|| o.get("interrupt_mode").getAsInt() != 3 || !o.get("spell_tier").getAsString().equals("custom")) fail(id, "casting config mismatch");
				var spell = new net.jackcooper.shapeShifterCurseAddon.spell.spells.ExplosionSpell();
				spell.ssc_addon$applyConfig(net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig.fromJson(o));
				var profile = spell.getCastingProfile(null, 1, false);
				if (!spell.requiresTargetBeforeChannel() || spell.getCastingMode() != SpellCastingRules.Mode.RELEASE
						|| spell.getAimMaxRange() != 128 || spell.getAimRadius(1) != 0
						|| profile.ticks() != 700 || profile.speedMultiplier() != 0 || !profile.immobilized())
					fail(id, "target selection must precede the 35-second immobile channel");
				if (new net.jackcooper.shapeShifterCurseAddon.spell.spells.MeteorSpell().requiresTargetBeforeChannel())
					fail(id, "meteor input behavior must remain unchanged");
				return;
			}
			if (id.equals("domain")) {
				if (levels.size() != 1 || !levels.get(0).getAsJsonObject().get("rarity").getAsString().equals("red")) fail(id, "红色必须单级");
				if (baseCd != 3600 || baseMana != 300 || !o.get("element").getAsString().equals("space")) fail(id, "领域基础数值不符");
				if (o.get("base_cast_time_ticks").getAsInt() != DomainRules.CHARGE_TICKS
						|| o.get("interrupt_mode").getAsInt() != 3 || !o.get("spell_tier").getAsString().equals("custom")) fail(id, "领域施法配置不符");
				return;
			}
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
