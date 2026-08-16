package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.GhostCatEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔（Nightmare）次要技能「惊吓」（Spook）—— 服务端权威。
 *
 * <p>按键（sp_secondary，CD 8s）对所有已入梦目标生效：</p>
 * <ul>
 *   <li><b>幽灵苦力怕</b>：目标身后空气位生成真实 {@link CreeperEntity}（NoAI/无敌/对他人
 *       隐身），仅目标客户端可见（mixin 局部显形）；引信音效与到期爆炸声/粒全部
 *       <b>S2C 直发目标</b>（他人零感知）；3 秒后无实伤爆除。</li>
 *   <li><b>幽灵野猫</b>：目标攻击苦力怕后，从苦力怕消散处生成真实
 *       {@link GhostCatEntity}（野猫形态 geo 模型，NoAI/无敌/对他人隐身，仅目标可见），先
 *       <b>猫哈气</b>（定向音效），朝目标奔跑（服务端手动驱动位移），距目标 4 格<b>起跳扑脸</b>，
 *       贴身挥爪（12 点魔法伤害），随后烟雾消散 + 头顶仅目标可见的烟花。</li>
 * </ul>
 *
 * <p>定向声画原则：所有声音/粒子只用 {@code PlaySoundS2CPacket}/{@code ParticleS2CPacket}
 * 直发给目标本人（服务端权威 + 只有目标听得到/看得到 + 带空间方位），绝不 world.playSound
 * 广播（那会把幽灵泄漏给周围所有人）。</p>
 */
public final class NightmareSpookManager {

	/** 技能 CD（tick，8 秒）。 */
	public static final int SPOOK_COOLDOWN_TICKS = 160;
	/** 幽灵野猫攻击伤害（魔法）。 */
	public static final float CLONE_DAMAGE = 12.0f;
	/** 幽灵苦力怕存活（tick，3 秒）。 */
	public static final int CREEPER_LIFE_TICKS = 60;
	/** 幽灵野猫存活上限（tick，兜底；正常 ~1.5-2.5 秒完成扑咬）。 */
	public static final int CAT_LIFE_TICKS = 90;
	/** 野猫奔跑步速（格/tick）。 */
	public static final double CAT_RUN_SPEED = 0.32;
	/** 野猫距目标多远起跳（格）。 */
	public static final double CAT_LEAP_DISTANCE = 4.0;

	/** 活跃幽灵苦力怕：实体 UUID -> 目标玩家 UUID。 */
	private static final Map<UUID, UUID> CREEPERS = new ConcurrentHashMap<>();
	/** 活跃幽灵野猫：实体 UUID -> CatState。 */
	private static final Map<UUID, CatState> CATS = new ConcurrentHashMap<>();

	/** 幽灵野猫运行态。 */
	private static final class CatState {
		final ServerPlayerEntity target;
		final UUID casterUuid;
		final long bornTick;
		/** 阶段：0=追跑 1=跳跃中 2=已挥爪(待消散)。 */
		int phase;
		/** 跳跃起点（插值抛物线用）。 */
		double jumpFromX, jumpFromY, jumpFromZ;
		/** 跳跃已进行 tick。 */
		int jumpTicks;

		CatState(ServerPlayerEntity target, UUID casterUuid, long bornTick) {
			this.target = target;
			this.casterUuid = casterUuid;
			this.bornTick = bornTick;
		}
	}

	private NightmareSpookManager() {
	}

