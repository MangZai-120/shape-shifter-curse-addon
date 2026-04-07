package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;

/**
 * 金沙岚SP - 凋零金沙（主动①）
 * 以自身为中心向周围10格释放金沙风暴，
 * 对范围内所有非白名单生物施加凋零II效果（8秒）。
 * CD: 30秒（600tick）
 */
public class GoldenSandstormWitherSand {

	/**
	 * AoE半径
	 */
	private static final double RADIUS = 10.0;

	// ==================== 常量 ====================
	/**
	 * 凋零等级（0=I, 1=II）
	 */
	private static final int WITHER_AMPLIFIER = 1;
	/**
	 * 凋零持续时间（tick）
	 */
	private static final int WITHER_DURATION = 160; // 8秒
	/**
	 * CD时间（tick）
	 */
	private static final int COOLDOWN_TICKS = 600; // 30秒

	private GoldenSandstormWitherSand() {
	}

	/**
	 * 玩家按下技能键触发
	 */
	public static boolean execute(ServerPlayerEntity player) {
		// CD检查：使用共享CD资源
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD);
		if (cd > 0) {
			return false;
		}

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

		// 设置CD
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, COOLDOWN_TICKS);

		// 播放释放音效
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.5f, 0.5f);

		// 粒子效果：金色粒子风暴
		for (int i = 0; i < 80; i++) {
			double angle = Math.random() * Math.PI * 2;
			double dist = Math.random() * RADIUS;
			double px = player.getX() + Math.cos(angle) * dist;
			double pz = player.getZ() + Math.sin(angle) * dist;
			double py = player.getY() + Math.random() * 2.0;
			serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, net.minecraft.block.Blocks.SAND.getDefaultState()),
					px, py, pz, 1, 0, 0, 0, 0);
		}
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL,
				player.getX(), player.getY() + 1.0, player.getZ(), 30, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.02);

		// 获取范围内生物并施加凋零
		Box box = player.getBoundingBox().expand(RADIUS);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && e.squaredDistanceTo(player) <= RADIUS * RADIUS);

		for (LivingEntity target : targets) {
			// 白名单检查
			if (WhitelistUtils.isProtected(player, target)) continue;

			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER, false, true));

			// 命中粒子
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					5, 0.3, 0.3, 0.3, 0.02);
		}

		return true;
	}
}
