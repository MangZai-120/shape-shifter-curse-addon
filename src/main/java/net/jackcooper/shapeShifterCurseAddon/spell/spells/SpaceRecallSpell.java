package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 空间归途（空间系，蓝色基底，jackcooper）：3 秒读条后传送回绑定床（重生点）。
 * 施法前置校验：必须有重生点（canCast），否则拒绝（不耗法力不进 CD）。
 * 读条走原版 HUD 无字秒表风格简化为：施放瞬间延迟 60t 由
 * {@code SpaceRecallManager} 完成（期间移动/受伤打断，打断不返还法力但 CD 减半）。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/space_recall.json}：
 * 读条 7s（每级 -1s，L5=3s）/ cd 45s / 耗蓝 30（每级 +20%）。</p>
 */
public class SpaceRecallSpell extends Spell {

	/** 基础读条时长（tick）：7s，每级 -1s（L1=7s … L5=3s）。 */
	public static final int BASE_CHANNEL_TICKS = 140;
	/** 每级减少的读条（tick）：1s。 */
	public static final int CHANNEL_PER_LEVEL = 20;

	public SpaceRecallSpell() {
		super(new Identifier("ssc_addon", "space_recall"), SpellRarity.BLUE);
	}

	@Override
	public boolean canCast(ServerPlayerEntity caster) {
		// 必须有绑定重生点（床/重生锚）
		BlockPos spawnPos = caster.getSpawnPointPosition();
		return spawnPos != null;
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		if (!(caster.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		BlockPos spawnPos = caster.getSpawnPointPosition();
		if (spawnPos == null) {
			return; // 双保险
		}
		// 注册读条（服务端结算：移动/受伤打断，见 SpaceRecallManager；读条随等级 7s→3s）
		int channelTicks = Math.max(40, BASE_CHANNEL_TICKS - (level - 1) * CHANNEL_PER_LEVEL);
		net.jackcooper.shapeShifterCurseAddon.ability.SpaceRecallManager.start(caster, channelTicks);
		// 起手演出：星门环绕
		serverWorld.spawnParticles(ParticleTypes.PORTAL,
				caster.getX(), caster.getBodyY(0.8), caster.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
	}
}
