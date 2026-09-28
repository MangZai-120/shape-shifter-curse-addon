package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.power.FailAwareActiveSelfPower;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐近战次要技能 - 瞬移攻击
 * 瞬移到10格范围内最多3个敌人身后攻击
 */
public class SnowFoxSpTeleportAttack {

	private static final ConcurrentHashMap<UUID, TeleportAttackData> ATTACKING_PLAYERS = new ConcurrentHashMap<>();
	private static final double RANGE = 10.0;          // 以下常量均为默认锚点；balance 可覆盖（abilities.snow_fox_sp_teleport）
	private static final int MAX_TARGETS = 3;
	private static final float BASE_DAMAGE = 6.0f;
	private static final float BONUS_DAMAGE = 3.0f;
	private static final int MANA_COST_SUCCESS = 30;
	private static final int MANA_COST_FAIL = 20;
	private static final int TELEPORT_INTERVAL = 10;
	private static final float DAMAGE_REDUCTION = 0.65f;
	private static final int TELEPORT_REGEN_LOCK_TICKS = 100; // 回能锁默认；balance regen_lock_ticks 可覆盖

	// 阶段 5：服务端权威快照读取
	private static final net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader BAL =
			new net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader("abilities.snow_fox_sp_teleport");
	// ==== NEW CODE: 使用FormIdentifiers（霜寒值读写已改走 ResourceBars，RESOURCE_ID 仅存于旧注释）====
	private static final Identifier REGEN_COOLDOWN_ID = FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN;

	private SnowFoxSpTeleportAttack() {
	}

	/**
	 * 执行瞬移攻击
	 * 门禁：power JSON（my_addon:fail_aware_active_self）原生 cooldown / fail_cooldown 字段管理，
	 * 本方法只负责效果与失败标记：无目标/蓝不够 → markFail()（按 fail_cooldown 进 CD）。
	 */
	private static final String SKILL_ID = "my_addon:form_snow_fox_sp_melee_secondary";

	public static boolean execute(ServerPlayerEntity player) {
		if (ATTACKING_PLAYERS.containsKey(player.getUuid())) {
			FailAwareActiveSelfPower.markFail();
			return false;
		}

		int currentMana = net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.get(player,
				net.jackcooper.shapeShifterCurseAddon.resource.BarKeys.SNOW_FOX);
		if (currentMana < BAL.i("mana_cost_fail", MANA_COST_FAIL)) {
			player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
			FailAwareActiveSelfPower.markFail();
			return false;
		}

		List<LivingEntity> targets = findTargets(player);

		if (targets.isEmpty()) {
			player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
			net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.consume(player,
					net.jackcooper.shapeShifterCurseAddon.resource.BarKeys.SNOW_FOX, BAL.i("mana_cost_fail", MANA_COST_FAIL));
			setRegenCooldown(player, BAL.i("regen_lock_ticks", TELEPORT_REGEN_LOCK_TICKS));
			FailAwareActiveSelfPower.markFail();
			return false;
		}

		if (currentMana < BAL.i("mana_cost_success", MANA_COST_SUCCESS)) {
			player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
			FailAwareActiveSelfPower.markFail();
			return false;
		}

		net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.consume(player,
				net.jackcooper.shapeShifterCurseAddon.resource.BarKeys.SNOW_FOX, BAL.i("mana_cost_success", MANA_COST_SUCCESS));
		setRegenCooldown(player, BAL.i("regen_lock_ticks", TELEPORT_REGEN_LOCK_TICKS));
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.begin(player, SKILL_ID);
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.released(player, SKILL_ID);

		Vec3d originalPos = player.getPos();
		float originalYaw = player.getYaw();
		float originalPitch = player.getPitch();

		TeleportAttackData data = new TeleportAttackData(
				originalPos, originalYaw, originalPitch,
				targets, 0, 0
		);
		data.castId = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.currentCastId(player, SKILL_ID);
        ATTACKING_PLAYERS.put(player.getUuid(), data);

		teleportToTarget(player, data);

		return true;
	}

	/**
	 * 每tick更新状态
	 */
	public static void tick(ServerPlayerEntity player) {
		TeleportAttackData data = ATTACKING_PLAYERS.get(player.getUuid());
		if (data == null) return;

		if (player.hasStatusEffect(SscAddon.PURIFIED)) {
			returnToOrigin(player, data);
			ATTACKING_PLAYERS.remove(player.getUuid());
			net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ended(player, SKILL_ID, data.castId);
			return;
		}

		data.ticksSinceLastTeleport++;

		player.setVelocity(0, 0, 0);
		player.velocityModified = true;

		if (data.ticksSinceLastTeleport >= BAL.i("teleport_interval", TELEPORT_INTERVAL)) {
			data.currentTargetIndex++;
			data.ticksSinceLastTeleport = 0;

			if (data.currentTargetIndex < data.targets.size()) {
				teleportToTarget(player, data);
			} else {
				returnToOrigin(player, data);
				ATTACKING_PLAYERS.remove(player.getUuid());
				net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ended(player, SKILL_ID, data.castId);
			}
		}
	}

