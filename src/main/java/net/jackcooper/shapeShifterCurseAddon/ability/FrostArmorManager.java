package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 寒棘狐被动「寒棘护体」：棘甲 + 反刺（服务端权威，多人一致）。
 *
 * <p><b>棘甲</b>：每根环绕冰锥使受到的近战伤害减少 4%（满 5 根 20%）——冰锥既是矛也是盾，
 * 发射后防御随之下跌，攻防自抉择。冰锥数实时读 {@link FrostSpikeManager}。</p>
 *
 * <p><b>反刺</b>：被生物近战命中时，攻击者叠 1 层「寒棘」（缓慢 I 2.5 秒 + 雪花粒子）；
 * 满 3 层触发<b>棘爆</b>：攻击者被冻结（FROST_FREEZE 1 秒）+ 受 2 点反伤，层数清零。
 * 内置 0.5 秒防连击刷层（同一攻击者 10 tick 内的连续命中只算 1 层）。</p>
 *
 * <p>白名单：默认白名单——白名单内个体（玩家及宠物/召唤物）不受反刺与棘爆影响。</p>
 */
public final class FrostArmorManager {

	/** 每根环绕冰锥的近战减伤比例。 */
	private static final float PER_THORN_REDUCTION = 0.04f;
	/** 反刺叠层上限：达到即触发棘爆并清零。 */
	private static final int BURST_AT = 3;
	/** 寒棘层有效期（tick）：超时未叠新层则清零重计。 */
	private static final int LAYER_EXPIRE_TICKS = 50;
	/** 同一攻击者两次命中叠层的最小间隔（tick），防连击快速刷满。 */
	private static final int RE_LAYER_GAP_TICKS = 10;
	/** 棘爆冻结时长（tick）。 */
	private static final int BURST_FREEZE_TICKS = 20;
	/** 棘爆反伤。 */
	private static final float BURST_DAMAGE = 2.0f;

	/** attackerUuid → 叠层状态。 */
	private static final Map<UUID, Layers> LAYERS = new HashMap<>();

	private static final class Layers {
		int count;
		long lastLayerGameTime = Long.MIN_VALUE; // 上次叠层的游戏时刻
		long lastHitGameTime = Long.MIN_VALUE;   // 上次命中的游戏时刻（防连击门）
	}

	private FrostArmorManager() {}

	/** 是否为「生物近战命中」：攻击者是活体且伤害源为近战系（玩家普攻 / 生物近战）。 */
	public static boolean isMeleeHit(DamageSource source) {
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof LivingEntity) || !(attacker instanceof net.minecraft.entity.mob.MobEntity)
				&& !(attacker instanceof net.minecraft.entity.player.PlayerEntity)) {
			return false;
		}
		return source.isOf(DamageTypes.PLAYER_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	/**
	 * 棘甲：受害者是寒棘狐且受近战伤害 → 按环绕冰锥数减伤。
	 * 挂 LivingEntity.damage 的 applyDamage 调用点（ModifyArgs），不吞事件、不吞击退。
	 *
	 * @return 修正后的伤害值
	 */
	public static float applyArmor(ServerPlayerEntity victim, DamageSource source, float amount) {
		if (!isMeleeHit(source)) return amount;
		int thorns = FrostSpikeManager.getHoverCount(victim);
		if (thorns <= 0) return amount;
		float reduction = Math.min(0.2f, thorns * PER_THORN_REDUCTION);
		return amount * (1.0f - reduction);
	}

	/**
	 * 反刺入口：寒棘狐被近战命中（伤害已实际生效后调用）→ 攻击者叠层 / 满 3 层棘爆。
	 * 在 RETURN 注入里调用，天然避开 damage 栈内重入；反伤经 server.execute 再延迟一帧双保险。
	 */
	public static void onFrostspineMeleeHit(ServerPlayerEntity victim, DamageSource source) {
		if (!isMeleeHit(source)) return;
		Entity raw = source.getAttacker();
		if (!(raw instanceof LivingEntity attacker) || attacker == victim) return;
		// 白名单：默认白名单内个体不受反刺
		if (WhitelistUtils.isProtected(victim, attacker)) return;
		if (!(victim.getWorld() instanceof ServerWorld sw)) return;
		long now = sw.getTime();

		Layers l = LAYERS.computeIfAbsent(attacker.getUuid(), k -> new Layers());
		// 防连击门：同一攻击者 10t 内重复命中不叠层
		if (now - l.lastHitGameTime < RE_LAYER_GAP_TICKS) return;
		l.lastHitGameTime = now;
		l.count++;
		l.lastLayerGameTime = now;

		if (l.count >= BURST_AT) {
			// 棘爆：冻结 + 反伤 + 清层
			l.count = 0;
			final LivingEntity fAttacker = attacker;
			sw.getServer().execute(() -> {
				if (!fAttacker.isAlive() || fAttacker.isRemoved()) return;
				fAttacker.addStatusEffect(new StatusEffectInstance(
						SscAddon.FROST_FREEZE, BURST_FREEZE_TICKS, 0, false, true, true), victim);
				// 反伤走独立 magic 源（无 attacker=寒棘狐本人）→ 不会在攻击者身上再触发反刺链
				fAttacker.damage(fAttacker.getDamageSources().create(
						net.minecraft.registry.RegistryKey.of(
								net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
								new net.minecraft.util.Identifier("my_addon", "thorn_burst")),
						victim), BURST_DAMAGE);
			});
			// 棘爆反馈：碎冰音 + 雪花爆裂粒子（攻击者处）
			sw.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 0.7f);
			sw.spawnParticles(ParticleTypes.SNOWFLAKE,
					attacker.getX(), attacker.getBodyY(0.5), attacker.getZ(), 18, 0.3, 0.4, 0.3, 0.05);
			sw.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 0.7f, 1.0f);
		} else {
			// 叠层反馈：缓慢 I 2.5s（随层递增显示）+ 少量雪花粒子
			attacker.addStatusEffect(new StatusEffectInstance(
					net.minecraft.entity.effect.StatusEffects.SLOWNESS, 50, 0, false, true, true), victim);
			sw.spawnParticles(ParticleTypes.SNOWFLAKE,
					attacker.getX(), attacker.getBodyY(0.5), attacker.getZ(), 5, 0.2, 0.3, 0.2, 0.02);
		}
	}

	/** 每服务端 tick：过期层清理（超时未叠新层清零）。挂 SscAddonServerEvents 世界 tick。 */
	public static void tick(ServerWorld world) {
		if (LAYERS.isEmpty()) return;
		long now = world.getTime();
		Iterator<Map.Entry<UUID, Layers>> it = LAYERS.entrySet().iterator();
		while (it.hasNext()) {
			Layers l = it.next().getValue();
			if (l.count > 0 && now - l.lastLayerGameTime > LAYER_EXPIRE_TICKS) {
				l.count = 0;
			}
			// 全冷层且长期无命中 → 直接移除防 map 膨胀
			if (l.count == 0 && now - l.lastHitGameTime > 1200) {
				it.remove();
			}
		}
	}

	/** 攻击者断线 / 世界卸载清理。 */
	public static void clearPlayer(UUID uuid) {
		LAYERS.remove(uuid);
	}

	/** 服务器停机清理。 */
	public static void clearAll() {
		LAYERS.clear();
	}
}
