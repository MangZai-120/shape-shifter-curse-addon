package net.jackcooper.shapeShifterCurseAddon.entity;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CaveVines;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.AmphibiousPathNodeMaker;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 美西螈幻形者 - 仿女巫使魔架构的中立水陆两栖生物
 * <p>
 * 复刻 SSC 原版美西螈（axolotl_3）玩家形态的技能与被动：
 * <ul>
 *   <li>湿润度（自管 moisture 0~300，语义对齐原版 air）：水中/雨中回润（+4/t），
 *       陆地流失（-3/s，对齐荧光幼链 moisture_drain），归零后每秒 2 点脱水真实伤害</li>
 *   <li>湿润度→最大生命分档联动（原版 oxygen_health 0~9：满 +6 最低 -10，
 *       基础 20HP，满润 26HP）</li>
 *   <li>水流爆破（原版冲刺+潜行「水花四溅」power=2 爆炸）：贴近目标释放，
 *       完整复刻 ExplosionDamageEntityAction（爆炸伤害+击退+雨/深蓝尘埃粒子），冷却 8 秒</li>
 *   <li>近战 4 点伤害（受护甲与保护附魔格挡，2026-10-20 起）+ 冲刺猛扑击飞（原版 sprinting_attack：4 格冲量）</li>
 *   <li>水中高机动（water_flexibility 0.93 阻力系数）+ 陆地减速 30%（ground_speed_down）</li>
 *   <li>摔落保护 6 格（falling_protection）</li>
 *   <li>疾跑滑溜（slipperiness 0.35）+ 疾跑跳增强 10%（jump_high）</li>
 *   <li>水生阵营（aquatic）+ 不被水流推动（like_water）</li>
 *   <li>中立：被攻击才反击；同类不互殴；湿润度低时主动寻找水源</li>
 *   <li>自带 5 点护甲；美西螈音效；水中游动时鹦鹉螺/水花粒子</li>
 * </ul>
 */
public class AxolotlShifterEntity extends PathAwareEntity implements GeoEntity {