	/**
	 * 瞬移到目标身后并攻击
	 */
	private static void teleportToTarget(ServerPlayerEntity player, TeleportAttackData data) {
		if (data.currentTargetIndex >= data.targets.size()) return;

		LivingEntity target = data.targets.get(data.currentTargetIndex);

		if (target.isDead() || target.isRemoved()) {
			return;
		}

		Vec3d targetPos = target.getPos();
		Vec3d targetLookDir = target.getRotationVector().normalize();
		Vec3d behindPos = targetPos.subtract(targetLookDir.multiply(1.5));

		player.teleport(behindPos.x, behindPos.y, behindPos.z);

		Vec3d toTarget = targetPos.subtract(behindPos).normalize();
		float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
		player.setYaw(yaw);
		player.setPitch(0);

		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

		if (player.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnDecorationParticles(serverWorld, player, ParticleTypes.REVERSE_PORTAL,
					player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
					20, 0.3, 0.5, 0.3, 0.05);
		}

		player.swingHand(player.getActiveHand());

		float damage = (float) BAL.d("base_damage", BASE_DAMAGE);

		StatusEffectInstance frostEffect = target.getStatusEffect(SscAddon.FROST_FREEZE);
		if (frostEffect != null) {
			damage += BAL.d("bonus_damage", BONUS_DAMAGE);
		}

		DamageSource source = player.getDamageSources().playerAttack(player);
		Vec3d oldVelocity = target.getVelocity();
		if (target.damage(source, damage)) {
			target.setVelocity(oldVelocity);
		}

		if (player.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
					target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
					15, 0.3, 0.3, 0.3, 0.1);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SWEEP_ATTACK,
					target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
					1, 0, 0, 0, 0);
		}

		player.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	/**
	 * 返回原位
	 */
	private static void returnToOrigin(ServerPlayerEntity player, TeleportAttackData data) {
		player.teleport(data.originalPos.x, data.originalPos.y, data.originalPos.z);
		player.setYaw(data.originalYaw);
		player.setPitch(data.originalPitch);
		player.setVelocity(0, 0, 0);
		player.velocityModified = true;

		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);

		if (player.getWorld() instanceof ServerWorld serverWorld) {
			ParticleUtils.spawnDecorationParticles(serverWorld, player, ParticleTypes.REVERSE_PORTAL,
					player.getX(), player.getY() + player.getHeight() / 2, player.getZ(),
					30, 0.3, 0.5, 0.3, 0.05);
		}
	}

	/**
	 * 查找范围内的目标
	 */
	private static List<LivingEntity> findTargets(ServerPlayerEntity player) {
		List<LivingEntity> result = new ArrayList<>();
		Box searchBox = player.getBoundingBox().expand(BAL.d("range", RANGE));

		List<LivingEntity> nearbyEntities = player.getWorld().getEntitiesByClass(
				LivingEntity.class, searchBox,
				entity -> entity != player &&
						!entity.isSpectator() &&
						entity.isAlive() &&
						!net.jackcooper.shapeShifterCurseAddon.spell.DomainManager.blocksTargeting(player, entity) &&
						player.squaredDistanceTo(entity) <= BAL.d("range", RANGE) * BAL.d("range", RANGE) &&
						!WhitelistUtils.isProtected(player, entity)
		);

		nearbyEntities.sort(Comparator.comparingDouble(player::squaredDistanceTo));

		for (int i = 0; i < Math.min(BAL.i("max_targets", MAX_TARGETS), nearbyEntities.size()); i++) {
			result.add(nearbyEntities.get(i));
		}

		return result;
	}

	/**
	 * 检查玩家是否正在瞬移攻击
	 */
	public static boolean isAttacking(ServerPlayerEntity player) {
		return ATTACKING_PLAYERS.containsKey(player.getUuid());
	}

	/**
	 * 玩家断线/死亡时清理所有状态，防止内存泄漏和重连后传送到错误位置
	 */
	public static void clearPlayer(java.util.UUID uuid) {
		ATTACKING_PLAYERS.remove(uuid);
	}

	/**
	 * 清除所有正在进行的传送攻击状态
	 */
	public static void clearAll() {
		ATTACKING_PLAYERS.clear();
	}

	/**
	 * 获取伤害减免系数（用于Mixin）
	 */
	public static float getDamageReduction(ServerPlayerEntity player) {
		if (isAttacking(player)) {
			return (float) BAL.d("damage_reduction", DAMAGE_REDUCTION);
		}
		return 0.0f;
	}

	/**
	 * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
	 * ==== NEW CODE: 使用PowerUtils ====
	 */
	public static void setRegenCooldown(ServerPlayerEntity player, int value) {
		PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, value);
	}

	/**
	 * 瞬移攻击数据
	 */
	private static class TeleportAttackData {
        long castId = -1;
		final Vec3d originalPos;
		final float originalYaw;
		final float originalPitch;
		final List<LivingEntity> targets;
		int currentTargetIndex;
		int ticksSinceLastTeleport;

		TeleportAttackData(Vec3d originalPos,
		                   float originalYaw, float originalPitch,
		                   List<LivingEntity> targets,
		                   int currentTargetIndex, int ticksSinceLastTeleport) {
			this.originalPos = originalPos;
			this.originalYaw = originalYaw;
			this.originalPitch = originalPitch;
			this.targets = targets;
			this.currentTargetIndex = currentTargetIndex;
			this.ticksSinceLastTeleport = ticksSinceLastTeleport;
		}
	}
}

