package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.entity.SpiderSwingBullet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 月织蛛「蛛丝荡漾」次技能（sp_secondary）—— <b>服务端权威层</b>。
 *
 * <p>物理放客户端（{@code SwingPhysicsMixin}），本类只做：发射蛛丝飞弹、mana 权威、断丝检测、
 * TETHER 拖拽目标生物、状态广播。发射改为投射物 {@link SpiderSwingBullet}（抛物线飞行，命中方块→
 * 摆荡 / 命中生物→拖拽 tether / miss→落地消失）。
 */
public final class SpiderMoonWeaverSwingManager {

	// ==== 发射 / 飞行 ====
	public static final double FIRE_MAX_REACH = 24.0;
	private static final float BULLET_SPEED = 1.7f;
	public static final double MANA_PER_BLOCK = 2.0;
	public static final double TETHER_HIT_MANA_COST = 8.0; // 勾中生物瞬间额外扣的 mana（不足扣到 0）

	// ==== 绳长 / 断丝 ====
	public static final double MAX_ROPE_REACH = 32.0;
	public static final double TETHER_MAX_LEN = 16.0; // tether 拴生物最大间距（不分敌友，不可延长蛛丝）
	public static final double MIN_ROPE_LEN = 1.5;
	public static final double REEL_SPEED = 0.16;
	private static final double BREAK_OVERSTRETCH = MAX_ROPE_REACH + 3.0;
	private static final double OBSCURE_BREAK_BLOCKS = 1.0;

	// ==== 状态 ====
	public static final int STATE_IDLE = 0;
	public static final int STATE_SWINGING = 2;
	public static final int STATE_TETHER = 3; // 连接生物拖拽

	private static final class SwingState {
		int state = STATE_IDLE;
		Vec3d anchor = Vec3d.ZERO;    // SWINGING 销点
		int tetherEntityId = -1;      // TETHER 目标实体 id
		double ropeLen = 0.0;
		boolean canExtend = true;
		int broadcastTick = 0;
		double lastPX = Double.NaN, lastPY = 0, lastPZ = 0; // 卡死检测：上 tick 玩家位置
		int stuckTicks = 0;          // 被拉但连续不动的 tick 数
	}

	private static final Map<UUID, SwingState> STATES = new ConcurrentHashMap<>();
	/** 蛛丝飞弹在飞的玩家 → 弹实体（防连发 + 支持飞行中再按键取消；命中/miss 时移除）。 */
	private static final Map<UUID, SpiderSwingBullet> BULLET_IN_FLIGHT = new ConcurrentHashMap<>();

	private SpiderMoonWeaverSwingManager() {}

