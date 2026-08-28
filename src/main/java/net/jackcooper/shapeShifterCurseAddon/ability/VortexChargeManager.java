package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP 美西螈 - 漩涡蓄力技能（服务端状态机）。
 *
 * 按 sp_primary 开始蓄力；每 10 tick 扣 8 湿润值(air) 且伤害 +2（基础 0）；
 * 最大 4 秒(80t / 8 次)、最大消耗 60 湿润值；中途再按 sp_primary 立即释放、满 4 秒自动释放。
 * 释放：半径 3 范围物理伤害 = 已扣次数 × 2 + 缓慢 III + 击退；CD 15 秒(300t) 释放后才起算。
 *
 * vortex_state 资源（my_addon:form_axolotl_sp_vortex_impact_vortex_state）用作"蓄力中"标记并同步客户端：
 * 0 = 未蓄力，>0 = 蓄力中（值=已蓄力 tick 数）。客户端据此决定按键是"开始"还是"释放"。
 */
public final class VortexChargeManager {
	public static final Identifier VORTEX_STATE =
			new Identifier("my_addon", "form_axolotl_sp_vortex_impact_vortex_state");

	/**
	 * 涡流「移动免疫」实体类型 tag：命中的实体不被涡流吸附 / 击退（仍正常受伤）。
	 * <p>数据包路径 {@code data/my_addon/tags/entity_types/vortex_immune.json}，默认含原版 6 类
	 * boss / 重甲（铁傀儡·凋灵·末影龙·潜影贝·监守者·远古守卫者）；{@code "replace": false} 允许
	 * 其它数据包 / 模组把自家 boss 追加进来，无需改代码即可扩展兼容。
	 */
	private static final TagKey<EntityType<?>> VORTEX_IMMUNE =
			TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("my_addon", "vortex_immune"));

	private static final int AIR_PER_HIT = 8;
	private static final int MAX_AIR_SPENT = 60;
	private static final int MAX_TICKS = 80;     // 4 秒
	private static final int HIT_INTERVAL = 10;  // 每 10 tick 扣一次
	private static final int DAMAGE_PER_HIT = 2;
	private static final int CD_TICKS = 300;     // 15 秒
	private static final double RADIUS = 3.0;
	// ===== 蓄力期吸附（唯一吸附源：原 JSON pull_effect 的固定力度吸附已删，改由此处按击退抗性分档牵引）=====
	/** 吸附作用半径（与原 JSON 吸附触及范围一致） */
	private static final double PULL_RADIUS = 6.0;
	/** 基础吸附力度（朝向玩家的水平速度分量；稳态速度≈ 2×本值×分档系数，如太强/太弱调此值） */
	private static final double PULL_FORCE = 0.6;

	// ===== 动态粒子（青蓝/白：蓄力吸附 + 释放抛物线，全部服务端生成并广播给所有客户端） =====
	/** 青蓝色尘埃（漂浮，吸附与扩散着色用） */
	private static final net.minecraft.particle.DustParticleEffect CYAN_DUST =
			new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.20f, 0.62f, 0.92f), 1.6f);
	/** 白色尘埃（漂浮，吸附用） */
	private static final net.minecraft.particle.DustParticleEffect WHITE_DUST =
			new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.92f, 0.96f, 1.0f), 1.3f);

	private static final Map<UUID, ChargeState> CHARGING = new ConcurrentHashMap<>();

	private static final class ChargeState {
		int ticks = 0;
		int hits = 0;
		int airSpent = 0;
	}

	private VortexChargeManager() {
	}

	public static boolean isCharging(ServerPlayerEntity player) {
		return CHARGING.containsKey(player.getUuid());
	}

	/**
	 * 客户端本地玩家「涡流蓄力中」缓存标记：由 VortexChargeClient 每客户端 tick 更新一次，
	 * 供碰撞推挤 mixin（SscAddonLivingEntityMixin.pushAwayFrom）快速读取，避免每次实体碰撞都读 Apoli 资源。
	 * 仅客户端有意义（服务端恒 false，服务端走 {@link #isCharging} 查表）。
	 */
	private static volatile boolean clientLocalCharging = false;

	/** 客户端每 tick 更新本地玩家蓄力标记。 */
	public static void setClientLocalCharging(boolean charging) {
		clientLocalCharging = charging;
	}

	/** 碰撞推挤 mixin 读取：客户端本地玩家是否处于涡流蓄力。 */
	public static boolean isClientLocalCharging() {
		return clientLocalCharging;
	}

	/** 客户端发「开始蓄力」包时调用。 */
	public static void start(ServerPlayerEntity player) {
		if (CHARGING.containsKey(player.getUuid())) return;
		if (!FormUtils.isAxolotlSP(player)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // CD 中
		if (player.getAir() < AIR_PER_HIT) return; // 至少够扣一次
		CHARGING.put(player.getUuid(), new ChargeState());
		PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 1); // 标记蓄力中（客户端读 >0）
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.5f, 0.5f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.6f, 1.5f);
		sw.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.6);
		sw.spawnParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
		// 青/白粒子向中心吸附（漩涡起手）
		spawnAbsorbRing(sw, player.getX(), player.getY() + 1, player.getZ(), 16, 0.0);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		ChargeState s = CHARGING.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !FormUtils.isAxolotlSP(player)) {
			cancel(player); // 形态丢失/死亡 → 取消，不结算
			return;
		}
		s.ticks++;
		// 持续吸附漩涡（每 2 tick 一圈，相位随时间旋转 → 动态收束）
		if (s.ticks % 2 == 0) {
			spawnAbsorbRing((ServerWorld) player.getWorld(),
					player.getX(), player.getY() + 1, player.getZ(), 8, s.ticks * 0.35);
			// 蓄力期实体吸附：把范围内怪物朝玩家牵引，力度随击退抗性衰减（每级 -20%，免疫的吸不动）
			pullEntitiesDuringCharge((ServerWorld) player.getWorld(), player);
		}
		if (s.ticks % HIT_INTERVAL == 0) {
			if (s.airSpent < MAX_AIR_SPENT && player.getAir() >= AIR_PER_HIT) {
				int spend = Math.min(AIR_PER_HIT, MAX_AIR_SPENT - s.airSpent);
				player.setAir(player.getAir() - spend);
				s.airSpent += spend;
				s.hits++;
				ServerWorld sw = (ServerWorld) player.getWorld();
				sw.spawnParticles(ParticleTypes.BUBBLE,
						player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.6);
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.8f, 0.6f);
			} else {
				release(player); // air 不足或已扣满 60 → 自动释放
				return;
			}
		}
		// 注：不再每 tick 同步 vortex_state —— 客户端与 JSON 条件只读「>0」判断蓄力中，
		// start(1)/release(0)/cancel(0) 三个事件级同步已足够，每 tick 重发是纯带宽浪费。
		if (s.ticks >= MAX_TICKS) {
			release(player); // 满 4 秒自动释放
		}
	}

	/** 客户端发「释放」包 或 自动释放时调用。 */
	public static void release(ServerPlayerEntity player) {
		ChargeState s = CHARGING.remove(player.getUuid());
		PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 0);
		if (s == null) return;
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS); // CD 释放后起算
		int damage = s.hits * DAMAGE_PER_HIT;
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.2f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.5f, 0.5f);
		sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(),
				150, RADIUS, 1.0, RADIUS, 1.0);
		sw.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1, player.getZ(),
				8, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.1);
		// 仿 RC-4 药水破碎的水花爆开（与水矛落地同款）
		net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnWaterBurst(sw, player.getX(), player.getY() + 1, player.getZ(), 1.3);
		if (damage <= 0) return; // 一次都没蓄到，仅取消
		Box box = player.getBoundingBox().expand(RADIUS);
		for (Entity e : sw.getOtherEntities(player, box)) {
			if (!(e instanceof LivingEntity living)) continue;
			// 默认白名单：豁免玩家/宠物/白名单个体，不受涡流冲击伤害与控制
			if (net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.isProtected(player, living)) continue;

			// 伤害：所有受影响生物一律满额（用户定稿「伤害正常」，boss 也照常受伤）
			living.damage(player.getDamageSources().mobAttack(player), (float) damage);

			// 击退力度 = f(击退抗性等级, 是否为boss)：boss → 0（不击退不缓慢），普通怪按抗性分档
			double scale = getMovementForceScale(living);
			if (scale <= 0.0) continue;              // boss / 极高抗性：只受伤，不被移动
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2), player);
			Vec3d push = living.getPos().subtract(player.getPos());
			if (push.lengthSquared() < 1.0e-4) push = new Vec3d(0, 1, 0);
			push = push.normalize().multiply(0.8 * scale);
			living.setVelocity(push.x, 0.6 * scale, push.z);
			living.velocityModified = true;
		}
	}

	/** 取消蓄力（不结算伤害、不进 CD）。 */
	public static void cancel(ServerPlayerEntity player) {
		if (CHARGING.remove(player.getUuid()) != null) {
			PowerUtils.setResourceValueAndSync(player, VORTEX_STATE, 0);
		}
	}

	// ==================== Boss 判定 + 移动力度系数 ====================

	/**
	 * 判定一个生物是否「涡流移动免疫」（不被吸附 / 击退，但仍正常受伤）。
	 * <p>双重判定：
	 * <ol>
	 *   <li><b>击退抗性 ≥ 0.99</b>：自动命中铁傀儡·监守者·以及设了高抗性的模组 boss，无需登记。</li>
	 *   <li><b>实体类型 tag {@code my_addon:vortex_immune}</b>：补住原版击退抗性=0.0 却应免疫的
	 *       凋灵·末影龙·潜影贝·远古守卫者（默认已写进 tag），并允许模组把自家 boss 追加进 tag 扩展兼容。</li>
	 * </ol>
	 */
	private static boolean isVortexImmune(LivingEntity living) {
		return getKnockbackResistance(living) >= 0.99 || living.getType().isIn(VORTEX_IMMUNE);
	}

	/**
	 * 计算目标受涡流「移动类效果」（蓄力吸附 / 释放击退）的力度系数 [0.0, 1.0]。
	 * <p>依据用户定稿的两项判据：
	 * <ul>
	 *   <li><b>是否为 boss</b>：{@link #isVortexImmune} 命中 → 返回 0.0（完全不被移动，力度归零）。</li>
	 *   <li><b>击退抗性等级</b>：普通怪按「每 0.2 点抗性一级、每级 -20% 力度」分档
	 *       —— 0.0→1.0、0.2→0.8、0.4→0.6、0.6→0.4、0.8→0.2、≥1.0→0.0。</li>
	 * </ul>
	 * 吸附与击退共用此系数，保证两处力度口径一致。伤害不走此系数（由调用方按「伤害正常」满额结算）。
	 */
	private static double getMovementForceScale(LivingEntity living) {
		if (isVortexImmune(living)) return 0.0;
		int level = (int) Math.ceil(getKnockbackResistance(living) / 0.2); // 0→0, 0.2→1 ... 1.0→5
		if (level > 5) level = 5;
		return Math.max(0.0, 1.0 - level * 0.2);
	}

	// ==================== 蓄力期实体吸附 ====================

	/**
	 * 蓄力期把范围内怪物朝玩家牵引。
	 * <p>吸附力度依据 {@link #getMovementForceScale}（击退抗性等级 + 是否为 boss）：
	 * <ul>
	 *   <li>Boss / 重甲实体（铁傀儡·凋灵·末影龙·监守者·潜影贝·远古守卫者·高抗性模组 boss）→ 力度 0，不被吸附。</li>
	 *   <li>普通怪按击退抗性「每 0.2 点一级、每级 -20% 力度」分档衰减。
	 *       注：setVelocity 直接覆盖速度不经过 takeKnockback，故击退抗性不能自然兑底，靠力度系数显式衰减。</li>
	 *   <li>玩家 / 驯服宠物 / 白名单个体豁免（{@link WhitelistUtils#isProtected}）。</li>
	 * </ul>
	 * 每 2 tick 施加一次朝向玩家的水平速度，贴脸阈值内不再拉近（防震荡）。
	 */
	private static void pullEntitiesDuringCharge(ServerWorld sw, ServerPlayerEntity player) {
		Box box = player.getBoundingBox().expand(PULL_RADIUS);
		Vec3d playerPos = player.getPos();
		for (Entity e : sw.getOtherEntities(player, box)) {
			if (!(e instanceof LivingEntity living)) continue;
			// 白名单 / 玩家 / 宠物豁免
			if (WhitelistUtils.isProtected(player, living)) continue;

			// 吸附力度 = f(击退抗性等级, 是否为boss)：boss → 0（不吸），普通怪按抗性分档
			double scale = getMovementForceScale(living);
			if (scale <= 0.0) continue;              // 力度归零：boss 或极高抗性，吸不动

			Vec3d toPlayer = playerPos.subtract(living.getPos());
			// 不设贴脸阈值：允许怪物被吸到玩家身上后反复震荡（特色效果，用户定稿保留）
			// 朝向玩家的水平方向（忽略 Y，避免把怪吸到天上 / 地下）；normalize 对零向量返回 ZERO，无 NaN 风险
			Vec3d dir = new Vec3d(toPlayer.x, 0, toPlayer.z).normalize();
			double force = PULL_FORCE * scale;
			// 叠加朝向玩家的水平速度（不覆盖原有 Y，保留重力 / 跳跃）
			living.setVelocity(living.getVelocity().x * 0.5 + dir.x * force,
					living.getVelocity().y,
					living.getVelocity().z * 0.5 + dir.z * force);
			living.velocityModified = true;
		}
	}

	public static void onPlayerDisconnect(UUID uuid) {
		CHARGING.remove(uuid);
	}

	// ==================== 击退抗性读取 ====================

	/**
	 * 读取一个生物的击退抗性（0.0~1.0）。
	 * <p>1.20.1 的 LivingEntity 无 getKnockbackResistance() 方法，须走属性实例读取。
	 * 实体未注册该属性时返回 0.0（可正常被击退/吸附）。
	 */
	private static double getKnockbackResistance(LivingEntity living) {
		if (living.getAttributes().hasAttribute(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)) {
			net.minecraft.entity.attribute.EntityAttributeInstance krInst =
					living.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
			if (krInst != null) return krInst.getValue();
		}
		return 0.0;
	}

	// ==================== 粒子辅助 ====================

	/** 生成一个带速度的有向粒子（count=0 时 delta 即为速度向量，speed=1）。 */
	private static void spawnDirected(ServerWorld sw, net.minecraft.particle.ParticleEffect particle,
			double x, double y, double z, double vx, double vy, double vz) {
		sw.spawnParticles(particle, x, y, z, 0, vx, vy, vz, 1.0);
	}

	/** 蓄力期：在外圈生成青/白粒子，速度指向中心并带切向分量 → 向内吸附 + 旋转漩涡。 */
	private static void spawnAbsorbRing(ServerWorld sw, double cx, double cy, double cz, int count, double phase) {
		double r = 2.6;
		for (int i = 0; i < count; i++) {
			double ang = (Math.PI * 2 / count) * i + phase;
			double px = cx + Math.cos(ang) * r;
			double pz = cz + Math.sin(ang) * r;
			double py = cy + 0.2 + (i % 4) * 0.28;
			double inX = (cx - px) * 0.20;
			double inZ = (cz - pz) * 0.20;
			double tanX = -Math.sin(ang) * 0.10;
			double tanZ = Math.cos(ang) * 0.10;
			spawnDirected(sw, (i & 1) == 0 ? CYAN_DUST : WHITE_DUST,
					px, py, pz, inX + tanX, 0.04, inZ + tanZ);
		}
	}
}
