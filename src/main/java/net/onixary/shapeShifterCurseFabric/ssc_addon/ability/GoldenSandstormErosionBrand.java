package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.ErosionSandPrismItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitheredSandRingItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金沙岚SP - 侵蚀烙印（被动标记系统）
 *
 * 近战攻击非白名单目标时自动叠加侵蚀烙印，每秒最多叠1层，每个目标最多3层。
 * 烙印持续10秒（每次叠加刷新计时器）。
 *
 * 层数颜色对应（用于entity_glow）：
 * - 1层 → 黄色
 * - 2层 → 橙色
 * - 3层 → 红色
 * - 绿色状态 → 已触发被动爆发，5秒内不可再次叠层（绿色标志持续10秒）
 *
 * 第4次攻击（3层满后继续攻击）触发被动爆发：
 * - 造成目标当前生命值20%的伤害（上限20）
 * - 自身回复10%最大生命值
 * - 清除所有层数，进入绿色状态（5秒不可叠层，绿色标志持续10秒）
 * - 绿色状态5秒冷却期后可重新叠层
 *
 * 引爆（次要技能）：
 * - 造成目标当前生命值20%的伤害（上限20）
 * - 自身回复已损生命值的20%
 */
public class GoldenSandstormErosionBrand {

	// ==================== 常量 ====================
	/** 烙印持续时间（tick），每次叠加刷新 */
	private static final int BRAND_DURATION = 200; // 10秒
	/** 绿色状态持续时间（tick），仅用于视觉标记 */
	private static final int GREEN_DURATION = 200; // 10秒
	/** 爆发后叠层冷却时间（tick），冷却结束后可重新叠层 */
	private static final int BRAND_STACK_COOLDOWN = 100; // 5秒
	/** 最大叠加层数 */
	private static final int MAX_STACKS = 3;
	/** 被动爆发生命百分比伤害 */
	private static final float BURST_HP_PERCENT = 0.20f;
	/** 被动爆发伤害上限 */
	private static final float BURST_DAMAGE_CAP = 20.0f;
	/** 被动爆发自愈百分比 */
	private static final float BURST_HEAL_PERCENT = 0.10f;
	/** 叠层冷却（tick），同一目标1秒内最多叠1层 */
	private static final long STACK_COOLDOWN = 20L;
	/** 蚀沙棱晶装备时的叠层冷却（tick），1.3秒 */
	private static final long STACK_COOLDOWN_WITH_PRISM = 26L;
	/** 枯沙指环装备时的被动爆发伤害上限（+30%） */
	private static final float BURST_DAMAGE_CAP_WITH_RING = 26.0f;
	/** 蚀沙棱晶扩散伤害范围（格） */
	private static final double SPREAD_RANGE = 5.0;
	/** 扩散叠标记半径（格） */
	private static final double SPLASH_BRAND_RANGE = 4.0;
	/** 引爆自愈：回复已损生命值的百分比 */
	private static final float DETONATE_HEAL_LOST_PERCENT = 0.20f;
	/** 蚀沙棱晶：扩散伤害占引爆伤害的比例（40%） */
	private static final float PRISM_SPLASH_PERCENT = 0.40f;

