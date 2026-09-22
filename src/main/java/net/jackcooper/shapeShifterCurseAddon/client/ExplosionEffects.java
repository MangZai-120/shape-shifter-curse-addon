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

	public void tick(ClientWorld world, Vec3d center, int elapsed, boolean detonated) {
		if (detonated) {
			if (!exploded) {
				exploded = true;
				blast(world, center);
				// 主题音频是唯一音源（用户定稿）：不再叠加原版爆炸音；主题继续自然播完。
			} else {
				embers(world, center); // 余韵：火星雨持续蹦出（服务端 AFTER_GLOW 后 view 被清，窗口约 20t）
			}
			return;
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

	private static void blast(ClientWorld world, Vec3d center) {
		// 中心 TNT 级大爆炸（2026-09-22 用户需求：粒子球半径 28 格）：单个 emitter 视觉直径约 16 格，
		// 中心 1 + 内环 6（半径 8）+ 外环 12（半径 18，错高 ±8）叠加覆盖 28 格球。
		particle(ParticleTypes.EXPLOSION_EMITTER, center, Vec3d.ZERO, 8);
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3 + 0.4;
			particle(ParticleTypes.EXPLOSION_EMITTER,
					center.add(Math.cos(angle) * 8, world.random.nextDouble() * 6 - 2, Math.sin(angle) * 8),
					Vec3d.ZERO, 8);
		}
		for (int i = 0; i < 12; i++) {
			double angle = i * Math.PI / 6;
			particle(ParticleTypes.EXPLOSION_EMITTER,
					center.add(Math.cos(angle) * 18, world.random.nextDouble() * 16 - 8, Math.sin(angle) * 18),
					Vec3d.ZERO, 8);
		}
		// 粒子球（2026-09-22 用户定稿半径 28 格）：等面积方向覆盖全球面（含目标上下方），
		// 外壳粒子落在 28 格球面、其余在球体内随机体积分布。
		final double burstRadius = 28.0;
		for (int i = 0; i < 1024; i++) {
			double y = 1 - 2 * (i + 0.5) / 1024;
			double radial = Math.sqrt(1 - y * y);
			double angle = i * Math.PI * (3 - Math.sqrt(5));
			Vec3d direction = new Vec3d(radial * Math.cos(angle), y, radial * Math.sin(angle));
			double radius = i % 3 == 0 ? burstRadius : burstRadius * Math.cbrt(world.random.nextDouble());
			particle(i % 2 == 0 ? RED : WHITE, center.add(direction.multiply(radius)), direction.multiply(0.25), 30);
			if (i % 8 == 0) particle(ParticleTypes.FLAME, center.add(direction.multiply(radius)), direction.multiply(0.5), 24);
		}
		particle(ParticleTypes.EXPLOSION_EMITTER, center, Vec3d.ZERO, 8);
		for (int i = 0; i < 48; i++) {
			particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.add((world.random.nextDouble() - 0.5) * 28,
					world.random.nextDouble() * 20, (world.random.nextDouble() - 0.5) * 28), new Vec3d(0, 0.3, 0), 45);
		}
		// 大量蹦出的火星（2026-09-22 用户需求：同恶魂火球上的 FLAME 火星）：从杀伤范围内地面窜起。
		sparks(world, center, 240);
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