	/** 次技能入口（power action 调用，仅服务端）。无入梦目标返回 false（不进 CD）。 */
	public static boolean execute(ServerPlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world)) return false;
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD);
		if (cd > 0) return false;

		long now = world.getTime();
		List<LivingEntity> targets = NightmareDreamManager.collectDreamTargets(player, now);
		if (targets.isEmpty()) return false;

		boolean any = false;
		for (LivingEntity target : targets) {
			if (target instanceof ServerPlayerEntity sp) {
				// 玩家目标：幽灵苦力怕幻象全套（引信 → 回击 → 幽灵野猫扑脸）
				if (spawnGhostCreeper(world, player, sp)) any = true;
			} else {
				// 非玩家目标（用户定稿）：无幻象，直接被幽灵野猫撕咬（12 点魔法伤害）；
				// 入梦时间重置为 20 秒满额，不强制退出（无恐惧联动的强制苏醒）。
				if (directMobBite(world, player, target, now)) any = true;
			}
		}
		if (!any) return false;
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, SPOOK_COOLDOWN_TICKS);
		return true;
	}

	/** 非玩家目标：无形的幽灵野猫直接撕咬（定向声效 + 伤害 + 入梦重置 20s）。 */
	private static boolean directMobBite(ServerWorld world, ServerPlayerEntity caster, LivingEntity target, long now) {
		if (!target.isAlive() || target.getWorld() != world) return false;
		// 白名单保护：白名单内生物不受惊吓伤害
		if (net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isProtected(caster, target)) return false;
		// 幽灵野猫贴身一击：12 点魔法伤害（同 completeCatAttack 的伤害源，可归因食梦魔）
		target.damage(caster.getDamageSources().indirectMagic(caster, caster), CLONE_DAMAGE);
		// 入梦时间重置为 20 秒满额（不强制退出；与恐惧的重置同额）
		NightmareDreamManager.resetDream(caster.getUuid(), target.getUuid(), now);
		// 声效与粒子（生物无客户端屏幕：全员可闻的低沉猫啦 + 目标位置烟雾）
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_CAT_HISS, net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.85f);
		world.spawnParticles(ParticleTypes.CLOUD,
				target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		return true;
	}

	// ==================== 定向声画（仅目标本人） ====================

	/** 定向音效：仅目标听到（S2C 直发，带空间方位衰减）。 */
	private static void playSoundAt(ServerPlayerEntity target, SoundEvent sound, double x, double y, double z,
	                                float volume, float pitch) {
		RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(sound);
		target.networkHandler.sendPacket(new PlaySoundS2CPacket(
				entry, SoundCategory.HOSTILE, x, y, z, volume, pitch, target.getRandom().nextLong()));
	}

	/** 定向粒子：仅目标看到（S2C 直发）。 */
	private static void spawnParticlesAt(ServerPlayerEntity target, ParticleEffect particle,
	                                     double x, double y, double z, int count, double ox, double oy, double oz) {
		target.networkHandler.sendPacket(new ParticleS2CPacket(particle, true, x, y, z,
				(float) ox, (float) oy, (float) oz, 0.0f, count));
	}

	// ==================== 幽灵苦力怕 ====================

	/** 在目标身后 180° 随机方向 2~3 格的空气位置生成幽灵苦力怕（真实体，仅目标可见）。 */
	private static boolean spawnGhostCreeper(ServerWorld world, ServerPlayerEntity caster, LivingEntity target) {
		if (!(target instanceof ServerPlayerEntity sp)) return false;
		Vec3d pos = findAirPosBehind(world, target, 2.0, 3.0, 16, 0.9, 1.5);
		if (pos == null) return false;

		CreeperEntity creeper = EntityType.CREEPER.create(world);
		creeper.refreshPositionAndAngles(pos.x, pos.y, pos.z, target.getHeadYaw() + 180.0f, 0.0f);
		// 幽灵化数据：禁 AI（站桩）、无敌、对他人隐身、无重力、持久化
		creeper.setAiDisabled(true);
		creeper.setInvulnerable(true);
		creeper.setInvisible(true);
		creeper.setNoGravity(true);
		creeper.setPersistent();
		creeper.addCommandTag("ssca_ghost_creeper");
		if (!world.spawnEntity(creeper)) return false;
		CREEPERS.put(creeper.getUuid(), sp.getUuid());
		// 幽灵标记包（仅目标）：客户端对该实体局部取消 invisible
		SscAddonNetworking.sendSpookGhost(sp, creeper.getUuid(), CREEPER_LIFE_TICKS);
		// 引信嘶嘶声（定向，仅目标听到，从幽灵方向传来）
		playSoundAt(sp, SoundEvents.ENTITY_CREEPER_PRIMED, pos.x, pos.y, pos.z, 1.0f, 1.0f);
		return true;
	}

	/** 目标攻击幽灵苦力怕（服务端 AttackEntityCallback）：击杀苦力怕 + 召出幽灵野猫。 */
	public static void onGhostCreeperAttacked(ServerPlayerEntity attacker, CreeperEntity creeper) {
		UUID targetUuid = CREEPERS.get(creeper.getUuid());
		if (targetUuid == null) return;
		if (!attacker.getUuid().equals(targetUuid)) return; // 只有目标本人的攻击有效
		CREEPERS.remove(creeper.getUuid());
		ServerWorld world = (ServerWorld) attacker.getWorld();
		// 击杀音效/烟雾（定向，仅目标）
		playSoundAt(attacker, SoundEvents.ENTITY_CREEPER_DEATH, creeper.getX(), creeper.getY(), creeper.getZ(), 0.8f, 1.0f);
		spawnParticlesAt(attacker, ParticleTypes.CLOUD, creeper.getX(), creeper.getBodyY(0.5), creeper.getZ(), 8, 0.3, 0.3, 0.3);
		Vec3d creeperPos = creeper.getPos();
		creeper.discard();
		spawnGhostCat(world, attacker, creeperPos);
	}

	// ==================== 幽灵野猫 ====================

	/** 从苦力怕消散处生成幽灵野猫（野猫形态模型真实体，仅目标可见）→ 奔跑 → 跳脸攻击。 */
	private static void spawnGhostCat(ServerWorld world, ServerPlayerEntity target, Vec3d nearPos) {
		// 生成点：优先苦力怕原位置附近（野猫「从苦力怕消散处扑出」），失败退目标身后
		Vec3d pos = findAirPosNear(world, nearPos, 1.5, 8, 0.9, 1.0);
		if (pos == null) pos = findAirPosBehind(world, target, 2.0, 3.0, 12, 0.9, 1.0);
		if (pos == null) return;

		GhostCatEntity cat = net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.GHOST_CAT_ENTITY.create(world);
		cat.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		// 幽灵化：NoAI + 无敌 + 隐身（仅目标客户端显形）；保留重力（要贴地跑/跳）
		cat.setAiDisabled(true);
		cat.setInvulnerable(true);
		cat.setInvisible(true);
		cat.setPersistent();
		cat.addCommandTag("ssca_ghost_cat");
		if (!world.spawnEntity(cat)) return;
		long now = world.getTime();
		CATS.put(cat.getUuid(), new CatState(target, target.getUuid(), now));
		// 显形标记包（仅目标）
		SscAddonNetworking.sendSpookGhost(target, cat.getUuid(), CAT_LIFE_TICKS);
		// 猫哈气音效（定向，仅目标听到）
		playSoundAt(target, SoundEvents.ENTITY_CAT_HISS, pos.x, pos.y, pos.z, 1.0f, 1.0f);
		// 出场小烟雾（定向粒子，仅目标）
		spawnParticlesAt(target, ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 6, 0.25, 0.25, 0.25);
	}

	/** 每 tick 推进幽灵野猫：追跑 → 起跳 → 贴身挥爪（12 魔法伤）。 */
	private static void tickGhostCat(ServerWorld world, CatState st, GhostCatEntity cat) {
		ServerPlayerEntity target = st.target;
		long now = world.getTime();
		long age = now - st.bornTick;
		// 兜底：超时没咬到 / 目标已死 → 烟雾消散
		if (age >= CAT_LIFE_TICKS || !target.isAlive() || target.getWorld() != world) {
			spawnParticlesAt(target, ParticleTypes.CLOUD, cat.getX(), cat.getBodyY(0.5), cat.getZ(), 8, 0.3, 0.3, 0.3);
			cat.discard();
			CATS.remove(cat.getUuid());
			return;
		}
		Vec3d catPos = cat.getPos();
		Vec3d eyePos = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
		Vec3d toEye = eyePos.subtract(catPos);
		double dist = toEye.length();
		// 面向目标
		float yaw = (float) Math.toDegrees(Math.atan2(toEye.x, toEye.z)) - 90.0f;
		cat.setYaw(yaw);
		cat.headYaw = yaw;
		cat.bodyYaw = yaw;

		if (st.phase == 0) {
			// 追跑：朝目标移动（NoAI 下手动驱动；limbAnimator 由位置差驱动腿部动画）
			Vec3d dir = toEye.normalize();
			Vec3d next = catPos.add(dir.multiply(CAT_RUN_SPEED));
			cat.setPosition(next.x, next.y, next.z);
			// 距离 4 格 → 起跳扑脸
			if (dist <= CAT_LEAP_DISTANCE) {
				st.phase = 1;
				st.jumpFromX = catPos.x;
				st.jumpFromY = catPos.y;
				st.jumpFromZ = catPos.z;
				st.jumpTicks = 0;
				// 扑击前低吼（猫叫降调，定向）
				playSoundAt(target, SoundEvents.ENTITY_CAT_AMBIENT, catPos.x, catPos.y, catPos.z, 0.9f, 0.75f);
			}
		} else if (st.phase == 1) {
			// 跳跃：插值抛物线扑向目标脸部（8t / 0.4s）
			st.jumpTicks++;
			float t = Math.min(1.0f, st.jumpTicks / 8.0f);
			double nx = st.jumpFromX + (target.getX() - st.jumpFromX) * t;
			double ny = st.jumpFromY + (target.getBodyY(0.5) - 1.4 - st.jumpFromY) * t
					+ Math.sin(t * Math.PI) * 0.9; // 弧顶 +0.9 格
			double nz = st.jumpFromZ + (target.getZ() - st.jumpFromZ) * t;
			// 防卡墙：跳跃路径前方有方块 → 直接结算贴身攻击
			BlockPos front = BlockPos.ofFloored(nx, ny, nz);
			if (!world.getBlockState(front).isAir()) {
				completeCatAttack(world, st, cat, target);
				return;
			}
			cat.setPosition(nx, ny, nz);
			if (t >= 1.0f) {
				completeCatAttack(world, st, cat, target);
			}
		}
	}

	/** 野猫贴身挥爪：12 魔法伤 + 命中音效/粒子，随后消散。 */
	private static void completeCatAttack(ServerWorld world, CatState st, GhostCatEntity cat, ServerPlayerEntity target) {
		if (st.phase == 2) return;
		st.phase = 2;
		ServerPlayerEntity caster = world.getServer().getPlayerManager().getPlayer(st.casterUuid);
		if (caster != null && target.isAlive() && target.getWorld() == world) {
			target.damage(caster.getDamageSources().indirectMagic(caster, caster), CLONE_DAMAGE);
			// 命中音效：哈气 + 挥击（定向，仅目标）
			playSoundAt(target, SoundEvents.ENTITY_CAT_HISS, target.getX(), target.getY(), target.getZ(), 1.2f, 0.9f);
			playSoundAt(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, target.getX(), target.getY(), target.getZ(), 0.7f, 1.2f);
		}
		// 消散：烟雾 + 目标头顶烟花（定向粒子，仅目标）
		spawnParticlesAt(target, ParticleTypes.CLOUD, cat.getX(), cat.getBodyY(0.5), cat.getZ(), 10, 0.35, 0.35, 0.35);
		spawnParticlesAt(target, ParticleTypes.FIREWORK, cat.getX(), cat.getBodyY(1.8), cat.getZ(), 3, 0.25, 0.1, 0.25);
		cat.discard();
		CATS.remove(cat.getUuid());
	}

	// ==================== 生成点采样 ====================

	/** 目标身后 180° 扇区找空气落点（碰撞盒无方块 + 向下 ≤2 格贴地）。 */
	private static Vec3d findAirPosBehind(ServerWorld world, LivingEntity target, double minDist, double maxDist,
	                                      int tries, double width, double height) {
		float behindYaw = target.getHeadYaw() + 180.0f;
		for (int i = 0; i < tries; i++) {
			float yaw = behindYaw + (world.random.nextFloat() - 0.5f) * 180.0f;
			double dist = minDist + world.random.nextDouble() * (maxDist - minDist);
			double cx = target.getX() - Math.sin(Math.toRadians(yaw)) * dist;
			double cz = target.getZ() + Math.cos(Math.toRadians(yaw)) * dist;
			Vec3d pos = tryGroundedAir(world, cx, target.getY(), cz, width, height);
			if (pos != null) return pos;
		}
		return null;
	}

	/** 某点周围找空气落点（环形随机采样）。 */
	private static Vec3d findAirPosNear(ServerWorld world, Vec3d center, double radius, int tries,
	                                    double width, double height) {
		for (int i = 0; i < tries; i++) {
			float ang = world.random.nextFloat() * (float) (Math.PI * 2);
			double r = 0.8 + world.random.nextDouble() * radius;
			double cx = center.x + Math.cos(ang) * r;
			double cz = center.z + Math.sin(ang) * r;
			Vec3d pos = tryGroundedAir(world, cx, center.y, cz, width, height);
			if (pos != null) return pos;
		}
		return null;
	}

	/** 校验 (cx, cy, cz)：碰撞盒空间无方块 + 向下 ≤2 格贴地，返回最终落点。 */
	private static Vec3d tryGroundedAir(ServerWorld world, double cx, double cy, double cz,
	                                    double width, double height) {
		Box box = new Box(cx - width / 2, cy, cz - width / 2, cx + width / 2, cy + height, cz + width / 2);
		if (!world.isSpaceEmpty(null, box)) return null;
		// 向下最多 2 格找贴地（避免悬空）
		BlockPos.Mutable mpos = new BlockPos.Mutable(Math.floor(cx), (int) Math.floor(cy), Math.floor(cz));
		int drop = 0;
		while (drop < 2 && world.getBlockState(mpos.down(drop + 1)).isAir()) {
			drop++;
		}
		if (drop >= 2) return null; // 悬空超过 2 格 → 放弃该采样点
		return new Vec3d(cx, cy - drop, cz);
	}

	// ==================== 查询 / 生命周期 ====================

	/** 服务端查询：被攻击实体是否幽灵苦力怕（tag 快路径）。 */
	public static boolean isGhostCreeperEntity(Entity e) {
		return e instanceof CreeperEntity && e.getCommandTags().contains("ssca_ghost_creeper");
	}

	/** 每 tick 推进（由 SscAddonServerEvents 调用）：苦力怕引信/到期、野猫追击/扑咬。 */
	public static void tick(ServerPlayerEntity player) {
		if (CREEPERS.isEmpty() && CATS.isEmpty()) return;
		if (!(player.getWorld() instanceof ServerWorld world)) return;

		// 幽灵苦力怕：只由目标本人的 tick 推进（去重）
		Iterator<Map.Entry<UUID, UUID>> cit = CREEPERS.entrySet().iterator();
		while (cit.hasNext()) {
			Map.Entry<UUID, UUID> e = cit.next();
			if (!e.getValue().equals(player.getUuid())) continue;
			Entity creeper = world.getEntity(e.getKey());
			if (!(creeper instanceof CreeperEntity gc)) {
				cit.remove();
				continue;
			}
			if (gc.age >= CREEPER_LIFE_TICKS) {
				// 到期：无实伤爆除（声/粒直发目标，不广播）
				cit.remove();
				playSoundAt(player, SoundEvents.ENTITY_GENERIC_EXPLODE, gc.getX(), gc.getY(), gc.getZ(), 1.0f, 1.0f);
				spawnParticlesAt(player, ParticleTypes.EXPLOSION_EMITTER, gc.getX(), gc.getBodyY(0.5), gc.getZ(), 1, 0, 0, 0);
				gc.discard();
				continue;
			}
			// 最后 1.2 秒：点燃引信（视觉闪白）
			if (gc.age >= CREEPER_LIFE_TICKS - 24) {
				gc.setFuseSpeed(1);
			}
		}

		// 幽灵野猫：只由目标本人的 tick 推进
		Iterator<Map.Entry<UUID, CatState>> it2 = CATS.entrySet().iterator();
		while (it2.hasNext()) {
			Map.Entry<UUID, CatState> e = it2.next();
			CatState st = e.getValue();
			if (!st.target.getUuid().equals(player.getUuid())) continue;
			Entity cat = world.getEntity(e.getKey());
			if (!(cat instanceof GhostCatEntity oc)) {
				it2.remove();
				continue;
			}
			tickGhostCat(world, st, oc);
		}
	}

	/** 目标断线/出梦清理：清其名下幽灵登记。 */
	public static void clearFor(UUID targetUuid) {
		CREEPERS.values().removeIf(targetUuid::equals);
		CATS.values().removeIf(cs -> cs.target.getUuid().equals(targetUuid));
	}

	/** 玩家掉线：清状态（实体由 tick 查不到时自然移除；clearAll 兜底扫 tag）。 */
	public static void onDisconnect(UUID playerUuid) {
		clearFor(playerUuid);
	}

	/** 服务器启动/数据包重载：清空全部状态并击杀残留的幽灵实体。 */
	public static void clearAll(MinecraftServer server) {
		for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
			for (Entity e : world.iterateEntities()) {
				if (e instanceof CreeperEntity && e.getCommandTags().contains("ssca_ghost_creeper")) {
					e.discard();
				}
				if (e instanceof GhostCatEntity && e.getCommandTags().contains("ssca_ghost_cat")) {
					e.discard();
				}
			}
		}
		CREEPERS.clear();
		CATS.clear();
	}

	/** 服务端事件注册（由 SscAddonServerEvents 调用）：目标攻击幽灵苦力怕的判定。 */
	public static void registerEvents() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			// AttackEntityCallback 注入 PlayerEntity.attack，服务端触发（ServerPlayerEntityMixin 已核实）
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
			if (isGhostCreeperEntity(entity)) {
				onGhostCreeperAttacked(sp, (CreeperEntity) entity);
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
	}
}