	// ==================== 自定义伤害类型 ====================
	/** 被动爆发伤害类型（绕过近战攻击的伤害免疫帧） */
	private static final RegistryKey<DamageType> CURSED_BURST_KEY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("my_addon", "cursed_burst"));
	/** 引爆伤害类型（绕过被动爆发的伤害免疫帧） */
	private static final RegistryKey<DamageType> CURSED_EROSION_KEY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("my_addon", "cursed_erosion"));

	// ==================== 网络同步 ====================
	/** S2C 包ID：同步烙印颜色数据到客户端 */
	public static final Identifier PACKET_BRAND_SYNC = new Identifier("ssc_addon", "erosion_brand_sync");
	/** 需要同步的玩家标记集合 */
	private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

	// ==================== 状态追踪 ====================
	/** 玩家UUID -> { 目标UUID -> 烙印状态 } */
	private static final ConcurrentHashMap<UUID, Map<UUID, BrandState>> ACTIVE_BRANDS = new ConcurrentHashMap<>();

	private GoldenSandstormErosionBrand() {
	}

	// ==================== 饰品检查 ====================

	/** 检查玩家是否装备了蚀沙棱晶（双重检测：tick追踪 + TrinketsApi查询） */
	private static boolean hasErosionPrism(ServerPlayerEntity player) {
		// 方法1：tick回调追踪
		if (ErosionSandPrismItem.isEquippedBy(player)) return true;
		// 方法2：直接查询TrinketsApi（后备方案）
		try {
			return TrinketsApi.getTrinketComponent(player)
					.map(c -> c.isEquipped(stack -> stack.getItem() instanceof ErosionSandPrismItem))
					.orElse(false);
		} catch (Exception e) {
			return false;
		}
	}

	/** 检查玩家是否装备了枯沙指环（双重检测：tick追踪 + TrinketsApi查询） */
	private static boolean hasWitheredRing(ServerPlayerEntity player) {
		// 方法1：tick回调追踪
		if (WitheredSandRingItem.isEquippedBy(player)) return true;
		// 方法2：直接查询TrinketsApi（后备方案）
		try {
			return TrinketsApi.getTrinketComponent(player)
					.map(c -> c.isEquipped(stack -> stack.getItem() instanceof WitheredSandRingItem))
					.orElse(false);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 近战攻击时调用 - 自动叠加烙印
	 * 该方法由 SscAddonActions 的 erosion_brand_hit biEntity action 触发
	 */
	public static void onPlayerAttack(ServerPlayerEntity player, LivingEntity target) {
		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return;
		// 近战攻击不检查白名单（允许对白名单目标叠标记）
		// 白名单仅限制扩散和引爆的叠标记

		UUID playerUuid = player.getUuid();
		UUID targetUuid = target.getUuid();
		long currentTick = serverWorld.getTime();

		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.computeIfAbsent(playerUuid, k -> new HashMap<>());
		BrandState state = playerBrands.get(targetUuid);

		if (state != null && state.greenState) {
			// 叠层冷却期间（前5秒）：不叠层，不触发被动爆发
			if (currentTick < state.stackCooldownExpiryTick) {
				return;
			}
			// 冷却结束（5-10秒），取消绿色状态，从0层开始正常叠层
			state.greenState = false;
			state.stacks = 0;
		}
		// 标记需要同步
		markDirty(playerUuid);

		if (state != null && state.stacks >= MAX_STACKS) {
			// 已满3层，第4次攻击触发被动爆发
			// !! 必须先重置状态再造成伤害，否则 target.damage() 会通过 Apoli action 递归调用 onPlayerAttack
			state.stacks = 0;
			state.greenState = true;
			state.greenExpiryTick = currentTick + GREEN_DURATION;			state.stackCooldownExpiryTick = currentTick + BRAND_STACK_COOLDOWN;			state.expiryTick = currentTick + GREEN_DURATION;

			// 同步标记效果到客户端（放在伤害前确保即时生效）
			updateTargetMarker(target, state, currentTick);

			// 现在安全地造成伤害
			triggerPassiveBurst(player, target, serverWorld);

			// 蚀沙棱晶：被动爆发也触发扩散（白名单目标不触发扩散）
			if (hasErosionPrism(player) && !WhitelistUtils.isProtected(player, target)) {
				float burstDmg = Math.min(target.getHealth() * BURST_HP_PERCENT, hasWitheredRing(player) ? BURST_DAMAGE_CAP_WITH_RING : BURST_DAMAGE_CAP);
				if (burstDmg < 1.0f) burstDmg = 1.0f;
				float splashDmg = burstDmg * PRISM_SPLASH_PERCENT;
				System.out.println("[SSC_ADDON][BURST] 被动爆发触发扩散: splashDmg=" + splashDmg + ", center=" + target.getPos() + ", range=" + SPREAD_RANGE);
				splashDamageToNearby(player, serverWorld, target.getPos(), splashDmg, target);
			}

			// 爆发音效和粒子
			serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.38f, 1.2f);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					30, 0.5, 0.5, 0.5, 0.1);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					1, 0, 0, 0, 0);
			return;
		}

		// 正常叠层
		if (state == null) {
			state = new BrandState();
			playerBrands.put(targetUuid, state);
		}

		// 检查叠层冷却（蚀沙棱晶：1.3秒，默认：1秒）
		long effectiveCooldown = hasErosionPrism(player) ? STACK_COOLDOWN_WITH_PRISM : STACK_COOLDOWN;
		if (currentTick - state.lastStackTick < effectiveCooldown) {
			// 冷却中，刷新持续时间但不叠层
			state.expiryTick = currentTick + BRAND_DURATION;
			updateTargetMarker(target, state, currentTick); // 刷新标记持续时间
			return;
		}

		state.stacks = Math.min(state.stacks + 1, MAX_STACKS);
		state.lastStackTick = currentTick;
		state.expiryTick = currentTick + BRAND_DURATION;

		// 同步标记效果到客户端
		updateTargetMarker(target, state, currentTick);

		// 叠加粒子提示
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
				target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
				5 * state.stacks, 0.2, 0.2, 0.2, 0.05);

		// 叠加音效
		serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 0.8f + state.stacks * 0.2f);
	}

	/**
	 * 触发被动爆发 - 造成20%当前生命伤害（上限20），自我回复10%最大生命
	 */
	private static void triggerPassiveBurst(ServerPlayerEntity player, LivingEntity target, ServerWorld serverWorld) {
		// 计算伤害：目标当前生命值的20%（枯沙指环：上限26，默认：上限20）
		boolean hasRing = hasWitheredRing(player);
		float cap = hasRing ? BURST_DAMAGE_CAP_WITH_RING : BURST_DAMAGE_CAP;
		float rawDamage = target.getHealth() * BURST_HP_PERCENT;
		float damage = Math.min(rawDamage, cap);
		if (damage < 1.0f) damage = 1.0f;
		System.out.println("[SSC_ADDON][BURST] 被动爆发: target=" + target.getType().getTranslationKey() + ", targetHP=" + target.getHealth() + "/" + target.getMaxHealth() + ", rawDmg=" + rawDamage + ", cap=" + cap + "(ring=" + hasRing + "), finalDmg=" + damage + ", timeUntilRegen=" + target.timeUntilRegen);

		// 造成自定义伤害（绕过近战攻击的伤害免疫帧）
		Vec3d oldVelocity = target.getVelocity();
		target.timeUntilRegen = 0; // 重置伤害免疫计时器
		boolean damaged = target.damage(target.getDamageSources().create(CURSED_BURST_KEY, player), damage);
		System.out.println("[SSC_ADDON][BURST] 被动爆发结果: damaged=" + damaged + ", newHP=" + target.getHealth() + ", damageType=cursed_burst");
		if (damaged) {
			target.setVelocity(oldVelocity); // 不击退
		}

		// 自我回复10%最大生命值
		float healAmount = player.getMaxHealth() * BURST_HEAL_PERCENT;
		player.heal(healAmount);
		System.out.println("[SSC_ADDON][BURST] 自愈: healAmount=" + healAmount + ", playerHP=" + player.getHealth() + "/" + player.getMaxHealth());
	}

	/**
	 * 次要技能引爆 - 引爆所有非绿色标记的目标
	 * @param player 施法者
	 * @return 引爆信息：[引爆目标数, 总层数]
	 */
	public static int[] detonateAll(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		if (playerBrands == null || playerBrands.isEmpty()) return new int[]{0, 0};

		if (!(player.getWorld() instanceof ServerWorld serverWorld)) return new int[]{0, 0};

		// 预先检查饰品状态（避免循环内重复检查）
		boolean hasRing = hasWitheredRing(player);
		boolean hasPrism = hasErosionPrism(player);
		float cap = hasRing ? BURST_DAMAGE_CAP_WITH_RING : BURST_DAMAGE_CAP;
		System.out.println("[SSC_ADDON][DETONATE] hasPrism=" + hasPrism + ", hasRing=" + hasRing + ", cap=" + cap + ", targets=" + playerBrands.size());
		int totalTargets = 0;
		int totalStacks = 0;

		Iterator<Map.Entry<UUID, BrandState>> it = playerBrands.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, BrandState> entry = it.next();
			BrandState state = entry.getValue();

			// 绿色状态的目标不参与引爆
			if (state.greenState || state.stacks <= 0) {
				System.out.println("[SSC_ADDON][DETONATE] 跳过目标 " + entry.getKey() + ": greenState=" + state.greenState + ", stacks=" + state.stacks);
				continue;
			}

			LivingEntity target = findEntity(serverWorld, entry.getKey());
			if (target == null || !target.isAlive()) {
				System.out.println("[SSC_ADDON][DETONATE] 跳过目标 " + entry.getKey() + ": target=" + (target == null ? "null" : "dead"));
				it.remove();
				continue;
			}
			System.out.println("[SSC_ADDON][DETONATE] 处理目标: " + target.getType().getTranslationKey() + ", stacks=" + state.stacks + ", HP=" + target.getHealth() + "/" + target.getMaxHealth());

			// 如果目标有3层，先触发被动爆发（不消耗层数）
			if (state.stacks >= MAX_STACKS) {
				triggerPassiveBurst(player, target, serverWorld);
				// 被动爆发的额外粒子
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
						target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
						1, 0, 0, 0, 0);
			}

			totalTargets++;
			totalStacks += state.stacks;

			// 引爆伤害 = 目标当前生命值20%（上限20/26）
			float rawDetonateDmg = target.getHealth() * BURST_HP_PERCENT;
			float detonateDamage = Math.min(rawDetonateDmg, cap);
			if (detonateDamage < 1.0f) detonateDamage = 1.0f;

			// 蚀沙棱晶：主目标承受60%，40%扩散给周围
			float mainDamage = hasPrism ? detonateDamage * (1.0f - PRISM_SPLASH_PERCENT) : detonateDamage;
			System.out.println("[SSC_ADDON][DETONATE] 引爆伤害计算: rawDmg=" + rawDetonateDmg + ", capped=" + detonateDamage + ", mainDmg=" + mainDamage + ", hasPrism=" + hasPrism + ", timeUntilRegen=" + target.timeUntilRegen);
			Vec3d oldVelocity = target.getVelocity();
			target.timeUntilRegen = 0; // 重置伤害免疫计时器
			boolean detonateResult = target.damage(target.getDamageSources().create(CURSED_EROSION_KEY, player), mainDamage);
			System.out.println("[SSC_ADDON][DETONATE] 引爆伤害结果: damaged=" + detonateResult + ", newHP=" + target.getHealth() + ", damageType=cursed_erosion");
			if (detonateResult) {
				target.setVelocity(oldVelocity);
			}

			// 蚀沙棱晶：40%引爆伤害扩散给周围5格内的生物
			if (hasPrism) {
				float splashDamage = detonateDamage * PRISM_SPLASH_PERCENT;
				System.out.println("[SSC_ADDON][DETONATE] 触发扩散: splashDmg=" + splashDamage + ", center=" + target.getPos() + ", range=" + SPREAD_RANGE);
				splashDamageToNearby(player, serverWorld, target.getPos(), splashDamage, target);
			} else {
				System.out.println("[SSC_ADDON][DETONATE] 未装备棱晶，跳过扩散");
			}

			// 引爆粒子
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					20 * state.stacks, 0.5, 0.5, 0.5, 0.1);

			// 进入绿色状态
			long currentTick = serverWorld.getTime();
			state.stacks = 0;
			state.greenState = true;
			state.greenExpiryTick = currentTick + GREEN_DURATION;
			state.stackCooldownExpiryTick = currentTick + BRAND_STACK_COOLDOWN;
			state.expiryTick = currentTick + GREEN_DURATION;

			// 同步标记效果到客户端
			updateTargetMarker(target, state, currentTick);
		}

		// 自我回复：已损生命值的20%
		if (totalTargets > 0) {
			float lostHealth = player.getMaxHealth() - player.getHealth();
			if (lostHealth > 0) {
				float healAmount = lostHealth * DETONATE_HEAL_LOST_PERCENT;
				player.heal(healAmount);
				System.out.println("[SSC_ADDON][DETONATE] 引爆自愈: lostHP=" + lostHealth + ", heal=" + healAmount + ", playerHP=" + player.getHealth() + "/" + player.getMaxHealth());
			}
			markDirty(player.getUuid());
		}
		System.out.println("[SSC_ADDON][DETONATE] 引爆完成: totalTargets=" + totalTargets + ", totalStacks=" + totalStacks);

		return new int[]{totalTargets, totalStacks};
	}

	/**
	 * 蚀沙棱晶效果 - 对引爆/爆发目标周围5格内的非白名单生物造成扩散伤害，
	 * 并对4格半径球内的目标额外叠1层烙印。若叠到满3层则触发被动爆发并重置层数。
	 */
	private static void splashDamageToNearby(ServerPlayerEntity player, ServerWorld serverWorld, Vec3d center, float damage, LivingEntity excludeTarget) {
		UUID playerUuid = player.getUuid();
		UUID excludeUuid = excludeTarget != null ? excludeTarget.getUuid() : null;
		System.out.println("[SSC_ADDON][SPLASH] === 扩散开始 === center=" + center + ", damage=" + damage + ", exclude=" + (excludeTarget != null ? excludeTarget.getType().getTranslationKey() : "null"));

		// 确保最低伤害为1
		if (damage < 1.0f) damage = 1.0f;

		// 使用较大的范围搜索（取伤害范围和叠标记范围的较大值）
		double maxRange = Math.max(SPREAD_RANGE, SPLASH_BRAND_RANGE);
		Box searchBox = new Box(
				center.x - maxRange, center.y - maxRange, center.z - maxRange,
				center.x + maxRange, center.y + maxRange, center.z + maxRange
		);

		List<Entity> allEntities = serverWorld.getOtherEntities(player, searchBox);
		System.out.println("[SSC_ADDON][SPLASH] getOtherEntities返回数量: " + allEntities.size());

		DamageSource splashDamageSource = player.getDamageSources().create(CURSED_EROSION_KEY, player);
		long currentTick = serverWorld.getTime();
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.computeIfAbsent(playerUuid, k -> new HashMap<>());
		int hitCount = 0;

		// 收集需要触发爆发的目标（避免在遍历中修改集合）
		List<LivingEntity> burstTargets = new ArrayList<>();

		for (Entity entity : allEntities) {
			if (!(entity instanceof LivingEntity living)) continue;
			if (!living.isAlive()) continue;
			if (living.getUuid().equals(playerUuid) || living.getUuid().equals(excludeUuid)) continue;
			if (WhitelistUtils.isProtected(player, living)) continue;

			double distSq = living.squaredDistanceTo(center);

			// 伤害范围检查（5格）
			if (distSq <= SPREAD_RANGE * SPREAD_RANGE) {
				System.out.println("[SSC_ADDON][SPLASH]   对 " + living.getType().getTranslationKey() + " 造成 " + damage + " 伤害 (HP=" + living.getHealth() + ")");
				Vec3d oldVelocity = living.getVelocity();
				living.timeUntilRegen = 0;
				boolean damaged = living.damage(splashDamageSource, damage);
				if (damaged) {
					living.setVelocity(oldVelocity);
					hitCount++;
				}
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
						living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ(),
						8, 0.3, 0.3, 0.3, 0.05);
			}

			// 叠标记范围检查（4格半径）
			if (distSq <= SPLASH_BRAND_RANGE * SPLASH_BRAND_RANGE) {
				UUID livingUuid = living.getUuid();
				BrandState state = playerBrands.get(livingUuid);

				// 绿色状态的目标：检查叠层冷却是否结束
				if (state != null && state.greenState) {
					if (currentTick < state.stackCooldownExpiryTick) {
						System.out.println("[SSC_ADDON][SPLASH]   " + living.getType().getTranslationKey() + " 叠层冷却中，跳过叠标记");
						continue;
					}
					// 冷却结束，取消绿色状态，从0开始叠层
					state.greenState = false;
					state.stacks = 0;
				}

				if (state == null) {
					state = new BrandState();
					playerBrands.put(livingUuid, state);
				}

				state.stacks = Math.min(state.stacks + 1, MAX_STACKS);
				state.lastStackTick = currentTick;
				state.expiryTick = currentTick + BRAND_DURATION;
				System.out.println("[SSC_ADDON][SPLASH]   " + living.getType().getTranslationKey() + " 叠标记至 " + state.stacks + " 层");

				// 满3层触发爆发
				if (state.stacks >= MAX_STACKS) {
					state.stacks = 0;
					state.greenState = true;
					state.greenExpiryTick = currentTick + GREEN_DURATION;
					state.stackCooldownExpiryTick = currentTick + BRAND_STACK_COOLDOWN;
					state.expiryTick = currentTick + GREEN_DURATION;
					updateTargetMarker(living, state, currentTick);
					burstTargets.add(living);
					System.out.println("[SSC_ADDON][SPLASH]   " + living.getType().getTranslationKey() + " 满3层，将触发爆发");
				} else {
					updateTargetMarker(living, state, currentTick);
				}
			}
		}

		// 延迟处理满3层的爆发（避免遍历中修改状态冲突）
		for (LivingEntity burstTarget : burstTargets) {
			triggerPassiveBurst(player, burstTarget, serverWorld);
			serverWorld.playSound(null, burstTarget.getX(), burstTarget.getY(), burstTarget.getZ(),
					SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.38f, 1.2f);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
					burstTarget.getX(), burstTarget.getY() + burstTarget.getHeight() * 0.5, burstTarget.getZ(),
					30, 0.5, 0.5, 0.5, 0.1);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLASH,
					burstTarget.getX(), burstTarget.getY() + burstTarget.getHeight() * 0.5, burstTarget.getZ(),
					1, 0, 0, 0, 0);
		}

		markDirty(playerUuid);

		System.out.println("[SSC_ADDON][SPLASH] === 扩散结束 === 命中: " + hitCount + ", 爆发: " + burstTargets.size());

		if (hitCount > 0) {
			serverWorld.playSound(null, center.x, center.y, center.z,
					SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.6f, 0.6f);
		}
	}

	/**
	 * 获取指定玩家对指定目标的烙印颜色
	 * @return "yellow"/"orange"/"red"/"green"/null
	 */
	public static String getBrandColor(UUID playerUuid, UUID targetUuid) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(playerUuid);
		if (playerBrands == null) return null;
		return getColorFromState(playerBrands.get(targetUuid));
	}

	/**
	 * 检查指定玩家对指定目标是否有某颜色的烙印
	 * 提供给 BiEntity 条件使用
	 */
	public static boolean hasColor(UUID playerUuid, UUID targetUuid, String color) {
		String currentColor = getBrandColor(playerUuid, targetUuid);
		return color.equals(currentColor);
	}

	/**
	 * 每tick更新 - 清理过期烙印
	 */
	public static void tick(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		if (playerBrands == null) return;

		// 形态检查
		if (!FormUtils.isGoldenSandstormSP(player)) {
			// 脱离形态，清理所有目标的标记效果
			if (player.getWorld() instanceof ServerWorld serverWorld) {
				for (UUID targetUuid : playerBrands.keySet()) {
					LivingEntity target = findEntity(serverWorld, targetUuid);
					if (target != null) removeAllBrandMarkers(target);
				}
			}
			ACTIVE_BRANDS.remove(player.getUuid());
			DIRTY_PLAYERS.remove(player.getUuid());
			// 发送空同步包到客户端，清除发光缓存（防止切回形态时残留旧数据）
			try {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeInt(0);
				ServerPlayNetworking.send(player, PACKET_BRAND_SYNC, buf);
			} catch (Exception ignored) {
				// 发送失败时忽略
			}
			return;
		}

		long currentTick = player.getServerWorld().getTime();
		ServerWorld serverWorld = player.getServerWorld();
		boolean[] hadExpiry = {false};
		playerBrands.entrySet().removeIf(entry -> {
			BrandState state = entry.getValue();
			boolean expired = false;
			// 绿色状态过期检查
			if (state.greenState && currentTick >= state.greenExpiryTick) {
				expired = true;
			}
			// 普通烙印过期检查
			if (!state.greenState && currentTick >= state.expiryTick) {
				expired = true;
			}
			if (expired) {
				hadExpiry[0] = true;
				// 移除目标的标记效果
				LivingEntity target = findEntity(serverWorld, entry.getKey());
				if (target != null) removeAllBrandMarkers(target);
			}
			return expired;
		});
		if (hadExpiry[0]) {
			markDirty(player.getUuid());
		}

		// 如果该玩家没有任何活跃烙印了，清除整个map
		if (playerBrands.isEmpty()) {
			ACTIVE_BRANDS.remove(player.getUuid());
		}

		// 同步烙印状态到客户端（仅在父变时发送）
		syncIfDirty(player);
	}

	/**
	 * 玩家断线/变形时清理，同时移除目标上的标记效果并发送空同步包
	 */
	public static void clearPlayer(ServerPlayerEntity player) {
		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.remove(player.getUuid());
		if (playerBrands != null && player.getWorld() instanceof ServerWorld serverWorld) {
			for (UUID targetUuid : playerBrands.keySet()) {
				LivingEntity target = findEntity(serverWorld, targetUuid);
				if (target != null) removeAllBrandMarkers(target);
			}
		}
		DIRTY_PLAYERS.remove(player.getUuid());
		// 发送空同步包到客户端
		try {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeInt(0);
			ServerPlayNetworking.send(player, PACKET_BRAND_SYNC, buf);
		} catch (Exception ignored) {
			// 玩家可能已断线，忽略发送失败
		}
	}

	/**
	 * 服务器重启/热重载时清理所有状态
	 */
	public static void clearAll() {
		ACTIVE_BRANDS.clear();
		DIRTY_PLAYERS.clear();
	}

	// ==================== 网络同步方法 ====================

	/**
	 * 标记玩家的烙印数据需要同步到客户端
	 */
	private static void markDirty(UUID playerUuid) {
		DIRTY_PLAYERS.add(playerUuid);
	}

	/**
	 * 如果玩家的烙印数据已变化，同步到其客户端
	 * 在 tick 结束时调用，避免每次变化都发包
	 */
	private static void syncIfDirty(ServerPlayerEntity player) {
		if (!DIRTY_PLAYERS.remove(player.getUuid())) return;

		Map<UUID, BrandState> playerBrands = ACTIVE_BRANDS.get(player.getUuid());
		PacketByteBuf buf = PacketByteBufs.create();

		if (playerBrands == null || playerBrands.isEmpty()) {
			buf.writeInt(0);
		} else {
			// 收集有效颜色条目
			List<Map.Entry<UUID, String>> entries = new ArrayList<>();
			for (Map.Entry<UUID, BrandState> entry : playerBrands.entrySet()) {
				String color = getColorFromState(entry.getValue());
				if (color != null) {
					entries.add(Map.entry(entry.getKey(), color));
				}
			}
			buf.writeInt(entries.size());
			for (Map.Entry<UUID, String> entry : entries) {
				buf.writeUuid(entry.getKey());
				buf.writeString(entry.getValue());
			}
		}

		ServerPlayNetworking.send(player, PACKET_BRAND_SYNC, buf);
	}

	/**
	 * 从 BrandState 获取颜色字符串
	 */
	private static String getColorFromState(BrandState state) {
		if (state == null) return null;
		if (state.greenState) return "green";
		return switch (state.stacks) {
			case 1 -> "yellow";
			case 2 -> "orange";
			case 3 -> "red";
			default -> null;
		};
	}

	/**
	 * 同步烙印状态到客户端 - 通过状态效果实现
	 * 拆分为3个独立效果以支持不同层数显示不同图标：
	 * erosion_brand_marker_1: 1层(黄色), erosion_brand_marker_2: 2层(橙色), erosion_brand_marker_3: 3层(红色)
	 * 绿色冷却状态不显示图标。
	 */
	private static void updateTargetMarker(LivingEntity target, BrandState state, long currentTick) {
		if (state == null || (state.stacks <= 0 && !state.greenState)) {
			removeAllBrandMarkers(target);
			return;
		}
		int duration;
		if (state.greenState) {
			// 绿色冷却状态：不显示图标，移除所有标记
			removeAllBrandMarkers(target);
			return;
		} else {
			duration = (int) (state.expiryTick - currentTick);
		}
		if (duration <= 0) {
			removeAllBrandMarkers(target);
			return;
		}
		// 先移除所有旧标记，再施加当前层数对应的标记
		removeAllBrandMarkers(target);
		net.minecraft.entity.effect.StatusEffect markerEffect = switch (state.stacks) {
			case 1 -> SscAddon.EROSION_BRAND_MARKER_1;
			case 2 -> SscAddon.EROSION_BRAND_MARKER_2;
			case 3 -> SscAddon.EROSION_BRAND_MARKER_3;
			default -> null;
		};
		if (markerEffect != null) {
			// ambient=false, showParticles=false, showIcon=true（显示对应层数图标）
			target.addStatusEffect(new StatusEffectInstance(
					markerEffect, duration, 0, false, false, true
			));
		}
	}

	/**
	 * 移除目标上的所有烙印标记效果
	 */
	private static void removeAllBrandMarkers(LivingEntity target) {
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_1);
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_2);
		target.removeStatusEffect(SscAddon.EROSION_BRAND_MARKER_3);
	}

	/**
	 * 在世界中查找目标实体
	 */
	private static LivingEntity findEntity(ServerWorld world, UUID uuid) {
		var entity = world.getEntity(uuid);
		return entity instanceof LivingEntity living ? living : null;
	}

	// ==================== 内部数据类 ====================
	private static class BrandState {
		int stacks = 0;
		boolean greenState = false;
		long lastStackTick = -100; // 上次叠层时间
		long expiryTick = 0; // 烙印过期时间
		long greenExpiryTick = 0; // 绿色状态过期时间（视觉标记，10秒）
		long stackCooldownExpiryTick = 0; // 叠层冷却过期时间（5秒）
	}
}
