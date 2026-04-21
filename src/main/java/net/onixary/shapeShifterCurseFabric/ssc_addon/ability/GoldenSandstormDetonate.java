package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
 * 金沙岚SP - 引爆标记（主动②次要技能）
 * <p>
 * 引爆所有非绿色状态的侵蚀烙印标记：
 * - 每层造成5点物理伤害
 * - 自身每层回复10%最大生命值
 * - 3层目标先触发被动爆发（10%当前HP伤害，上限30，自愈10%），再引爆
 * - 引爆后目标进入绿色状态（10秒不可再叠层）
 * <p>
 * CD: 10秒（200tick）
 */
public class GoldenSandstormDetonate {

	/** CD时间（tick） */
	private static final int COOLDOWN_TICKS = 200; // 10秒

	private GoldenSandstormDetonate() {
	}

	/**
	 * 玩家按下次要技能键触发
	 */
	public static boolean execute(ServerPlayerEntity player) {
		// CD检查
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD);
		if (cd > 0) return false;

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return false;

		// 调用 ErosionBrand 的引爆方法
		int[] result = GoldenSandstormErosionBrand.detonateAll(player);
		int targets = result[0];
		int totalStacks = result[1];

		if (targets <= 0) {
			// 没有可引爆的目标，不进入CD
			return false;
		}

		// 设置CD
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, COOLDOWN_TICKS);

		// 释放音效
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 1.5f, 0.8f);

		// 自身周围粒子提示
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
				player.getX(), player.getY() + 1.0, player.getZ(),
				20 * totalStacks, 1.0, 1.0, 1.0, 0.1);

		return true;
	}
}
