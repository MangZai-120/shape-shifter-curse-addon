package net.jackcooper.shapeShifterCurseAddon.particle.client;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

/**
 * 「汇聚冰晶」粒子客户端实现：匀速直线飞向中心，抵达（寿命尽）即移除，末段淡出。
 *
 * <p><b>为什么必须自定义粒子</b>（反编译 {@code Particle} 6 参构造器实证）：原版粒子构造器会给初速加
 * ±0.4 随机抖动并乘 0.15~0.45 随机系数——本粒子 0.05/tick 的汇聚速度会被随机量完全淹没（视觉即「四散」）；
 * WhiteAsh/Snowflake 还带正重力会下坠。故本粒子：
 * <ul>
 *   <li>走 3 参构造器（零速度）+ 手动赋精确速度——绕开随机抖动；</li>
 *   <li>零物理：gravityStrength=0、velocityMultiplier=1（无重力、无摩擦）→ 匀速直线；</li>
 *   <li>固定寿命 {@value #INWARD_ICE_LIFETIME} tick——服务端按「初速 = 距离/寿命」发速度，
 *       寿命尽头 = 抵达中心 = 自动移除（「汇聚到中心就消失」精确成立）；</li>
 *   <li>末 {@value #FADE_TICKS} tick alpha 平滑淡出。</li>
 * </ul></p>
 */
public class InwardIceParticle extends SpriteBillboardParticle {

	/** 固定寿命（tick）：服务端按此值计算初速（距离/寿命），保证恰好抵达中心。 */
	public static final int INWARD_ICE_LIFETIME = 20;
	/** 末段淡出时长（tick）。 */
	private static final int FADE_TICKS = 5;

	private InwardIceParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
		// 3 参构造器：速度零初始化（绕开 6 参构造器的 ±0.4 随机抖动 + 随机缩放）
		super(world, x, y, z);
		// 单帧贴图：构造时取一次 sprite 即可，无需保留 provider 引用
		this.setSprite(sprites.getSprite(0, 1));
		// 精确赋服务端算好的向心速度（字段直赋，无任何随机化）
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		// 零物理：无重力、无摩擦 → 匀速直线
		this.gravityStrength = 0.0F;
		this.velocityMultiplier = 1.0F;
		this.collidesWithWorld = false;
		this.maxAge = INWARD_ICE_LIFETIME;
		// 小冰晶颗粒（渲染尺寸 0.12 格）；白色乘色保持贴图冰蓝白原色
		this.scale(0.12f);
		this.red = 1.0F;
		this.green = 1.0F;
		this.blue = 1.0F;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick(); // gravity=0、multiplier=1 → 匀速直线；寿命尽自动 markDead
	}

	@Override
	protected int getBrightness(float tickDelta) {
		// 全亮度：冰晶在暗处也清晰可见（同 EndRod/Snowflake 自发光处理）
		return 15728880;
	}

	@Override
	public void buildGeometry(net.minecraft.client.render.VertexConsumer vertexConsumer, net.minecraft.client.render.Camera camera, float tickDelta) {
		// 末段淡出：按剩余寿命压 alpha（alpha 为实例字段，安全）
		int remain = this.maxAge - this.age;
		if (remain <= FADE_TICKS) {
			this.alpha = Math.max(0.0F, remain / (float) FADE_TICKS);
		}
		super.buildGeometry(vertexConsumer, camera, tickDelta);
	}

	/** 粒子工厂：服务端速度精确交给粒子（无随机化）。 */
	public static class Factory implements ParticleFactory<DefaultParticleType> {
		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider sprites) {
			this.spriteProvider = sprites;
		}

		@Override
		public InwardIceParticle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
			return new InwardIceParticle(world, x, y, z, vx, vy, vz, spriteProvider);
		}
	}
}
