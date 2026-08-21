package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity;

/**
 * 红使魔狐火火球渲染器：沿用原版飞行物品渲染（火焰弹物品本体）+ 客户端本地自绘拖尾粒子。
 *
 * <p><b>网络优化</b>：原服务端逐 tick 广播拖尾（count=0 单粒包 ~360包/秒）已删除——
 * 火球位置/速度客户端均已知（速度已编入生成包），拖尾几何（火焰球体 + 岩浆火星 + 熔岩滴落 + 尾部反向火焰）
 * 全部可由「实体位置 + 速度」本地复算，多人天然一致，持续粒子网络包归零。</p>
 */
@Environment(EnvType.CLIENT)
public class FoxFireballRenderer extends FlyingItemEntityRenderer<FoxFireballEntity> {

	public FoxFireballRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, 1.0F, true); // 与原注册参数一致：scale 1.0、自发光
	}

	@Override
	public void render(FoxFireballEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vcp, int light) {
		spawnTrailClient(entity);
		super.render(entity, yaw, tickDelta, matrices, vcp, light);
	}

	/** 拖尾粒子客户端自绘（几何与原服务端 spawnTrail 完全一致）。每 render 帧一次，火焰寿命 ~0.5s 自然衔接。 */
	private void spawnTrailClient(FoxFireballEntity entity) {
		ParticleManager pm = MinecraftClient.getInstance().particleManager;
		java.util.Random rnd = new java.util.Random();
		double x = entity.getX(), y = entity.getY(), z = entity.getZ();
		// 火焰球体：2 格直径内随机分布（普通火焰 6 + 魂火 4）
		for (int i = 0; i < 6; i++) {
			Vec3d p = randomInSphere(1.0, rnd);
			pm.addParticle(ParticleTypes.FLAME, x + p.x, y + p.y, z + p.z, 0, 0, 0.01);
		}
		for (int i = 0; i < 4; i++) {
			Vec3d p = randomInSphere(1.0, rnd);
			pm.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + p.x, y + p.y, z + p.z, 0, 0, 0.01);
		}
		// 岩浆火星：从火球表面随机迸出的小火花。
		if (rnd.nextFloat() < 0.3f) {
			Vec3d p = randomInSphere(0.4, rnd);
			pm.addParticle(ParticleTypes.LAVA, x + p.x, y + p.y, z + p.z, 0.003, 0.008, 0.003);
		}
		// 熔岩往下滴落：火球下方零星滴落的熔岩粒子。
		if (rnd.nextFloat() < 0.07f) {
			pm.addParticle(ParticleTypes.FALLING_LAVA,
					x + (rnd.nextDouble() - 0.5) * 1.0,
					y - 0.6 + (rnd.nextDouble() - 0.5) * 0.8,
					z + (rnd.nextDouble() - 0.5) * 1.0, 0, -0.1, 0.01);
		}
		// 尾部反向火焰 + 烟雾（速度反方向 0.5 格处；速度已编入生成包，客户端可取）
		Vec3d v = entity.getVelocity();
		Vec3d dir = v.lengthSquared() > 1.0e-6 ? v.normalize() : new Vec3d(0, 0, 1);
		Vec3d back = dir.multiply(-0.5);
		pm.addParticle(ParticleTypes.FLAME, x + back.x, y + back.y, z + back.z, 0.15, 0.15, 0.0);
		pm.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + back.x, y + back.y, z + back.z, 0.12, 0.12, 0.0);
		pm.addParticle(ParticleTypes.SMOKE, x + back.x * 1.5, y + back.y * 1.5, z + back.z * 1.5, 0.1, 0.1, 0.0);
	}

	private static Vec3d randomInSphere(double radius, java.util.Random rnd) {
		double u = rnd.nextDouble() * 2 - 1;
		double theta = rnd.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(1 - u * u);
		double len = radius * Math.cbrt(rnd.nextDouble());
		return new Vec3d(r * Math.cos(theta) * len, u * len, r * Math.sin(theta) * len);
	}
}