	private static boolean isSpiderMoonWeaver(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER);
	}

	private static ManaComponent mana(ServerPlayerEntity player) {
		return RegManaComponent.MANA.get(player);
	}

	private static SwingState state(ServerPlayerEntity player) {
		return STATES.computeIfAbsent(player.getUuid(), k -> new SwingState());
	}

	private static void broadcastState(ServerPlayerEntity player, SwingState s) {
		SscAddonNetworking.syncSwingState(player, s.state != STATE_IDLE,
				s.anchor.x, s.anchor.y, s.anchor.z, s.ropeLen, s.state, s.canExtend, s.tetherEntityId);
	}

	public static boolean isSwinging(ServerPlayerEntity player) {
		SwingState s = STATES.get(player.getUuid());
		return s != null && s.state != STATE_IDLE;
	}

	/**
	 * 该玩家是否正拴住（TETHER）指定实体。用于足丝拴生物的伤害增减判定（双向通用）。
	 * 参数用 {@link Entity} 以免额外 import；player/target 任一为 null 或非 TETHER 均返回 false。
	 */
	public static boolean isTethering(Entity player, Entity target) {
		if (player == null || target == null) return false;
		SwingState s = STATES.get(player.getUuid());
		return s != null && s.state == STATE_TETHER && s.tetherEntityId == target.getId();
	}

	/**
	 * 反查正拴住（TETHER）指定实体的拴主玩家，无则返回 null。用于被拴目标受伤时找拴主（敌我判定/伤害分担）。
	 */
	public static ServerPlayerEntity getTetheringPlayer(Entity target) {
		if (target == null || target.getServer() == null) return null;
		for (Map.Entry<UUID, SwingState> e : STATES.entrySet()) {
			SwingState s = e.getValue();
			if (s.state == STATE_TETHER && s.tetherEntityId == target.getId()) {
				ServerPlayerEntity sp = target.getServer().getPlayerManager().getPlayer(e.getKey());
				if (sp != null) return sp;
			}
		}
		return null;
	}

	private static Vec3d torso(ServerPlayerEntity player) {
		return player.getPos().add(0, 1.0, 0);
	}

	private static Vec3d entityCenter(LivingEntity e) {
		return e.getPos().add(0, e.getHeight() * 0.5, 0);
	}

	// ==== 次键：IDLE→发弹 / 否则→断丝 ====
	public static void onSecondaryPress(ServerPlayerEntity player) {
		if (!isSpiderMoonWeaver(player)) return;
		SwingState s = state(player);
		if (s.state == STATE_IDLE) {
			// 飞行中再按键 → 取消飞弹（→ remove → onBulletMiss → 5 秒 CD）
			SpiderSwingBullet flying = BULLET_IN_FLIGHT.get(player.getUuid());
			if (flying != null && flying.isAlive()) {
				flying.discard();
				return;
			}
			shootBullet(player);
		} else {
			breakWeb(player, s, true);
		}
	}

	/** 发射蛛丝飞弹（抛物线投射物，碰撞后回调进入摆荡 / tether）。 */
	private static void shootBullet(ServerPlayerEntity player) {
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return;
		if (BULLET_IN_FLIGHT.containsKey(player.getUuid())) return;
		if (mana(player).getMana() < 1.0) {
			player.sendMessage(Text.translatable("message.my_addon.spider_moon_weaver.swing.no_mana"), true);
			return;
		}
		SpiderSwingBullet bullet = new SpiderSwingBullet(player);
		bullet.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, BULLET_SPEED, 0.0f);
		player.getWorld().spawnEntity(bullet);
		BULLET_IN_FLIGHT.put(player.getUuid(), bullet);
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 1.3f);
	}

	// ==== 飞弹碰撞回调（由 SpiderSwingBullet 调用） ====

	/** 飞弹命中方块 → 钩住进入摆荡。 */
	public static void onBulletHitBlock(ServerPlayerEntity player, Vec3d anchor) {
		BULLET_IN_FLIGHT.remove(player.getUuid());
		if (!isSpiderMoonWeaver(player)) return;
		SwingState s = state(player);
		s.state = STATE_SWINGING;
		s.anchor = anchor;
		s.tetherEntityId = -1;
		s.ropeLen = MathHelper.clamp(torso(player).distanceTo(anchor), MIN_ROPE_LEN, MAX_ROPE_REACH);
		s.canExtend = true;
		broadcastState(player, s);
	}

	/** 拴住目标的高光描边色：白名单友军→绿，敌人→蓝（仅施法者可见）。 */
	private static int tetherHighlightColor(ServerPlayerEntity player, LivingEntity target) {
		return WhitelistUtils.isProtected(player, target) ? 0x3AF03A : 0x3AA0FF;
	}

	/** 飞弹命中生物 → 连接进入 tether 拖拽。 */
	public static void onBulletHitEntity(ServerPlayerEntity player, LivingEntity target) {
		BULLET_IN_FLIGHT.remove(player.getUuid());
		if (!isSpiderMoonWeaver(player)) return;
		SwingState s = state(player);
		s.state = STATE_TETHER;
		s.tetherEntityId = target.getId();
		s.anchor = Vec3d.ZERO;
		s.ropeLen = MathHelper.clamp(torso(player).distanceTo(entityCenter(target)), MIN_ROPE_LEN, TETHER_MAX_LEN);
		s.canExtend = true;
		mana(player).consumeMana(TETHER_HIT_MANA_COST); // 勾中生物瞬间额外扣 8 点 mana（自动 clamp 到 0 不会负、自动同步客户端 mana 条）
		SscAddonNetworking.sendWebHighlight(player, target.getId(), 40, tetherHighlightColor(player, target)); // 仅施法者可见高光（友军绿/敌人蓝）
		broadcastState(player, s);
	}

	/** 飞弹 miss 落地消失 / 被移除 → 5 秒 CD（幂等：仅仍在飞未命中时生效）。 */
	public static void onBulletMiss(ServerPlayerEntity player) {
		if (BULLET_IN_FLIGHT.remove(player.getUuid()) != null) {
			PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, 100);
		}
	}

	// ==== 断丝 ====
	private static void breakWeb(ServerPlayerEntity player, SwingState s, boolean giveCd) {
		boolean wasActive = s.state != STATE_IDLE;
		s.state = STATE_IDLE;
		s.anchor = Vec3d.ZERO;
		s.tetherEntityId = -1;
		s.ropeLen = 0.0;
		s.canExtend = true;
		s.stuckTicks = 0;
		s.lastPX = Double.NaN;
		if (wasActive) {
			ServerWorld sw = (ServerWorld) player.getWorld();
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.PLAYERS, 0.7f, 1.1f);
			if (giveCd) {
				PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, 100);
			}
		}
		broadcastState(player, s);
	}

	// ==== 每服务端 tick ====
	public static void tick(ServerPlayerEntity player) {
		SwingState s = STATES.get(player.getUuid());
		if (s == null || s.state == STATE_IDLE) return;
		if (player.isDead() || !isSpiderMoonWeaver(player)) {
			breakWeb(player, s, false);
			return;
		}
		if (s.state == STATE_SWINGING) {
			tickSwinging(player, s);
		} else if (s.state == STATE_TETHER) {
			tickTether(player, s);
		}
	}

	/** SWINGING：断丝检测 + 定期广播 + 销点粒子（物理在客户端）。 */
	private static void tickSwinging(ServerPlayerEntity player, SwingState s) {
		player.fallDistance = 0.0f; // 服务端清摔落距离防摆荡落地摔伤（不影响客户端 fallDistance 驱动的 FALL 动画）
		Vec3d torso = torso(player);
		double dist = torso.distanceTo(s.anchor);
		if (dist > BREAK_OVERSTRETCH) {
			breakWeb(player, s, true);
			return;
		}
		if (computeObscuration(player, torso, s.anchor) > OBSCURE_BREAK_BLOCKS) {
			breakWeb(player, s, true);
			return;
		}
		if (++s.broadcastTick >= 3) {
			s.broadcastTick = 0;
			broadcastState(player, s);
			((ServerWorld) player.getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
					s.anchor.x, s.anchor.y, s.anchor.z, 2, 0.12, 0.12, 0.12, 0.0);
		}
	}

	/** TETHER：拉目标生物向玩家（按抗性缩放，有阻力）+ 断丝检测 + 高光刷新 + 广播。 */
	private static void tickTether(ServerPlayerEntity player, SwingState s) {
		player.fallDistance = 0.0f; // 服务端清摔落距离防 tether 拖拽落地摔伤
		ServerWorld world = (ServerWorld) player.getWorld();
		Entity target = world.getEntityById(s.tetherEntityId);
		if (!(target instanceof LivingEntity living) || !living.isAlive()) {
			breakWeb(player, s, true);
			return;
		}
		Vec3d pPos = torso(player);
		Vec3d tPos = entityCenter(living);
		double dist = pPos.distanceTo(tPos);
		if (dist > BREAK_OVERSTRETCH) {
			breakWeb(player, s, true);
			return;
		}
		if (computeObscuration(player, pPos, tPos) > OBSCURE_BREAK_BLOCKS) {
			breakWeb(player, s, true);
			return;
		}
		double ropeLen = Math.max(s.ropeLen, MIN_ROPE_LEN);
		// 超出绳长 → 拉目标向玩家（趋向速度，防撞墙累积；按 1-抗性缩放，重型拉不动由客户端把玩家拉过去）
		if (dist > ropeLen) {
			double resist = knockbackResist(living);
			Vec3d dir = pPos.subtract(tPos).normalize();
			double over = dist - ropeLen;
			double desired = Math.min(over * 0.3, 0.55) * (1.0 - resist);
			if (desired > 0.001) {
				Vec3d tv = living.getVelocity();
				double toward = tv.dotProduct(dir);
				if (toward < desired) {
					tv = tv.add(dir.multiply(desired - toward));
					double sp = tv.length();
					if (sp > 0.55) tv = tv.multiply(0.55 / sp); // 硬上限防生物爆冲
					living.setVelocity(tv);
					living.velocityModified = true;
					living.velocityDirty = true;
				}
			}
		}
		// 高光刷新（仅施法者可见；友军绿/敌人蓝）
		SscAddonNetworking.sendWebHighlight(player, living.getId(), 20, tetherHighlightColor(player, living));
		// 卡死保底：被拉（dist>绳长）但连续不动 → 2 秒后自动断丝解脱（防永久卡死）
		if (dist > ropeLen + 0.5) {
			if (!Double.isNaN(s.lastPX)) {
				double dx = pPos.x - s.lastPX, dy = pPos.y - s.lastPY, dz = pPos.z - s.lastPZ;
				if (dx * dx + dy * dy + dz * dz < 0.0016) { // 移动 < 0.04 格
					if (++s.stuckTicks > 20) {
						breakWeb(player, s, true);
						return;
					}
				} else {
					s.stuckTicks = 0;
				}
			}
			s.lastPX = pPos.x;
			s.lastPY = pPos.y;
			s.lastPZ = pPos.z;
		} else {
			s.stuckTicks = 0;
			s.lastPX = Double.NaN;
		}
		if (++s.broadcastTick >= 2) {
			s.broadcastTick = 0;
			broadcastState(player, s);
		}
	}

	private static double knockbackResist(LivingEntity living) {
		EntityAttributeInstance inst = living.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		return inst != null ? MathHelper.clamp(inst.getValue(), 0.0, 1.0) : 0.0;
	}

	/**
	 * 客户端每 tick 上报绳长 + 收放意图（+1 收 / -1 放 / 0）。服务端信任客户端 ropeLen（转发 + 断丝参考），
	 * 放绳权威扣 mana，mana 不足则 canExtend=false 广播阻止继续放绳。SWINGING / TETHER 均适用。
	 */
	public static void onReelSync(ServerPlayerEntity player, double clientRopeLen, int reel) {
		SwingState s = STATES.get(player.getUuid());
		if (s == null || (s.state != STATE_SWINGING && s.state != STATE_TETHER)) return;
		// tether 拴生物：最大间距 16 且禁止放绳延长；swinging 荡漾仍可到 32 并放绳
		double maxLen = (s.state == STATE_TETHER) ? TETHER_MAX_LEN : MAX_ROPE_REACH;
		s.ropeLen = MathHelper.clamp(clientRopeLen, MIN_ROPE_LEN, maxLen);
		if (s.state == STATE_TETHER) reel = 0; // tether 不能延长蛛丝，忽略放绳意图
		boolean prev = s.canExtend;
		if (reel < 0) {
			double cost = REEL_SPEED / MANA_PER_BLOCK;
			ManaComponent m = mana(player);
			if (m.getMana() >= cost) {
				m.consumeMana(cost);
				s.canExtend = true;
			} else {
				s.canExtend = false;
			}
		} else {
			s.canExtend = true;
		}
		if (s.canExtend != prev) {
			broadcastState(player, s);
		}
	}

	private static double computeObscuration(ServerPlayerEntity player, Vec3d from, Vec3d to) {
		double totalDist = from.distanceTo(to);
		if (totalDist < 2.0) return 0.0;
		int samples = MathHelper.ceil(totalDist * 2.0);
		double obscured = 0.0;
		Vec3d dir = to.subtract(from).normalize();
		double skip = 0.6 / totalDist;
		for (int i = 0; i < samples; i++) {
			double t = (i + 0.5) / samples;
			if (t < skip || t > 1.0 - skip) continue;
			Vec3d p = from.add(dir.multiply(totalDist * t));
			BlockPos bp = BlockPos.ofFloored(p);
			if (player.getWorld().getBlockState(bp).isSolidBlock(player.getWorld(), bp)) {
				obscured += 0.5;
			}
		}
		return obscured;
	}

	public static void onDisconnect(UUID uuid) {
		STATES.remove(uuid);
		BULLET_IN_FLIGHT.remove(uuid);
	}
}
