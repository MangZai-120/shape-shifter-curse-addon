package net.jackcooper.shapeShifterCurseAddon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpDeathDomain;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpSummonWolves;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormWitherSand;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormErosionBrand;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormCounterBurst;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormDetonate;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpFrostStorm;
import net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpMeleeAbility;
import net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpTeleportAttack;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostBallEntity;
import net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.SkillBlocker;

import java.util.UUID;

public class SscAddonActions {

	private SscAddonActions() {
		// This utility class should not be instantiated
	}

	/**
	 * 已迁移至 Apoli 资源系统，保留为接口兼容
	 */
	public static void clearPlayer(UUID uuid) {
	}

	/**
	 * 已迁移至 Apoli 资源系统，保留为接口兼容
	 */
	public static void clearAll() {
	}

	public static void register() {
		registerEntity(new ActionFactory<>(new Identifier("my_addon", "fallen_allay_scream"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) player.getWorld();

						// Particle and sound
						world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.2f);
						net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(world, net.minecraft.particle.ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.5, 0.5, 0.5, 0.1);

						Box box = player.getBoundingBox().expand(25.0);

						// Glow non-whitelisted
						java.util.List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive());
						for (LivingEntity e : entities) {
							// 始终跳过：自己的驯服动物、自己的恕魔、劫掠阵营
							if (e instanceof net.minecraft.entity.passive.TameableEntity tameable && player.getUuid().equals(tameable.getOwnerUuid())) {
								continue;
							}
							if (e instanceof net.minecraft.entity.mob.VexEntity vex && vex.getCommandTags().contains("owner:" + player.getUuidAsString())) {
								continue;
							}
							if (e instanceof net.minecraft.entity.raid.RaiderEntity) {
								continue;
							}
							// 统一白名单判定：受服务端总开关控制
							if (WhitelistUtils.isProtected(player, e)) continue;
						e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0), player); // 8s；source=施法者供入梦高光拦截
						}

						// Kill projectiles
						java.util.List<Entity> projectiles = world.getOtherEntities(player, box, e -> e instanceof net.minecraft.entity.projectile.ProjectileEntity);
						for (Entity p : projectiles) {
							net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(world, net.minecraft.particle.ParticleTypes.SMOKE, p.getX(), p.getY(), p.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
							p.discard();
						}
					}
				}
		));

		registerEntity(new ActionFactory<>(new Identifier("my_addon", "summon_fallen_allay_vex"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) player.getWorld();

						// Check if player already has vex
						boolean hasVex = false;
						for (Entity e : world.getEntitiesByClass(net.minecraft.entity.mob.VexEntity.class, player.getBoundingBox().expand(128.0), v -> true)) {
							if (e.getCommandTags().contains("owner:" + player.getUuidAsString()) && e.getCommandTags().contains("ssc_fallen_allay_vex")) {
								hasVex = true;
								break;
							}
						}

						if (!hasVex) {
							for (int i = 0; i < 2; i++) {
								net.minecraft.entity.mob.VexEntity vex = net.minecraft.entity.EntityType.VEX.create(world);
								if (vex != null) {
									vex.refreshPositionAndAngles(player.getX(), player.getEyeY(), player.getZ(), player.getYaw(), player.getPitch());
									// Spawn two vexes 180° apart with small velocity so they don't overlap
									double angle = i * Math.PI;
									vex.setVelocity(Math.cos(angle) * 0.3, 0.3, Math.sin(angle) * 0.3);
									vex.addCommandTag("ssc_fallen_allay_vex");
									vex.addCommandTag("owner:" + player.getUuidAsString());
									vex.setLifeTicks(700); // 35s * 20 ticks
									world.spawnEntity(vex);
								}
							}
						}
					}
				}
		));
		registerEntity(PhantomBellTeleportAction.getFactory());
                // 物品冷却动作（幻铃/救命猫尾等饰品 power 用；此前漏注册导致相关 power 整体被跳过）
                registerEntity(net.jackcooper.shapeShifterCurseAddon.action.ItemCooldownAction.getFactory());

		// 食梦魔主要技能「恐惧」：对所有已入梦目标施加恐惧（粉雾/减速/心跳/伤害翻倍/1s隐匿）
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "nightmare_fear"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						net.jackcooper.shapeShifterCurseAddon.ability.NightmareFearManager.execute(player);
					}
				}));

		// 食梦魔次要技能「惊吓」：假苦力怕幻影 + 复制品反击（仅目标可见）
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "nightmare_spook"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.execute(player);
					}
				}));

		registerEntity(new ActionFactory<>(new Identifier("my_addon", "fire_breath"),
				new SerializableData()
						.add("distance", SerializableDataTypes.FLOAT)
						.add("damage", SerializableDataTypes.FLOAT)
						.add("duration", SerializableDataTypes.INT, 100),
				(data, entity) -> {
					if (!(entity instanceof LivingEntity living)) return;

					float distance = data.getFloat("distance");
					float damageAmount = data.getFloat("damage");
					int duration = data.getInt("duration");

					Vec3d eyePos = living.getEyePos();
					Vec3d lookVec = living.getRotationVec(1.0F);
					Box box = living.getBoundingBox().expand(distance).stretch(lookVec.multiply(distance));
					living.getWorld().getEntitiesByClass(LivingEntity.class, box, target -> target != living).forEach(target -> {
						if (living instanceof ServerPlayerEntity sPlayer && WhitelistUtils.isProtected(sPlayer, target))
							return;
						Vec3d targetVec = target.getPos().add(0, target.getHeight() / 2, 0).subtract(eyePos).normalize();
						double dot = lookVec.dotProduct(targetVec);
						double distSq = living.squaredDistanceTo(target);

						if (dot > 0.8 && distSq < distance * distance) {
							Vec3d oldVelocity = target.getVelocity();
							RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
							if (target.damage(target.getDamageSources().create(magicKey, living, living), damageAmount)) {
								target.setVelocity(oldVelocity);
							}

							target.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, duration, 0), living); // Duration from data; source=施法者供入梦拦截归因

							if (living instanceof PlayerEntity player && target instanceof SscIgnitedEntityAccessor accessor) {
								accessor.sscAddon$setIgniterUuid(player.getUuid());
							}
						}
					});

					// 火焰吐息路径上的水有15%概率变为冰霜行者冰
					if (!living.getWorld().isClient() && living.getWorld() instanceof ServerWorld serverWorld) {
						freezeWaterInCone(serverWorld, eyePos, lookVec, distance, 0.15f);
					}
				}));

		registerBiEntity(new ActionFactory<>(new Identifier("ssc_addon", "salticidae_venom_fang"),
				new SerializableData(),
				(data, pair) -> {
				// 毒牙（跳蛛被动）：左键命中 → 中毒 I 5s（100t）；毒液腺体：等级+1（变 II）/ 3s（60t）
				Entity actor = pair.getLeft();
				Entity target = pair.getRight();
				if (!(actor instanceof net.minecraft.server.network.ServerPlayerEntity player)
						|| !(target instanceof net.minecraft.entity.LivingEntity living)) return;
				boolean gland = net.jackcooper.shapeShifterCurseAddon.item.VenomGlandItem.isWearingBy(player);
				int amp = 0 + (gland ? 1 : 0);
				int dur = gland ? 60 : 100;
					living.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
							net.minecraft.entity.effect.StatusEffects.POISON, dur, amp, false, true, true), player);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							net.minecraft.sound.SoundEvents.ENTITY_SPIDER_AMBIENT,
							net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 1.6f);
				}));

		registerBiEntity(new ActionFactory<>(new Identifier("my_addon", "damage_target_from_actor"),
				new SerializableData()
						.add("amount", SerializableDataTypes.FLOAT)
						.add("damage_type", SerializableDataTypes.IDENTIFIER),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					if (actor == null || target == null) return;

					float amount = data.getFloat("amount");
					Identifier damageTypeId = data.getId("damage_type");

					if (target instanceof LivingEntity) {
						RegistryKey<DamageType> damageTypeKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, damageTypeId);
						Vec3d oldVelocity = target.getVelocity();
						if (target.damage(target.getDamageSources().create(damageTypeKey, null, actor), amount)) {
							target.setVelocity(oldVelocity);
						}
					}
				}));

		// SP雪狐 - 雪刺冲刺技能
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_dash"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "melee_primary")) {
							return;
						}
						SnowFoxSpMeleeAbility.execute(player);
					}
				}));

		// SP雪狐 - 瞬移攻击技能
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_teleport_attack"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "melee_secondary")) {
							return;
						}
						SnowFoxSpTeleportAttack.execute(player);
					}
				}));

		// SP雪狐 - 法术冰球技能
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_frost_ball"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "ranged_primary")) {
							return;
						}
						// 检查CD资源是否还在冷却中
						int currentCd = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD);
						if (currentCd > 0) {
							return;
						}

						// 检查并消耗霜寒值
						int currentMana = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
						int manaCost = 15;
						if (currentMana < manaCost) {
							player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
							return;
						}
						PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, -manaCost);
						// 设置回复冷却（5秒）
						PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN, 100);
						// 设置CD显示资源（5秒 = 100tick）
						PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD, 100);

						// 创建并发射冰球
						FrostBallEntity frostBall = new FrostBallEntity(player.getWorld(), player);
						Vec3d lookVec = player.getRotationVec(1.0F);
						frostBall.setDirection(lookVec);
						player.getWorld().spawnEntity(frostBall);

						// 播放发射音效
						player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
								SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
					}
				}));

		// SP雪狐 - 冰风暴技能（点按开始蓄力）
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "snow_fox_sp_frost_storm"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "ranged_secondary")) {
							return;
						}
						SnowFoxSpFrostStorm.startCharging(player);
					}
				}));

		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "trigger_play_dead"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof LivingEntity living) {
						// 1. Effects
						// Duration 6s = 120 ticks
						int duration = 120;

						// 项链黄心改由 PlayingDeadEffect 每10tick累积，不再用 Absorption 效果

						// visible=false to hide icon（回血改由 PlayingDeadEffect 每10tick结算）
						living.addStatusEffect(new StatusEffectInstance(SscAddon.PLAYING_DEAD, duration, 0, false, false, false));
						living.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false));
						living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 10, false, false));

						// 2. Clear Aggro
						double radius = 64.0;
						Box box = living.getBoundingBox().expand(radius);
						living.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, box, mob -> mob.getTarget() == living).forEach(mob -> {
							mob.setTarget(null);
							mob.setAttacker(null);
						});

						// 3. Force Pose
						living.setPose(EntityPose.SLEEPING);

						// 4. 设置CD显示资源
						if (living instanceof ServerPlayerEntity sp) {
							PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.SP_SECONDARY_CD, 620);
						}

					}
				}));

		registerEntity(new ActionFactory<>(new Identifier("my_addon", "swim_jump_out"),
				new SerializableData().add("multiplier", SerializableDataTypes.FLOAT, 2.0F),
				(data, entity) -> {
					// Must be in swimming pose (sprinting in water) to trigger
					if (entity.isSwimming()) {
						Vec3d velocity = entity.getVelocity();
						double vy = velocity.y;

						// Only boost if moving upwards
						// AND looking up (Pitch < -20) for the special "Jump Out" mechanics
						if (vy > 0) {
							double newVy = vy;
							double newVx = velocity.x;
							double newVz = velocity.z;

							// Special Jump Boost: Only when looking up (Pitch < -20)
							// Triggers explosive jump out of water
							if (entity.getPitch() < -20.0f) {
								newVy = vy * data.getFloat("multiplier");

								// Height limit constraints (Considering Air Resistance 0.98 and Gravity 0.08)
								// Min: 7 Blocks Height -> requires ~1.1 velocity
								// Max: 12 Blocks Height -> requires ~1.5 velocity
								double minVy = 1.1;
								double maxVy = 1.7;

								if (newVy < minVy) newVy = minVy;
								if (newVy > maxVy) newVy = maxVy;

								// Maintain Horizontal Acceleration (Fix "stutter/stop" when looking up)
							} else {
								// Normal Swimming Leap (Flat/Looking Down):
								// Vertical speed (Rising/Sinking) 1.5x boost
								newVy = vy * 1.5;
								// Horizontal boost (1.5x) to create composite vector acceleration
							}
							newVx = velocity.x * 1.5;
							newVz = velocity.z * 1.5;

							// Always apply velocity to preserve momentum against water exit drag
							// This ensures smooth transition for both cases
							entity.setVelocity(newVx, newVy, newVz);
							entity.velocityModified = true;
						}
					}
				}));

		// ==== SP阿努比斯之狼 - 死亡领域 ====
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "anubis_wolf_sp_death_domain"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity sp) {
						if (SkillBlocker.isSkillBlocked(sp, "anubis_wolf", "death_domain")) {
							return;
						}
						AnubisWolfSpDeathDomain.execute(sp);
					}
				}));

		// ==== SP阿努比斯之狼 - 冥狼裁庭 ====
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "anubis_wolf_sp_summon_wolves"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity sp) {
						if (SkillBlocker.isSkillBlocked(sp, "anubis_wolf", "summon_wolves")) {
							return;
						}
						AnubisWolfSpSummonWolves.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 侵蚀烙印命中处理 ====
		registerBiEntity(new ActionFactory<>(new Identifier("ssc_addon", "golden_sandstorm_erosion_brand_hit"),
				new SerializableData(),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					if (actor instanceof ServerPlayerEntity sp && target instanceof LivingEntity living) {
						GoldenSandstormErosionBrand.onPlayerAttack(sp, living);
					}
				}));

		// ==== 金沙岚SP - 凋零金沙 ====
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "golden_sandstorm_wither_sand"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity sp) {
						GoldenSandstormWitherSand.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 引爆烙印 ====
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "golden_sandstorm_detonate"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity sp) {
						GoldenSandstormDetonate.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 反噬冲击（被动） ====
		registerEntity(new ActionFactory<>(new Identifier("ssc_addon", "golden_sandstorm_counter_burst"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity sp) {
						GoldenSandstormCounterBurst.execute(sp);
					}
				}));

		// ==== 旋转圆环粒子（附属专用，仅 red 火环外圈使用，不影响主模组 spawn_particles_in_circle） ====
		registerEntity(SpawnRotatingCircleAction.getFactory());

		// ==== red 狐火火球：发射火球投射物 + 近身 60°×4格 锥形霰击（5 魔法伤害，附属专用，只作用 red） ====
		registerEntity(new ActionFactory<>(new Identifier("my_addon", "fox_fireball"),
				new SerializableData(),
				(data, entity) -> {
					if (!(entity instanceof ServerPlayerEntity player)) return;
					if (!(player.getWorld() instanceof ServerWorld world)) return;
					Vec3d look = player.getRotationVec(1.0F);
					// 发射火球投射物
					net.jackcooper.shapeShifterCurseAddon.entity.FoxFireballEntity ball =
							new net.jackcooper.shapeShifterCurseAddon.entity.FoxFireballEntity(world, player);
					ball.setDirection(look);
					world.spawnEntity(ball);
					// 近身 60°、4 格锥形霰击：5 点魔法伤害，跳过白名单
					Vec3d eye = player.getEyePos();
					RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
					Box box = player.getBoundingBox().expand(4.0);
					world.getEntitiesByClass(LivingEntity.class, box,
							e -> e != player && e.isAlive() && !e.isSpectator()).forEach(t -> {
						if (WhitelistUtils.isProtected(player, t)) return;
						Vec3d toT = t.getPos().add(0, t.getHeight() / 2.0, 0).subtract(eye).normalize();
						double dot = look.dotProduct(toT);
						if (dot > 0.5 && player.squaredDistanceTo(t) < 16.0) {
							t.damage(t.getDamageSources().create(magicKey, player, player), 5.0f);
						}
					});
				}));
	}

	private static void registerBiEntity(ActionFactory<Pair<Entity, Entity>> actionFactory) {
		if (!ApoliRegistries.BIENTITY_ACTION.containsId(actionFactory.getSerializerId())) {
			Registry.register(ApoliRegistries.BIENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
		}
	}

	private static void registerEntity(ActionFactory<Entity> actionFactory) {
		if (!ApoliRegistries.ENTITY_ACTION.containsId(actionFactory.getSerializerId())) {
			Registry.register(ApoliRegistries.ENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
		}
	}

	/**
	 * 将锥形范围内的水源方块以概率转为冰霜行者冰（Frosted Ice）
	 *
	 * @param world     服务器世界
	 * @param origin    起始位置（玩家眼睛位置）
	 * @param direction 方向向量
	 * @param distance  最大距离
	 * @param chance    每个水方块被冻结的概率
	 */
	private static void freezeWaterInCone(ServerWorld world, Vec3d origin, Vec3d direction, float distance, float chance) {
		// 沿视线方向每格取样，锥形扩散与吐息粒子范围一致
		for (float d = 1.0f; d <= distance; d += 1.0f) {
			Vec3d center = origin.add(direction.multiply(d));
			// 锥形扩散半径：与吐息粒子效果一致（最远处约3格宽）
			int radius = Math.max(1, (int) (d * 0.375f));

			int cx = MathHelper.floor(center.x);
			int cy = MathHelper.floor(center.y);
			int cz = MathHelper.floor(center.z);

			for (int x = -radius; x <= radius; x++) {
				for (int y = -1; y <= 1; y++) {
					for (int z = -radius; z <= radius; z++) {
						if (x * x + z * z > radius * radius) continue;

						BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
						if (world.getBlockState(pos).isOf(Blocks.WATER)
								&& world.getFluidState(pos).isStill()
								&& world.getBlockState(pos.up()).isAir()) {

							if (world.getRandom().nextFloat() < chance) {
								world.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState());
								world.scheduleBlockTick(
										pos, Blocks.FROSTED_ICE,
										MathHelper.nextInt(world.getRandom(), 60, 120));
							}
						}
					}
				}
			}
		}
	}
}
