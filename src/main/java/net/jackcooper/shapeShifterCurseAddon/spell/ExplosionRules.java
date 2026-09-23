package net.jackcooper.shapeShifterCurseAddon.spell;

/**
 * 爆裂魔法（explosion）数值规则（jackcooper，2026-09-22 用户定稿）：
 * 烈焰系红色单级法术——128 格瞄准、35 秒禁动读条、300 蓝 / 3 分钟 CD（同领域规格）。
 *
 * <p>锁点后的 700t 蓄力序列：地面 2 格法阵即刻出现并保持 → 第 27 秒（540t）起播
 * 爆炸主题音频（3 秒渐入）并升起 0.15 格直径红色光柱（32 格/秒，4 秒到 128 格顶），
 * 途中在 12/16/20/24/28/32/36 格高度依次浮现半径 8/16/28/8/10/7/12 格的红紫法阵
 * → 第 34.1 秒红白球长至 12 格 → 第 35 秒蓄力完成结算爆炸（音频 8 秒处爆炸音同步）。</p>
 *
 * <p>伤害（无差别，含施法者本人；不破坏方块）：0-32 格 100%→40% 线性递减，
 * 32 格处陡降至 10%，32-64 格 10%→0 线性递减（以基础伤害 1000 计即 1000→400 / 100→0）。
 * 点燃：32-42 格 10 秒、42-50 格 5 秒。击退：全范围从爆心向外，近强远弱。</p>
 *
 * <p>音效（2026-09-22 用户定稿）：完全由 explosion_theme（21 秒）接管——T-8s 起播、
 * 3 秒渐入、第 8 秒爆炸音；不再叠原版 TNT 爆炸音与预警 beep。距离衰减 64 格内满音量、
 * 64-164 格线性递减至 0。</p>
 */
public final class ExplosionRules {
	private ExplosionRules() {}

	// ---- 施法规格（与 explosion.json 保持一致，平衡测试强制校验） ----
	/** 蓄力读条时长（35 秒，custom 档禁动）。 */
	public static final int CHARGE_TICKS = 700;
	/** 基础冷却（3 分钟，同领域）。 */
	public static final int CD_TICKS = 3600;
	/** 基础耗蓝（同领域）。 */
	public static final int MANA_COST = 300;
	/** 最大施法（瞄准）距离（格）。 */
	public static final double AIM_RANGE = 128.0;

	// ---- 伤害与附加 ----
	/** 核心杀伤半径：0-CORE 内伤害 100%→40% 线性递减。 */
	public static final double CORE_RADIUS = 32.0;
	/** 直伤外沿：CORE-OUTER 内伤害 10%→0 线性递减（CORE 处陡降断层，用户定稿）。 */
	public static final double OUTER_RADIUS = 64.0;
	/** 点燃内界（CORE-IGNITE_NEAR 点燃 10 秒）。 */
	public static final double IGNITE_NEAR = 42.0;
	/** 点燃外界（IGNITE_NEAR-IGNITE_FAR 点燃 5 秒，更远不点燃）。 */
	public static final double IGNITE_FAR = 50.0;
	/** 32-42 格点燃时长（t）。 */
	public static final int FIRE_TICKS_NEAR = 200;
	/** 42-50 格点燃时长（t）。 */
	public static final int FIRE_TICKS_FAR = 100;

	// ---- 视觉序列 ----
	/** 主题音频起播 + 光柱启动时刻：蓄力第 27 秒（T-8s，伤害前 8 秒）。 */
	public static final int SOUND_START_TICKS = 540;
	/** 光柱上升速度（32 格/秒 = 1.6 格/tick）。 */
	public static final double BEAM_BLOCKS_PER_TICK = 1.6;
	/** 光柱总高（格）。 */
	public static final double BEAM_TOP = 128.0;
	/** 光柱直径（格，红色 16 棱圆柱）。 */
	public static final double BEAM_WIDTH = 0.15;
	/** 光柱途中法阵出现高度（格，升序）。 */
	public static final int[] CIRCLE_HEIGHTS = {12, 16, 20, 24, 28, 32, 36};
	/** 各高度法阵半径（格，与 {@link #CIRCLE_HEIGHTS} 一一对应）。 */
	public static final double[] CIRCLE_RADII = {8, 16, 28, 8, 10, 7, 12};
	/** 红白球开始生长时刻：蓄力第 34.1 秒（音频 7.1 秒处）。 */
	public static final int BALL_START_TICKS = 682;
	/** 红白球生长时长（0.9 秒，长满 12 格）。 */
	public static final int BALL_LEAD_TICKS = 18;
	/** 红白球完全体半径（格）。 */
	public static final double BALL_RADIUS = 12.0;
	/** 蓄力完成/引爆时刻 = 700t（音频第 8 秒处爆炸音同步）。 */
	public static final int EXPLODE_TICKS = CHARGE_TICKS;
	/** 音频 3 秒渐入时长（t）。 */
	public static final int SOUND_FADE_TICKS = 60;
	// ---- 终章（2026-09-22 用户定稿：爆炸后法阵自上而下加速上飞消散） ----
	/** 爆前 0.3s 光柱向下收缩归零（6t，收缩量按平方加速增强收缩感）。 */
	public static final int BEAM_SHRINK_TICKS = 6;
	/** 爆炸后法阵原地保持 0.7s（14t）。 */
	public static final int FINALE_HOLD_TICKS = 14;
	/** 终章法阵自上而下启动间隔 0.12s（2.4t）。 */
	public static final float FINALE_STAGGER_TICKS = 2.4f;
	/** 每层法阵上飞时长 0.5s（10t）。 */
	public static final int FINALE_RISE_TICKS = 10;
	/** 上飞加速时长 0.2s（4t，之后匀速到末速度）。 */
	public static final int FINALE_ACCEL_TICKS = 4;
	/** 各层末速度（格/tick，自最上层向下：16/14/12/10/7/5/3 格每秒 ÷ 20）。 */
	public static final double[] FINALE_SPEEDS = {0.8, 0.7, 0.6, 0.5, 0.35, 0.25, 0.15};
	/** 爆炸后余韵：覆盖终章全程（14t 保持 + 6×2.4t 间隔 + 10t 上飞 ≈ 38t），过后移除序列条目。 */
	public static final int AFTER_GLOW_TICKS = 40;
	// ---- 冲击波球（2026-09-22 用户定稿：爆炸瞬间白色半透明球扩张消散） ----
	/** 冲击波球扩张时长 0.3s（6t）：从爆心 0 半径扩到直伤外沿 64 格。 */
	public static final int SHOCKWAVE_TICKS = 6;
	/** 冲击波球初始透明度：20%（2026-09-22 用户实机反馈 10% 偏淡调高），随扩张线性降到 0。 */
	public static final float SHOCKWAVE_ALPHA = 0.20f;

