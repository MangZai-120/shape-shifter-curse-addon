package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.spell.ExplosionRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/** Bounded local particles and the 21-second theme audio for one server-authorized sequence.
 * 2026-09-22 rework: the theme track (3s fade-in, blast at its 8th second) is the sole audio —
 * vanilla TNT blast overlay and pre-blast warning beeps are removed. Volume follows the agreed
 * distance curve (full within 64, linear to zero at 164) times the 3-second fade-in. */
@Environment(EnvType.CLIENT)
public final class ExplosionEffects {
	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1, 0.12f, 0.08f), 2.5f);
	private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1, 0.95f, 0.9f), 2.5f);
	private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.55f, 0.12f, 0.85f), 2.5f);
	private boolean exploded;
	private ThemeSound theme;
	/** 分帧计数器（2026-09-24）：爆炸粒子分帧摊平用，正数=尚待发放的剩余批次数。 */
	private int pendingBlastBatches;

	public void tick(ClientWorld world, Vec3d center, int elapsed, boolean detonated) {
		if (detonated) {
			if (!exploded) {
				exploded = true;
				// 爆炸粒子分帧（2026-09-24 修卡顿）：原实现单 tick 提交 ~1900 粒子（球面 1024 +
				// 火星 240 + 烟 48 + emitter 双环 18）→ 同帧粒子系统分配/渲染尖峰，表现为
				// 「造成伤害后法阵卡一下」。改为首帧中心 emitter + 首批粒子，其余 5 帧每帧一批
				// 整数分割发放（详见 spawnBlastBatch）。
				particle(ParticleTypes.EXPLOSION_EMITTER, center, Vec3d.ZERO, 8);
				pendingBlastBatches = BLAST_BATCHES;
				spawnBlastBatch(world, center);
				// 主题音频是唯一音源（用户定稿）：不再叠加原版爆炸音；主题继续自然播完。
			} else {
				if (pendingBlastBatches > 0) spawnBlastBatch(world, center);
				embers(world, center); // 余韵：火星雨持续蹦出（服务端 AFTER_GLOW 后 view 被清，窗口约 20t）
			}
			return;
		}
		// 光影着色器预热（2026-09-24 修卡顿，用户实测静音仍卡+使用光影）：Iris/Oculus 下粒子类型
		// 首次渲染时会即时编译着色器程序——爆裂独用的 emitter/篝火烟/火星在爆炸前从未出现过，
		// 编译尖峰正好落在爆炸那一帧（蓄力 35 秒不卡、一爆就卡的真因）。蓄力早期（第 2-10 秒，
		// 音频未起、演出最安静的窗口）往爆心地下塞 1-2 tick 寿命的隐形样本，把编译提前到无感期。
		if (elapsed >= 40 && elapsed <= 200 && elapsed % 20 == 0) {
			var hidden = center.add(0, -8, 0); // 爆心地下 8 格，被地形遮挡不可见
			particle(ParticleTypes.EXPLOSION_EMITTER, hidden, Vec3d.ZERO, 1);
			particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, hidden, Vec3d.ZERO, 1);
			particle(ParticleTypes.FLAME, hidden, Vec3d.ZERO, 1);
			particle(ParticleTypes.LARGE_SMOKE, hidden, Vec3d.ZERO, 1);
		}
		// 主题音频：T-8s（540t）起播，音量 = 渐入 × 距离曲线（随听者移动逐 tick 重算）。
		if (elapsed >= ExplosionRules.SOUND_START_TICKS && theme == null) {
			theme = new ThemeSound(world, center);
			MinecraftClient.getInstance().getSoundManager().play(theme);
		}
		// 400 伤警示圈（2026-09-22 用户需求）：爆炸前 8 秒起，红/黑/紫三色粒子圈标出 400 伤范围
		// （32 格处伤害恰为 400，即 CORE_RADIUS；与音频起播、光柱启动同时刻）。
		if (elapsed >= ExplosionRules.SOUND_START_TICKS) warnRing(world, center, elapsed);
		// Geometry provides the solid beam/circles; sparse sparks never require particle packets.
		double height = ExplosionRules.beamHeight(elapsed);
		for (int i = 0; i < 6 && height > 0; i++) {
			particle(RED, center.add((world.random.nextDouble() - 0.5) * 0.5,
					world.random.nextDouble() * height, (world.random.nextDouble() - 0.5) * 0.5), Vec3d.ZERO, 14);
		}
	}

	/** 400 伤警示圈：每 2t 沿 32 格圆周撒红/黑/紫交替粒子，圈体随时间缓慢旋转流动。 */
	private static void warnRing(ClientWorld world, Vec3d center, int elapsed) {
		if (world.getTime() % 2 != 0) return;
		double radius = ExplosionRules.CORE_RADIUS;
		int points = 120;
		double offset = (elapsed - ExplosionRules.SOUND_START_TICKS) * 0.02;
		for (int i = 0; i < points; i++) {
			double angle = offset + i * 2 * Math.PI / points;
			double x = Math.cos(angle) * radius;
			double z = Math.sin(angle) * radius;
			Vec3d pos = center.add(x, 0.25, z);
			Vec3d drift = new Vec3d(-Math.sin(angle) * 0.05, 0.04, Math.cos(angle) * 0.05);
			switch (i % 3) {
				case 0 -> particle(RED, pos, drift, 40);
				case 1 -> particle(ParticleTypes.LARGE_SMOKE, pos, drift.multiply(0.6), 36); // 黑
				default -> particle(PURPLE, pos, drift, 40);
			}
		}
	}

	/** 爆炸粒子分帧批次总数（6 帧 = 首 1 + 后续 5，约 0.3 秒内摊完）。 */
	private static final int BLAST_BATCHES = 6;
	/** 粒子球总量（与原单帧实现一致）。 */
	private static final int SPHERE_PARTICLES = 1024;

	/** 分帧发放一批爆炸粒子（2026-09-24 修卡顿）：第 batch 帧（0 = 首帧，已在 detonated 沿调用）。
	 * 各类粒子按全局下标整数区间 [batch*N/6, (batch+1)*N/6) 精确分割，0..N-1 完整覆盖无遗漏：
	 * emitter 双环 18（内 6 + 外 12）、粒子球 1024、火星 240、烟 48 每帧各 3/171/40/8。
	 * 每粒子的方向/颜色/半径仍由全局下标 i 决定（斐波那契球面 + 红白交替 + i%3 壳层），
	 * 分帧只改提交时机不改分布；6 帧 0.3 秒内全部发完，窗口远小于 AFTER_GLOW 40t 不会截断。 */
	private void spawnBlastBatch(ClientWorld world, Vec3d center) {
		int batch = BLAST_BATCHES - pendingBlastBatches; // 0..5
		pendingBlastBatches--;
		// emitter 双环（18 个）：全局下标 <6 为内环（半径 8，错高 ±2），≥6 为外环（半径 18，错高 ±8）
		for (int i = batch * 18 / BLAST_BATCHES; i < (batch + 1) * 18 / BLAST_BATCHES; i++) {
			boolean outer = i >= 6;
			int n = outer ? i - 6 : i;
			double angle = outer ? n * Math.PI / 6 : n * Math.PI / 3 + 0.4;
			double ringR = outer ? 18 : 8;
			particle(ParticleTypes.EXPLOSION_EMITTER,
					center.add(Math.cos(angle) * ringR, world.random.nextDouble() * (outer ? 16 : 6) - (outer ? 8 : 2), Math.sin(angle) * ringR),
					Vec3d.ZERO, 8);
		}
		// 粒子球：等面积方向覆盖全球面（含目标上下方），外壳粒子落在 28 格球面、其余球体内随机体积分布
		final double burstRadius = 28.0;
		int from = batch * SPHERE_PARTICLES / BLAST_BATCHES;
		int to = (batch + 1) * SPHERE_PARTICLES / BLAST_BATCHES;
		for (int i = from; i < to; i++) {
			double y = 1 - 2 * (i + 0.5) / (double) SPHERE_PARTICLES;
			double radial = Math.sqrt(1 - y * y);
			double angle = i * Math.PI * (3 - Math.sqrt(5));
			Vec3d direction = new Vec3d(radial * Math.cos(angle), y, radial * Math.sin(angle));
			double radius = i % 3 == 0 ? burstRadius : burstRadius * Math.cbrt(world.random.nextDouble());
			particle(i % 2 == 0 ? RED : WHITE, center.add(direction.multiply(radius)), direction.multiply(0.25), 30);
			if (i % 8 == 0) particle(ParticleTypes.FLAME, center.add(direction.multiply(radius)), direction.multiply(0.5), 24);
		}
		// 火星（每帧 40 共 240）：从杀伤圈内地面窜起；烟（每帧 8 共 48）
		sparks(world, center, 40);
		for (int i = 0; i < 8; i++) {
			particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.add((world.random.nextDouble() - 0.5) * 28,
					world.random.nextDouble() * 20, (world.random.nextDouble() - 0.5) * 28), new Vec3d(0, 0.3, 0), 45);
		}
	}

	/** 火星：火焰粒子从 32 格杀伤圈内随机位置向上蹦出（带随机水平散布，向上速度 0.2-0.7）。 */
	private static void sparks(ClientWorld world, Vec3d center, int count) {
		for (int i = 0; i < count; i++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			double dist = world.random.nextDouble() * ExplosionRules.CORE_RADIUS;
			Vec3d pos = center.add(Math.cos(angle) * dist, world.random.nextDouble() * 2, Math.sin(angle) * dist);
			Vec3d vel = new Vec3d((world.random.nextDouble() - 0.5) * 0.4,
					0.2 + world.random.nextDouble() * 0.5, (world.random.nextDouble() - 0.5) * 0.4);
			particle(ParticleTypes.FLAME, pos, vel, 30);
		}
	}

	/** 余韵火星雨：爆炸后每个客户端 tick 继续蹦 12 个火星，直至服务端移除序列（约 20t）。 */
	private static void embers(ClientWorld world, Vec3d center) {
		sparks(world, center, 12);
	}

	private static void particle(ParticleEffect effect, Vec3d position, Vec3d velocity, int life) {
		var particle = MinecraftClient.getInstance().particleManager.addParticle(effect,
				position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
		if (particle != null) particle.setMaxAge(life);
	}

	public void stop() {
		if (theme != null) MinecraftClient.getInstance().getSoundManager().stop(theme);
		theme = null;
	}

	/** 主题音频实例：AttenuationType.NONE 绕开原版二次衰减，音量逐 tick 按渐入×距离曲线重算。 */
	private static final class ThemeSound extends MovingSoundInstance {
		private final ClientWorld world;
		private final Vec3d center;
		private final long startedAt;

		ThemeSound(ClientWorld world, Vec3d center) {
			super(net.jackcooper.shapeShifterCurseAddon.SscAddon.EXPLOSION_THEME_EVENT,
					SoundCategory.PLAYERS, Random.create());
			this.world = world;
			this.center = center;
			this.startedAt = world.getTime();
			x = center.x; y = center.y; z = center.z;
			this.pitch = 1.0f;
			attenuationType = AttenuationType.NONE;
			repeat = false;
			tick();
		}

		@Override public void tick() {
			var client = MinecraftClient.getInstance();
			if (client.world != world || client.player == null) {
				volume = 0;
				setDone();
				return;
			}
			int since = (int) Math.max(0, world.getTime() - startedAt);
			volume = ExplosionRules.themeVolume(ExplosionRules.SOUND_START_TICKS + since,
					Math.sqrt(client.player.squaredDistanceTo(center)));
			if (volume <= 0f && since > ExplosionRules.SOUND_FADE_TICKS) setDone();
		}

		@Override public boolean shouldAlwaysPlay() { return true; }
	}
}
