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
 * 空间归途（空间系，蓝色基底，jackcooper）：特殊一级，统一读条 8 秒后传送回绑定床（重生点）。
 * 施法前置校验：必须有重生点（canCast），否则拒绝（不耗法力不进 CD）。
 * 读条生命周期由统一管理器负责：禁主动走动/跳跃，转身/视角不受限（原版机制）；打断仅限受伤或自己再次长按施法键 1 秒，移动不算打断。
 * 读条完成 → {@code SpaceRecallManager.completeNow} 传送结算。
 *
 * <p>数值外置 {@code data/ssc_addon/spells/space_recall.json}：
 * 读条 8s / cd 60s（L5 30s，每级 -7.5s）/ 耗蓝 60（每级 +20%）。</p>
 */
public class SpaceRecallSpell extends Spell {

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
		net.jackcooper.shapeShifterCurseAddon.ability.SpaceRecallManager.completeNow(caster);
		// 起手演出：星门环绕
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(serverWorld, caster, ParticleTypes.PORTAL,
				caster.getX(), caster.getBodyY(0.8), caster.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
		serverWorld.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
	}
}
