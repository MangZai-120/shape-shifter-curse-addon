package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * 魔法蜘蛛二段跳 - 服务端音效粒子广播。
 *
 * <p>跳跃本身由客户端调用原版 {@code LivingEntity.jump()} 完成（含疾跑前冲，动画由原版 v3 FSM 自动播放）；
 * 客户端在空中触发二段跳后发包到此，服务端校验形态 + 空中后广播二段跳音效与粒子，让附近玩家也能看到听到。
 * 「一次滞空一跳」的额度限制在客户端完成，服务端不再施加速度，避免与客户端权威移动冲突。
 */
public final class SpiderMagicDoubleJumpManager {

	private SpiderMagicDoubleJumpManager() {}

	/** 收到客户端二段跳发包：校验形态 + 空中后广播音效粒子（跳跃速度已由客户端原版 jump() 施加）。 */
	public static void onDoubleJump(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.SPIDER_MAGIC)) return;
		if (player.isOnGround()) return; // 地面首跳走 vanilla，不处理
		boolean lunge = player.isSprinting(); // 疾跑跳对应原版 jump() 的水平前冲，用更多粒子表现
		playEffects(player, lunge);
	}

	private static void playEffects(ServerPlayerEntity player, boolean lunge) {
		ServerWorld sw = (ServerWorld) player.getWorld();
		// 音效：沿用原版蜘蛛跳跃广播声（山羊长跳 + 青蛙长跳，与 form_spider_3 一致）
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 0.6f, 1.0f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_FROG_LONG_JUMP, SoundCategory.PLAYERS, 0.6f, 0.8f);
		// 粒子：跑跳前扑用更多云雾（对齐原版蜘蛛 16 个）；魔法蜘蛛额外加紫色魔法粒子
		int cloudCount = lunge ? 20 : 12;
		sw.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
				cloudCount, 0.3, 0.3, 0.3, 0.01);
		sw.spawnParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.2, player.getZ(),
				8, 0.4, 0.3, 0.4, 0.01);
	}
}