	// ===== 水流爆破参数（复刻原版 form_axolotl_3_sprinting_sneaking_water_explode：power=2） =====
	private static final int BURST_COOLDOWN_MAX = 160;      // 8 秒冷却（原版由冲刺+潜行触发，怪物化转为冷却）
	private static final int BURST_POWER = 2;               // 原版 power=2
	private static final float BURST_RADIUS = BURST_POWER * 2.0f; // 影响半径 = 4 格
	private static final int BURST_MOISTURE_COST = 15;      // 原版 gain_air -15
	// 原版水花爆炸免疫 tag（盔甲架/展示框等）
	private static final TagKey<EntityType<?>> WATER_EXPLODE_IMMUNE =
			TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier("shape-shifter-curse", "water_explode_immune"));

	// ===== 湿润度参数 =====
	public static final int MAX_MOISTURE = 300;   // 对齐玩家 air 上限
	private static final int MOISTURE_DRAIN_PER_SEC = 3;    // 陆地流失（荧光幼链 moisture_drain -3/s）
	private static final int MOISTURE_REGEN_PER_TICK = 4;   // 水中/雨中回润速度（对齐玩家出水回气 +4/t）
	private static final int DEHYDRATION_DAMAGE = 2;        // 归零后每秒 2 点真实伤害（dehydration power）
	private static final int FIND_WATER_THRESHOLD = 180;    // 湿润度 < 60% 主动找水
	private static final int SEEK_WATER_RANGE = 24;         // 寻水扫描水平半径（扩大搜索范围）
	private static final int NEAR_WATER_RADIUS = 6;         // 水源方块 6 格内：湿润度流失减 80%（岸边被动）

	// ===== 战斗缺水撤退（湿润度过低时脱战入水补润，滞回窗口防抖） =====
	private static final int RETREAT_MOISTURE_THRESHOLD = 90;   // 战斗中湿润度 < 90（30%）触发撤退找水
	private static final int RETREAT_RECOVER_MOISTURE = 240;    // 补润到 ≥240（80%）才结束撤退回战

	// ===== 环境伤害惊慌（火/岩浆/浆果丛/仙人掌等触发，钻水躲避） =====
	private static final int PANIC_DURATION = 200;             // 惊慌持续 10 秒（每次受环境伤害刷新）

	// ===== 发光浆果进食（偶尔上岸摘繁茂洞穴发光浆果吃，回润 + 回血） =====
	private static final int EAT_SCAN_RANGE = 10;             // 扫描发光浆果水平半径
	private static final int EAT_MOISTURE_GAIN = 60;          // 吃一颗回润 60
	private static final float EAT_HEAL = 4.0f;              // 吃一颗回血 4（2 心）
	private static final int EAT_COOLDOWN_MIN = 600;         // 两次进食最短间隔 30 秒
	private static final int EAT_COOLDOWN_MAX = 1200;        // 最长 60 秒

	// ===== 战斗爆发提速（平时移速慢，进战斗临时提速，复刻「战斗触发 power 增强」观感） =====
	private static final UUID COMBAT_SPEED_UUID = UUID.fromString("b7f3c2a1-4d5e-6f70-8192-a3b4c5d6e7f8");
	private static final double COMBAT_SPEED_BOOST = 0.3;    // 战斗时移速 ×1.3（0.22→0.286，避免过快导致寻路过冲乱晃）

	// ===== 冲划猛扑（原版 form_axolotl_3_sprinting_attack 的音效/粒子部分，击退增强已按需求移除） =====
	private static final int POUNCE_MOISTURE_COST = 10;     // 原版 gain_air -10

	// ===== 湿润度→生命联动（原版 oxygen_health 0~9 分档合并） =====
	private static final UUID MOISTURE_HEALTH_UUID = UUID.fromString("8e6e6b3a-1c2d-4e5f-9a8b-7c6d5e4f3a2b");

	// ===== 自定义伤害类型（近战已改为受护甲格挡 / 脱水真实伤害保留穿甲） =====
	private static final RegistryKey<DamageType> ATTACK_DAMAGE_KEY =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("ssc_addon", "axolotl_shifter_attack"));
	private static final RegistryKey<DamageType> THIRST_DAMAGE_KEY =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("ssc_addon", "axolotl_shifter_thirst"));

	/** 深蓝水尘粒子（原版 dust 参数 0.11 0.18 0.69 2） */
	private static final DustParticleEffect DEEP_BLUE_DUST =
			new DustParticleEffect(new Vector3f(0.11f, 0.18f, 0.69f), 2.0f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	/** 湿润度（0~300，NBT 持久化） */
	private int moisture = MAX_MOISTURE;
	/** 当前已应用的湿润度生命加成档位（哨兵初始值：确保首次 tick 强制应用满湿 +6，修复生成时只有基础 20 血的 bug） */
	private int moistureBonus = Integer.MIN_VALUE;
	/** 水流爆破冷却 */
	private int burstCooldown = 0;
	/** 环境伤害惊慌计时（>0 时最高优先级钻水，不持久化） */
	private int panicTicks = 0;
	/** 发光浆果进食冷却（避免频繁上岸摘浆果，不持久化） */
	private int eatCooldown = 0;
	/** 当前是否已施加战斗爆发提速 modifier（避免重复增删） */
	private boolean combatSpeedApplied = false;
	/** 补润期间不追击或释放爆破；由当前持有移动控制权的寻水目标管理。 */
	private boolean recoveringMoisture;

	public AxolotlShifterEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		this.moveControl = new ShifterMoveControl(this);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0f);
	}

	/** 水陆两栖寻路（对齐原版 AxolotlEntity.createNavigation） */
	@Override
	protected EntityNavigation createNavigation(World world) {
		return new ShifterNavigation(this, world);
	}

	public static DefaultAttributeContainer.Builder createAxolotlShifterAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)      // 基础 20HP，满润 +6 = 26HP（对齐原版满氧美西螈）
				.add(EntityAttributes.GENERIC_ARMOR, 5.0)            // 自带 5 点护甲（用户指定）
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)  // 平时移速慢（陆地笨拙），战斗时由 setCombatSpeedBoost 临时提速
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)    // 近战 4 点（实际造成走自定义伤害源，受护甲格挡）
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.0);
	}

	/** 繁茂洞穴水体生成条件：出生点是水且下方为固体（仿原版美西螈 canSpawn） */
	public static boolean canSpawnInWater(EntityType<? extends AxolotlShifterEntity> type, ServerWorldAccess world,
	                                      SpawnReason reason, BlockPos pos, net.minecraft.util.math.random.Random random) {
		return world.getFluidState(pos).isIn(FluidTags.WATER)
				&& world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
	}

	public int getMoisture() {
		return this.moisture;
	}

	public boolean isPanicking() {
		return this.panicTicks > 0;
	}

	/** 由近及远扫描附近水源方块（供惊慌钻水 / 缺水撤退复用） */
	@Nullable
	private BlockPos findNearestWater(int rangeH, int rangeV) {
		BlockPos center = this.getBlockPos();
		for (BlockPos pos : BlockPos.iterateOutwards(center, rangeH, rangeV, rangeH)) {
			if (this.getWorld().getFluidState(pos).isIn(FluidTags.WATER)) {
				return pos.toImmutable();
			}
		}
		return null;
	}

	/** 一次寻路在多处水源中选择可到达的落脚点，避免盯住墙后或水浸方块里的水。 */
	@Nullable
	private Path findWaterPath(int rangeH, int rangeV, Set<BlockPos> rejected) {
		Set<BlockPos> candidates = new HashSet<>();
		for (BlockPos pos : BlockPos.iterateOutwards(this.getBlockPos(), rangeH, rangeV, rangeH)) {
			if (!this.getWorld().getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || rejected.contains(pos)
					|| !this.getWorld().getFluidState(pos).isIn(FluidTags.WATER)) continue;
			Vec3d feet = Vec3d.ofBottomCenter(pos);
			if (this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(feet.subtract(this.getPos())))) {
				candidates.add(pos.toImmutable());
			}
		}
		Path path = this.getNavigation().findPathTo(candidates, 0);
		return path != null && path.reachesTarget() ? path : null;
	}

	/**
	 * 朝指定 yaw 方向前方选点漫游一步（找不到水时的定向探索 / 逃离，确保会移动而非原地呆着）。
	 * @return 是否成功发起寻路
	 */
	private boolean wanderTowardYaw(float yaw, double speed) {
		double rad = Math.toRadians(yaw);
		// FuzzyTargeting 只选落脚点，仍须确认完整路径，不能把部分路径当成可到达。
		Vec3d prefer = this.getPos().add(-Math.sin(rad) * 12.0, 0.0, Math.cos(rad) * 12.0);
		Vec3d target = FuzzyTargeting.findTo(this, 12, 7, prefer);
		if (target == null) target = FuzzyTargeting.find(this, 10, 5);  // 兜底：方向找不到就取任意可达点，确保会移动
		if (target == null) return false;
		Path path = this.getNavigation().findPathTo(BlockPos.ofFloored(target), 0);
		return path != null && path.reachesTarget() && this.getNavigation().startMovingAlong(path, speed);
	}

	/** 战斗爆发提速：进入战斗临时提速，脱离战斗恢复（固定 UUID，先移后加防叠加） */
	private void setCombatSpeedBoost(boolean active) {
		if (active == this.combatSpeedApplied) return;
		EntityAttributeInstance attr = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (attr == null) return;
		attr.removeModifier(COMBAT_SPEED_UUID);
		if (active) {
			attr.addTemporaryModifier(new EntityAttributeModifier(COMBAT_SPEED_UUID,
					"Axolotl shifter combat speed", COMBAT_SPEED_BOOST, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		this.combatSpeedApplied = active;
	}

	/** 吃掉一处成熟发光浆果（清除浆果状态 + 采摘音效 + 回润回血 + 粒子） */
	private void eatGlowBerryAt(BlockPos pos) {
		BlockState state = this.getWorld().getBlockState(pos);
		if (!CaveVines.hasBerries(state)) return;
		this.getWorld().setBlockState(pos, state.with(Properties.BERRIES, false), Block.NOTIFY_LISTENERS);
		this.getWorld().playSound(null, pos, SoundEvents.BLOCK_CAVE_VINES_PICK_BERRIES,
				SoundCategory.NEUTRAL, 1.0f, 0.8f + this.getRandom().nextFloat() * 0.4f);
		this.moisture = Math.min(MAX_MOISTURE, this.moisture + EAT_MOISTURE_GAIN);
		this.heal(EAT_HEAL);
		if (this.getWorld() instanceof ServerWorld sw) {
			ParticleUtils.spawnParticles(sw, ParticleTypes.HAPPY_VILLAGER,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.0);
		}
	}

	@Override
	protected void initGoals() {
		// 优先级越小越高。战斗行为向玩家看齐：惊慌钻水 > 缺水脱战撤退 > 近战猛攻 > 缺水找水 > 摘浆果 > 游荡
		this.goalSelector.add(0, new PanicToWaterGoal(this));            // 受环境伤害惊慌，最高优先级钻水
		this.goalSelector.add(1, new CombatRetreatToWaterGoal(this));    // 战斗中湿润度过低，脱战撤入水源补润
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));  // 近战猛攻（贴脸；水流爆破由 mobTick 触发）
		this.goalSelector.add(3, new SeekWaterGoal(this));              // 非战斗：缺水去水 / 附近没水则定向漫游找水
		this.goalSelector.add(4, new EatGlowBerriesGoal(this));          // 偶尔上岸摘繁茂洞穴发光浆果吃
		this.goalSelector.add(5, new SwimAroundGoal(this, 1.0, 10) {
			@Override public boolean canStart() {
				return isTouchingWater() && getTarget() == null && !recoveringMoisture && super.canStart();
			}
			@Override public boolean shouldContinue() {
				return isTouchingWater() && getTarget() == null && !recoveringMoisture && super.shouldContinue();
			}
		});
		this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8) {
			@Override public boolean canStart() {
				return !isTouchingWater() && getTarget() == null && !recoveringMoisture && super.canStart();
			}
			@Override public boolean shouldContinue() {
				return !isTouchingWater() && getTarget() == null && !recoveringMoisture && super.shouldContinue();
			}
		});
		this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		// 主动索敌：对齐原版美西螈——主动攻击水中的敌对怪物（溟尸/守卫者/远古守卫者等）；
		// 不主动攻击玩家（保持对玩家中立，仅被玩家攻击时反击）
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false, this::shouldHuntTarget));
		// 中立反击：被任何生物攻击都反击（不限于同类）；同类之间不互相报复
		this.targetSelector.add(2, new RevengeGoal(this, AxolotlShifterEntity.class).setGroupRevenge(AxolotlShifterEntity.class));
	}

	/**
	 * 主动索敌筛选（对齐原版美西螈）：只追击在水中的敌对怪物。
	 * 排除同类，避免互攻。
	 */
	private boolean shouldHuntTarget(LivingEntity target) {
		if (target instanceof AxolotlShifterEntity) return false;
		if (!target.isTouchingWater()) return false;
		return target.isAlive();
	}

	@Override
	public void mobTick() {
		super.mobTick();

		if (this.burstCooldown > 0) this.burstCooldown--;
		if (this.panicTicks > 0) this.panicTicks--;
		if (this.eatCooldown > 0) this.eatCooldown--;

		// 疾跑管理 + 战斗爆发提速：怪物 AI 不会自行疾跑，主动管理（追击目标且距离较远时疾跑逼近，
		// 带动滑溜/跳高等原版疾跑系被动；带滞回窗口防贴脸时反复开关疾跑导致寻路过冲绕圈）。
		// 有攻击目标即施加临时提速（战斗爆发），无目标则收敛回平时的慢速。
		LivingEntity target0 = this.getTarget();
		if (this.isPanicking()) {
			// 惊慌：陆地不减速全力逃，但不叠战斗提速，避免速度过快
			this.setSprinting(true);
			setCombatSpeedBoost(false);
		} else if (this.recoveringMoisture) {
			this.setSprinting(target0 != null && !this.isTouchingWater());
			setCombatSpeedBoost(false);
		} else if (target0 == null) {
			this.setSprinting(false);
			setCombatSpeedBoost(false);
		} else {
			setCombatSpeedBoost(true);
			double distSq = this.squaredDistanceTo(target0);
			if (distSq >= 36.0) {
				this.setSprinting(true);    // ≥6 格：开疾跑逼近
			} else if (distSq <= 9.0) {
				this.setSprinting(false);   // ≤3 格：关疾跑精确近战；中间保持原状态
			}
		}

		if (this.getWorld().isClient) return;
		ServerWorld sw = (ServerWorld) this.getWorld();

		// 着火惊慌保险：只要着火且不在水里就保持惊慌（不依赖 damage() 火焰伤害 tick 时机），
		// 交 PanicToWaterGoal 带它逃火 / 钻水
		if (this.isOnFire() && !this.isTouchingWater()) {
			this.panicTicks = Math.max(this.panicTicks, 60);
		}
		// 已入水 = 惊慌目的达成（水会立即扑灭火）：清除剩余惊慌。
		// 否则残留 panicTicks 会在它出水追击玩家时再次触发 PanicToWaterGoal 弹回水里，来回横跳不攻击
		// （用户需求 2026-08-18：灭完火后应继续靠近并攻击玩家）。
		if (this.panicTicks > 0 && this.isTouchingWater()) {
			this.panicTicks = 0;
		}

		// ===== 湿润度管理 =====
		boolean inWater = this.isTouchingWater();
		boolean rained = this.getWorld().isRaining() && this.getWorld().hasRain(this.getBlockPos());
		if (inWater || rained) {
			// 水中/雨中快速回润
			if (this.moisture < MAX_MOISTURE) {
				this.moisture = Math.min(MAX_MOISTURE, this.moisture + MOISTURE_REGEN_PER_TICK);
			}
		} else if (this.age % 20 == 0) {
			// 陆地流失 + 脱水伤害（对齐荧光幼链 moisture_drain / dehydration）。
			// 被动：水源方块 6 格内流失速率减 80%（80% 概率跳过本秒流失），使湿润度高时能在岸边活动。
			boolean nearWater = findNearestWater(NEAR_WATER_RADIUS, 4) != null;
			if (!nearWater || this.getRandom().nextFloat() >= 0.8f) {
				this.moisture = Math.max(0, this.moisture - MOISTURE_DRAIN_PER_SEC);
				if (this.moisture <= 0) {
					this.damage(this.getDamageSources().create(THIRST_DAMAGE_KEY), DEHYDRATION_DAMAGE);
				}
			}
		}
		// 湿润度→最大生命联动（每秒刷新一次档位）
		if (this.age % 20 == 0) {
			updateMoistureHealth();
		}

		// ===== 水中游动粒子（原版 form_axolotl_3_particle：鹦鹉螺+水花，interval 5） =====
		if (this.age % 5 == 0 && inWater && this.getVelocity().horizontalLengthSquared() > 0.001) {
			ParticleUtils.spawnParticles(sw, ParticleTypes.NAUTILUS,
					this.getX(), this.getY() + 0.5, this.getZ(), 2, 0.5, 0.3, 0.5, 0.0);
			ParticleUtils.spawnParticles(sw, ParticleTypes.SPLASH,
					this.getX(), this.getY() + 0.3, this.getZ(), 2, 0.5, 0.1, 0.5, 0.0);
		}

		// ===== 水流爆破：有攻击目标、贴近（≤4格）且非惊慌时释放（开场 AOE，之后进入近战） =====
		if (this.burstCooldown <= 0 && this.panicTicks <= 0 && !this.recoveringMoisture && this.getTarget() != null
				&& this.canSee(this.getTarget())
				&& this.squaredDistanceTo(this.getTarget()) <= BURST_RADIUS * BURST_RADIUS) {
			useWaterBurst();
			this.burstCooldown = BURST_COOLDOWN_MAX;
		}
	}

	/**
	 * 湿润度→最大生命分档（复刻原版 oxygen_health 0~9 的合并档位）：
	 * ≥270:+6 / ≥240:+4 / ≥210:+2 / ≥120:0 / ≥90:-4 / ≥60:-6 / ≥30:-8 / <30:-10
	 * 首次应用时按新上限补满（对齐满氧生成满血）；湿润回升提升上限时同步回复差额。
	 */
	private void updateMoistureHealth() {
		int m = this.moisture;
		int bonus = m >= 270 ? 6 : m >= 240 ? 4 : m >= 210 ? 2 : m >= 120 ? 0
				: m >= 90 ? -4 : m >= 60 ? -6 : m >= 30 ? -8 : -10;
		if (bonus == this.moistureBonus) return;
		int oldBonus = this.moistureBonus;
		this.moistureBonus = bonus;

		EntityAttributeInstance attr = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (attr == null) return;
		attr.removeModifier(MOISTURE_HEALTH_UUID);
		attr.addTemporaryModifier(new EntityAttributeModifier(MOISTURE_HEALTH_UUID,
				"Axolotl shifter moisture health", bonus, EntityAttributeModifier.Operation.ADDITION));
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		} else if (oldBonus == Integer.MIN_VALUE) {
			// 首次应用（生成/读档后）：按新上限补满，对齐满氧美西螈满血观感
			this.setHealth(this.getMaxHealth());
		} else if (bonus > oldBonus) {
			// 湿润回升提升上限：同步回复差额（对齐玩家满氧回血体验）
			this.heal(bonus - oldBonus);
		}
	}

	/**
	 * 释放水流爆破（完整复刻原版 ExplosionDamageEntityAction power=2 +
	 * form_axolotl_3_sprinting_sneaking_water_explode 的音效/粒子/自弹开）
	 */
	private void useWaterBurst() {
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;

		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();

		// 音效（原版 power：splash + wither.break_block）
		serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.NEUTRAL, 0.5f, 0.8f);
		serverWorld.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.NEUTRAL, 0.5f, 0.8f);

		// 粒子：雨滴 + 深蓝尘埃（原版 spread x2 y0.5/1 z2，count 32）
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.RAIN, x, y + 0.5, z, 32, 2.0, 0.5, 2.0, 0.0);
		ParticleUtils.spawnParticles(serverWorld, DEEP_BLUE_DUST,
				x, y + 0.5, z, 32, 2.0, 1.0, 2.0, 0.0);

		// 爆炸游戏事件（原版会触发）
		serverWorld.emitGameEvent(this, GameEvent.EXPLODE, this.getPos());

		// 自身后上弹开（原版 add_velocity y0.6 z-0.3 local）
		Vec3d look = this.getRotationVector();
		double hl = Math.sqrt(look.x * look.x + look.z * look.z);
		if (hl > 0.001) {
			this.addVelocity(-look.x / hl * 0.3, 0.6, -look.z / hl * 0.3);
		} else {
			this.addVelocity(0, 0.6, 0);
		}
		this.velocityModified = true;

		// 湿润度消耗（原版 gain_air -15）
		this.moisture = Math.max(0, this.moisture - BURST_MOISTURE_COST);

		// ===== 复刻原版 ExplosionDamageEntityAction（explosion_damage_entity 默认 true） =====
		Vec3d explosionPos = this.getPos();
		float q = BURST_RADIUS; // power * 2.0 = 4.0

		int k = MathHelper.floor(explosionPos.getX() - q - 1.0);
		int l = MathHelper.floor(explosionPos.getX() + q + 1.0);
		int r = MathHelper.floor(explosionPos.getY() - q - 1.0);
		int s = MathHelper.floor(explosionPos.getY() + q + 1.0);
		int t = MathHelper.floor(explosionPos.getZ() - q - 1.0);
		int u = MathHelper.floor(explosionPos.getZ() + q + 1.0);

		List<Entity> entityList = serverWorld.getOtherEntities(this, new Box(k, r, t, l, s, u));
		DamageSource source = serverWorld.getDamageSources().explosion(this, this);

		for (Entity targetEntity : entityList) {
			if (!shouldBurstAffect(targetEntity)) continue;

			// 归一化距离
			double w = Math.sqrt(targetEntity.squaredDistanceTo(explosionPos)) / (double) q;
			if (w > 1.0) continue;

			double dx = targetEntity.getX() - explosionPos.getX();
			double dy = targetEntity.getEyeY() - explosionPos.getY();
			double dz = targetEntity.getZ() - explosionPos.getZ();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist == 0.0) continue;

			dx /= dist;
			dy /= dist;
			dz /= dist;

			// 暴露度（视线检查）
			double exposure = Explosion.getExposure(explosionPos, targetEntity);
			double intensity = (1.0 - w) * exposure;

			// 爆炸伤害（原版公式）
			float damage = (float) ((int) ((intensity * intensity + intensity) / 2.0 * 7.0 * (double) q + 1.0));
			targetEntity.damage(source, damage);

			// 爆炸击退（受爆炸保护附魔减免）
			double knockback;
			if (targetEntity instanceof LivingEntity living) {
				knockback = ProtectionEnchantment.transformExplosionKnockback(living, intensity);
			} else {
				knockback = intensity;
			}
			targetEntity.setVelocity(targetEntity.getVelocity().add(dx * knockback, dy * knockback, dz * knockback));
		}
	}

	/** 水流爆破的目标筛选 */
	private boolean shouldBurstAffect(Entity target) {
		// 同类不伤（避免内讧）
		if (target instanceof AxolotlShifterEntity) return false;
		// 爆炸免疫实体
		if (target.isImmuneToExplosion()) return false;
		// 原版水花爆炸免疫 tag（盔甲架/展示框/掉落物等）
		if (target.getType().isIn(WATER_EXPLODE_IMMUNE)) return false;
		return true;
	}

	/**
	 * 近战攻击：4 点物理伤害（伤害类型 effects=hurt，正常受护甲与保护附魔格挡；
	 * 2026-10-20 已从 minecraft:bypasses_armor 标签移除，死亡消息保留）。
	 * 疾跑状态下命中时播放原版冲刺攻击的水花粒子/音效并耗 10 湿润。
	 */
	@Override
	public boolean tryAttack(Entity target) {
		DamageSource source = this.getDamageSources().create(ATTACK_DAMAGE_KEY, this, this);
		if (!target.damage(source, 4.0f)) return false;

		if (this.isSprinting() && !this.isTouchingWater() && this.moisture > 0) {
			// 音效 + 粒子（原版：rain 8 + 深蓝尘 8，splash 0.5/0.8；击退增强已移除）
			this.playSound(SoundEvents.ENTITY_AXOLOTL_SPLASH, 0.5f, 0.8f);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				ParticleUtils.spawnParticles(serverWorld, ParticleTypes.RAIN,
						target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.5, 0.5, 0.5, 0.0);
				ParticleUtils.spawnParticles(serverWorld, DEEP_BLUE_DUST,
						target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.5, 0.5, 0.5, 0.0);
			}
			this.moisture = Math.max(0, this.moisture - POUNCE_MOISTURE_COST);
		}
		return true;
	}

	/**
	 * 水中高机动（复刻 water_flexibility 0.93：水中阻力系数 0.93）
	 * 陆地非疾跑时减速 30%（复刻 ground_speed_down multiply_total -0.3）
	 * 疾跑时非冰面滑溜 0.35（复刻 slipperiness power：速度保留更少惯性衰减，此处以轻微加速冲量近似）
	 */
	@Override
	public void travel(Vec3d movementInput) {
		if (this.isLogicalSideForUpdatingMovement() && this.isTouchingWater()) {
			this.updateVelocity(this.getMovementSpeed(), movementInput);
			this.move(net.minecraft.entity.MovementType.SELF, this.getVelocity());
			// water_flex 0.93：水平保留 93% 速度（原版水中阻力为 ×0.8）
			this.setVelocity(this.getVelocity().multiply(0.93, 0.85, 0.93));
			// 上下游动与上岸由移动控制器按路径处理，撞墙本身不再触发无条件顶跳。
			this.updateLimbs(false);
		} else {
			if (!this.isSprinting()) {
				movementInput = movementInput.multiply(0.7, 0.7, 0.7);
			}
			super.travel(movementInput);
		}
	}

	/** 摔落保护：≤6 格完全免伤，超出部分才结算（复刻 falling_protection fall_distance 6） */
	@Override
	public boolean handleFallDamage(float fallDistance, float multiplier, DamageSource damageSource) {
		if (fallDistance <= 6.0f) return false;
		return super.handleFallDamage(fallDistance - 6.0f, multiplier, damageSource);
	}

	/**
	 * 环境伤害（火焰/岩浆/热块/浆果丛/仙人掌/冰冻）→ 惊慌：触发短时惊慌状态，
	 * 由 PanicToWaterGoal 最高优先级带它钻进最近水源躲避（用户需求）。
	 * 排除自身脱水真实伤害，避免惊慌死循环。
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean result = super.damage(source, amount);
		if (result && !this.getWorld().isClient && this.isAlive() && isEnvironmentalHazard(source)) {
			this.panicTicks = PANIC_DURATION;
		}
		return result;
	}

	/** 判定是否为「会让美西螈惊慌钻水」的环境伤害（排除脱水自伤，避免死循环） */
	private boolean isEnvironmentalHazard(DamageSource source) {
		if (source.getTypeRegistryEntry().matchesKey(THIRST_DAMAGE_KEY)) return false; // 脱水自伤不算
		if (source.isIn(DamageTypeTags.IS_FIRE)) return true;                          // 火/岩浆/热块/火球
		if (source.isIn(DamageTypeTags.IS_FREEZING)) return true;                      // 冰冻
		String name = source.getName();
		return "sweetBerryBush".equals(name) || "cactus".equals(name);                 // 浆果丛扎 / 仙人掌
	}

	/** 水生阵营（对应 aquatic power：药水效果按水生组结算） */
	@Override
	public EntityGroup getGroup() {
		return EntityGroup.AQUATIC;
	}

	/** 不被水流推动（对应 like_water power） */
	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	/** 湿润度载体自管：水中不掉氧、不溺水 */
	@Override
	public boolean canBreatheInWater() {
		return true;
	}

	// ========== 持久化 ==========

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Moisture", this.moisture);
		nbt.putInt("BurstCooldown", this.burstCooldown);
		nbt.putInt("MoistureBonus", this.moistureBonus);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.moisture = nbt.contains("Moisture") ? MathHelper.clamp(nbt.getInt("Moisture"), 0, MAX_MOISTURE) : MAX_MOISTURE;
		this.burstCooldown = nbt.getInt("BurstCooldown");
		// attribute modifier 不随 NBT 持久化，读档后置哨兵值强制首个 tick 重算湿润度生命档
		this.moistureBonus = Integer.MIN_VALUE;
		// 临时状态不持久化：读档后清零（惊慌/战斗提速 modifier 均不随存档）
		this.panicTicks = 0;
		this.combatSpeedApplied = false;
		this.recoveringMoisture = false;
	}

	// ========== 音效（美西螈音效，对应 form_axolotl_sound 系列） ==========

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_AXOLOTL_IDLE_AIR;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_AXOLOTL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_AXOLOTL_DEATH;
	}

	// ========== GeoEntity 实现 ==========

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// 移动动画（走路/待机，骨骼变换由渲染器程序化驱动）
		controllers.add(new AnimationController<>(this, "movement", 3, state -> {
			if (state.isMoving()) {
				state.setAnimation(RawAnimation.begin().then("walk", Animation.LoopType.LOOP));
			} else {
				state.setAnimation(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
			}
			return PlayState.CONTINUE;
		}));
		// 攻击动画
		controllers.add(new AnimationController<>(this, "attack", 0, state -> {
			if (this.handSwinging) {
				return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
			}
			state.getController().forceAnimationReset();
			return PlayState.STOP;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	// ========== 水陆移动与自定义 AI 目标 ==========

	static class ShifterNavigation extends AmphibiousSwimNavigation {
		ShifterNavigation(MobEntity entity, World world) { super(entity, world); }

		@Override
		protected PathNodeNavigator createPathNodeNavigator(int range) {
			this.nodeMaker = new AmphibiousPathNodeMaker(false) {
				@Override public void init(ChunkCache cache, MobEntity entity) {
					super.init(cache, entity);
					// 水栖倾向由行为目标决定，战斗路径不能因原版的陆地代价绕着岸边打转。
					entity.setPathfindingPenalty(PathNodeType.WALKABLE, 0.0f);
					entity.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0f);
				}
			};
			this.nodeMaker.setCanEnterOpenDoors(true);
			return new PathNodeNavigator(this.nodeMaker, range);
		}

		@Override protected Vec3d getPos() { return this.entity.getPos(); }
		@Override protected boolean canPathDirectlyThrough(Vec3d from, Vec3d to) {
			// 保留逐节点的台阶/岸边路线，避免水中的直线捷径跨过实际不能攀上的岸壁。
			return false;
		}
	}

	static class ShifterMoveControl extends MoveControl {
		private final AquaticMoveControl swimming;

		ShifterMoveControl(MobEntity entity) {
			super(entity);
			this.swimming = new AquaticMoveControl(entity, 85, 45, 0.2f, 1.0f, false);
		}

		@Override public void moveTo(double x, double y, double z, double speed) {
			super.moveTo(x, y, z, speed);
			this.swimming.moveTo(x, y, z, speed);
		}

		@Override public void tick() {
			if (this.entity.isTouchingWater()) {
				this.swimming.tick();
				// 只有路径下一步确实上岸、且头顶有空间时才跃上去；不会对着水下墙壁连跳。
				if (this.state == State.MOVE_TO && !this.entity.getNavigation().isIdle()
						&& this.entity.horizontalCollision && this.targetY > this.entity.getY() + 0.1
						&& this.targetY <= this.entity.getY() + 1.25
						&& !this.entity.getWorld().getFluidState(BlockPos.ofFloored(this.targetX, this.targetY, this.targetZ)).isIn(FluidTags.WATER)
						&& this.entity.getWorld().isSpaceEmpty(this.entity, this.entity.getBoundingBox().offset(0.0, 0.6, 0.0))) {
					Vec3d velocity = this.entity.getVelocity();
					this.entity.setVelocity(velocity.x, Math.max(velocity.y, 0.42), velocity.z);
				}
			} else {
				this.entity.setUpwardSpeed(0.0f);
				this.entity.setPitch(this.wrapDegrees(this.entity.getPitch(), 0.0f, 10.0f));
				super.tick(); // 陆地继续使用原版步行、转向和台阶跳跃。
			}
		}
	}

	/** 寻水、撤退和灭火共用路径生命周期：走完或连续卡住后再重算，不在行进中不断换点。 */
	static abstract class WaterSeekingGoal extends Goal {
		protected final AxolotlShifterEntity shifter;
		private final int range;
		private final double speed;
		private final boolean recovery;
		@Nullable private BlockPos waterPos;
		private final Set<BlockPos> rejected = new HashSet<>();
		private int nextSearchAge;
		private int rejectedUntil;
		private int lastProgressAge;
		private Vec3d lastProgressPos = Vec3d.ZERO;
		private float exploreYaw;

		WaterSeekingGoal(AxolotlShifterEntity shifter, int range, double speed, boolean recovery) {
			this.shifter = shifter;
			this.range = range;
			this.speed = speed;
			this.recovery = recovery;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override public boolean shouldRunEveryTick() { return true; }

		@Override public void start() {
			this.shifter.recoveringMoisture = this.recovery;
			this.nextSearchAge = this.shifter.age;
			this.lastProgressAge = this.shifter.age;
			this.lastProgressPos = this.shifter.getPos();
			this.exploreYaw = this.shifter.getYaw();
			this.waterPos = null;
			this.rejected.clear();
		}

		@Override public void tick() {
			if (this.shifter.isTouchingWater()) {
				this.shifter.getNavigation().stop();
				// 到水里停稳补润，避免惯性又把它带回岸上。
				this.shifter.setVelocity(this.shifter.getVelocity().multiply(0.6));
				return;
			}
			boolean moving = !this.shifter.getNavigation().isIdle();
			if (this.shifter.getPos().squaredDistanceTo(this.lastProgressPos) >= 0.0625) {
				this.lastProgressPos = this.shifter.getPos();
				this.lastProgressAge = this.shifter.age;
			}
			boolean stuck = moving && this.shifter.age - this.lastProgressAge >= 40;
			boolean waterGone = this.waterPos != null
					&& !this.shifter.getWorld().getFluidState(this.waterPos).isIn(FluidTags.WATER);
			if (stuck || waterGone || (!moving && this.waterPos != null)) {
				if (this.waterPos != null) {
					for (BlockPos pos : BlockPos.iterateOutwards(this.waterPos, 1, 1, 1)) this.rejected.add(pos.toImmutable());
					this.rejectedUntil = this.shifter.age + 200;
				}
				this.waterPos = null;
				this.shifter.getNavigation().stop();
				this.exploreYaw += 90 + this.shifter.getRandom().nextInt(90);
				this.lastProgressAge = this.shifter.age;
				moving = false;
			}
			if (this.waterPos != null || this.shifter.age < this.nextSearchAge) return;
			this.nextSearchAge = this.shifter.age + 40;
			if (this.shifter.age >= this.rejectedUntil) this.rejected.clear();
			Path path = this.shifter.findWaterPath(this.range, 8, this.rejected);
			if (path != null && this.shifter.getNavigation().startMovingAlong(path, this.speed)) {
				this.waterPos = path.getTarget();
				this.lastProgressAge = this.shifter.age;
			} else if (!moving) {
				if (!this.shifter.wanderTowardYaw(this.exploreYaw, this.recovery ? 0.8 : this.speed)) this.exploreYaw += 90;
				this.lastProgressAge = this.shifter.age;
			}
		}

		@Override public void stop() {
			if (this.recovery) this.shifter.recoveringMoisture = false;
			this.waterPos = null;
			this.shifter.getNavigation().stop();
		}
	}

	/** 非战斗缺水时入水，补到 240 再恢复活动；湿润充足时允许在岸边生活。 */
	static class SeekWaterGoal extends WaterSeekingGoal {
		private int nextScanAge;
		SeekWaterGoal(AxolotlShifterEntity shifter) { super(shifter, SEEK_WATER_RANGE, 1.1, true); }

		@Override public boolean canStart() {
			if (this.shifter.getTarget() != null || this.shifter.isPanicking() || this.shifter.age < this.nextScanAge) return false;
			this.nextScanAge = this.shifter.age + 20;
			if (this.shifter.getMoisture() < FIND_WATER_THRESHOLD) return true;
			return !this.shifter.isTouchingWater() && this.shifter.findNearestWater(NEAR_WATER_RADIUS, 4) == null;
		}

		@Override public boolean shouldContinue() {
			if (this.shifter.getTarget() != null || this.shifter.isPanicking()) return false;
			if (this.shifter.isTouchingWater()) return this.shifter.getMoisture() < RETREAT_RECOVER_MOISTURE;
			// 已经开始补水后，不会因为途中刚好接近水边就提前交还控制权。
			return this.shifter.getMoisture() < RETREAT_RECOVER_MOISTURE
					|| this.shifter.findNearestWater(NEAR_WATER_RADIUS, 4) == null;
		}
	}

	/** 战斗低于 90 时撤退；失去攻击目标也会把这一轮补水完成，避免往返抖动。 */
	static class CombatRetreatToWaterGoal extends WaterSeekingGoal {
		CombatRetreatToWaterGoal(AxolotlShifterEntity shifter) { super(shifter, 12, 1.15, true); }
		@Override public boolean canStart() {
			return this.shifter.getTarget() != null && !this.shifter.isPanicking()
					&& this.shifter.getMoisture() < RETREAT_MOISTURE_THRESHOLD
					&& (this.shifter.isTouchingWater() || this.shifter.findNearestWater(12, 4) != null);
		}
		@Override public boolean shouldContinue() {
			return !this.shifter.isPanicking() && this.shifter.getMoisture() < RETREAT_RECOVER_MOISTURE;
		}
	}

	/** 环境惊慌先找可达水源；无水且正在战斗时按已有规则继续反击。 */
	static class PanicToWaterGoal extends WaterSeekingGoal {
		private int nextWaterScanAge;
		private boolean noWaterNearby;
		PanicToWaterGoal(AxolotlShifterEntity shifter) { super(shifter, SEEK_WATER_RANGE, 1.05, false); }
		private boolean shouldYieldToCombat() {
			if (this.shifter.getTarget() == null) return false;
			if (this.shifter.age >= this.nextWaterScanAge) {
				this.nextWaterScanAge = this.shifter.age + 40;
				this.noWaterNearby = this.shifter.findWaterPath(SEEK_WATER_RANGE, 8, Set.of()) == null;
			}
			return this.noWaterNearby;
		}
		@Override public boolean canStart() {
			return this.shifter.isPanicking() && !this.shifter.isTouchingWater() && !shouldYieldToCombat();
		}
		@Override public boolean shouldContinue() { return canStart(); }
	}

	/** 偶尔采摘成熟浆果；只启动完整可达的路径，失败或超时后让出移动控制权。 */
	static class EatGlowBerriesGoal extends Goal {
		private final AxolotlShifterEntity shifter;
		@Nullable private BlockPos berryPos;
		@Nullable private Path berryPath;
		private int nextScanAge;
		private int finishAge;

		EatGlowBerriesGoal(AxolotlShifterEntity shifter) {
			this.shifter = shifter;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}
		@Override public boolean canStart() {
			if (this.shifter.getTarget() != null || this.shifter.isPanicking() || this.shifter.eatCooldown > 0
					|| this.shifter.age < this.nextScanAge) return false;
			this.nextScanAge = this.shifter.age + 30;
			for (BlockPos pos : BlockPos.iterateOutwards(this.shifter.getBlockPos(), EAT_SCAN_RANGE, 6, EAT_SCAN_RANGE)) {
				if (!CaveVines.hasBerries(this.shifter.getWorld().getBlockState(pos))) continue;
				// 藤蔓可以悬在头顶，落脚点不必与浆果处于同一高度。
				for (int below = 0; below <= 2; below++) {
					Path path = this.shifter.getNavigation().findPathTo(pos.down(below), 0);
					if (path != null && path.reachesTarget()) {
						this.berryPos = pos.toImmutable();
						this.berryPath = path;
						return true;
					}
				}
			}
			return false;
		}
		@Override public boolean shouldContinue() {
			return this.berryPos != null && this.shifter.getTarget() == null && !this.shifter.isPanicking()
					&& this.shifter.age < this.finishAge && (!this.shifter.getNavigation().isIdle() || canReachBerry())
					&& CaveVines.hasBerries(this.shifter.getWorld().getBlockState(this.berryPos));
		}
		@Override public void start() {
			this.finishAge = this.shifter.age + 200;
			this.shifter.getNavigation().startMovingAlong(this.berryPath, 1.0);
		}
		@Override public void tick() {
			if (this.berryPos == null) return;
			Vec3d berry = Vec3d.ofCenter(this.berryPos);
			this.shifter.getLookControl().lookAt(berry.x, berry.y, berry.z);
			if (canReachBerry()) {
				this.shifter.eatGlowBerryAt(this.berryPos);
				this.berryPos = null;
			}
		}
		private boolean canReachBerry() {
			if (this.berryPos == null) return false;
			Vec3d berry = Vec3d.ofCenter(this.berryPos);
			return this.shifter.getEyePos().squaredDistanceTo(berry) <= 4.0
					&& this.shifter.getWorld().raycast(new RaycastContext(this.shifter.getEyePos(), berry,
							RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.shifter)).getType() == HitResult.Type.MISS;
		}
		@Override public void stop() {
			this.berryPos = null;
			this.berryPath = null;
			this.shifter.getNavigation().stop();
			this.shifter.eatCooldown = EAT_COOLDOWN_MIN
					+ this.shifter.getRandom().nextInt(EAT_COOLDOWN_MAX - EAT_COOLDOWN_MIN + 1);
		}
	}
}