	/** 光柱当前逻辑高度（格）：540t 前 0，之后 1.6 格/tick 上升，封顶 128（法阵浮现判定用）。 */
	public static double beamHeight(double ticks) {
		if (ticks <= SOUND_START_TICKS) return 0;
		return Math.min(BEAM_TOP, (ticks - SOUND_START_TICKS) * BEAM_BLOCKS_PER_TICK);
	}

	/** 光柱渲染高度（格）：末段叠加爆前 0.3s 向下收缩（按平方加速：保留量 = 剩余比例² × 当前高）。 */
	public static double beamRenderHeight(double ticks) {
		double height = beamHeight(ticks);
		if (height <= 0) return 0;
		double remain = EXPLODE_TICKS - ticks;
		if (remain >= BEAM_SHRINK_TICKS) return height;
		if (remain <= 0) return 0;
		double ratio = remain / (double) BEAM_SHRINK_TICKS;
		return height * ratio * ratio;
	}

	/** 终章法阵位移（格，向上）：自启动起 0.2s 加速到末速（三角加速），之后匀速；0.5s 末恰归末速×10t。 */
	public static double finaleLift(int fromTop, float since) {
		double speed = FINALE_SPEEDS[Math.min(fromTop, FINALE_SPEEDS.length - 1)];
		if (since <= FINALE_ACCEL_TICKS) {
			return speed * since * since / (2.0 * FINALE_ACCEL_TICKS);
		}
		return speed * (FINALE_ACCEL_TICKS / 2.0 + (since - FINALE_ACCEL_TICKS));
	}

	// ---- 音效与可见范围 ----
	/** 满音量半径：64 格内音量恒 1.0。 */
	public static final double SOUND_FULL = 64.0;
	/** 音效外沿：64-164 格线性递减至 0。 */
	public static final double SOUND_RANGE = 164.0;
	/** 视觉（法阵/光柱/球）同步范围：音效外沿 + 余量。 */
	public static final double VIEW_RANGE = 200.0;

	/** 主题音频整体音量增益。⚠ MC 音频引擎实际增益钳制在 1.0（超过只扩大可闻范围不放大），
	 * 想加大音量必须提升源文件响度（2026-09-23 已将 ogg 峰值 -16.1dB → -1.1dB，增益还原 1.0）。 */
	public static final float THEME_VOLUME_GAIN = 1.0f;

	/** 主题音频当前播放音量（渐入 × 距离衰减 × 增益）：T-8s 前 0；3 秒线性渐入 × 64 内满/64-164 递减。 */
	public static float themeVolume(int elapsed, double distance) {
		if (elapsed < SOUND_START_TICKS) return 0f;
		float fade = Math.min(1f, (elapsed - SOUND_START_TICKS) / (float) SOUND_FADE_TICKS);
		return Math.min(THEME_VOLUME_GAIN, fade * soundVolume(distance) * THEME_VOLUME_GAIN);
	}

	/** 距爆心 distance 格的伤害系数：0-32 格 1.0→0.4；32-64 格 0.1→0.0（陡降断层）。 */
	public static double damageFactor(double distance) {
		if (!Double.isFinite(distance) || distance < 0) return 0;
		if (distance <= CORE_RADIUS) {
			return 1.0 - 0.6 * (distance / CORE_RADIUS);
		}
		if (distance <= OUTER_RADIUS) {
			return 0.1 * (1.0 - (distance - CORE_RADIUS) / (OUTER_RADIUS - CORE_RADIUS));
		}
		return 0;
	}

	/** 距爆心 distance 格的点燃时长（t）：32-42 格 200t、42-50 格 100t、其余 0。 */
	public static int fireTicks(double distance) {
		if (distance > CORE_RADIUS && distance <= IGNITE_NEAR) return FIRE_TICKS_NEAR;
		if (distance > IGNITE_NEAR && distance <= IGNITE_FAR) return FIRE_TICKS_FAR;
		return 0;
	}

	/** 击退水平强度：近处 1.8 线性衰减到外沿 0.2。 */
	public static double knockbackStrength(double distance) {
		double t = Math.min(1.0, distance / OUTER_RADIUS);
		return 1.6 * (1.0 - t) + 0.2;
	}

	/** 音效音量曲线：64 格内恒 1.0，64-164 格线性递减至 0（与领域曲线形状不同，用户定稿）。 */
	public static float soundVolume(double distance) {
		if (!Double.isFinite(distance)) return 0f;
		if (distance <= SOUND_FULL) return 1.0f;
		if (distance >= SOUND_RANGE) return 0f;
		return (float) (1.0 - (distance - SOUND_FULL) / (SOUND_RANGE - SOUND_FULL));
	}
}
