package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostArrayEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 寒棘狐「冰刺」蓄力 / 发射 / 环绕 / 计时 / 替换 / 净化 服务端状态机。
 *
 * <p>长按主技能键蓄力：每 {@value #CHARGE_INTERVAL} tick（1.2 秒）凝聚一根冰锥，最多 {@value #MAX_THORNS} 根，
 * 冰锥在玩家背部竖直面上按 150° 扇形（上→左→右→左上→右上）开屏分布；朝向与身体朝向平行，尖朝头部所指方向（最多偏 30°）。
 * 满 5 根后继续蓄力则替换存在时间最久（剩余最短）的一根。点按主技能键按生成顺序（最旧优先）发射一根，
 * 内置 {@value #FIRE_CD} tick（0.2 秒）发射冷却（走 SP_PRIMARY_CD）。主人被 SP 悦灵净化时全部冰锥碎裂。</p>
 *
 * <p>每根冰锥各自独立 60 秒存在时间由 {@link FrostThornEntity} 自行计时；本管理器只负责环绕定位、蓄力节奏与发射。
 * 全部判定在服务端，冰锥实体走 EntityTracker 天然多人同步。</p>
 */
public final class FrostSpikeManager {

	private static final int CHARGE_INTERVAL = 24; // 1.2 秒凝聚一根
	private static final int MAX_THORNS = 5;
	private static final int FIRE_CD = 4;          // 0.2 秒内置发射冷却
	// ===== 凝棘（次技能）蓄力 =====
	private static final int SECONDARY_CONSUME_INTERVAL = 20;   // 每 1 秒消耗一个环绕冰锥强化
	private static final double SECONDARY_SLOW_AMOUNT = -0.90;  // 蓄力时移速降为 10%（MULTIPLY_TOTAL -0.9）
	private static final UUID SECONDARY_SLOW_UUID = UUID.fromString("f2a7c3d1-8b64-4e29-9a11-6c3d0f7e51ab");
	// 环绕几何统一在 FrostThornEntity.hoverTarget/hoverYaw（服务端权威设置；客户端每 tick 按本地玩家自算贴合，平滑不卡顿）

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private static final class State {
		boolean charging = false;
		int chargeTicks = 0;
		// 凝棘次技能蓄力态
		boolean secondaryCharging = false;
		int secondaryTicks = 0;
		int secondaryLevel = 0;
		FrostArrayEntity arrayEntity = null; // 蓄力法阵（视觉）
		final FrostThornEntity[] slots = new FrostThornEntity[MAX_THORNS];
	}

	private FrostSpikeManager() {}

	/** 客户端「长按确认」→ 开始蓄力。次技能（凝棘）蓄力期间禁止凝聚新冰锥。 */
	public static void startCharge(ServerPlayerEntity player) {
		if (!isFrostspine(player)) return;
		State s = STATES.computeIfAbsent(player.getUuid(), k -> new State());
		if (s.secondaryCharging) return; // 凝棘蓄力中：主技能禁用
		if (s.charging) return;          // 已在蓄力：不重复广播
		s.charging = true;
		// 事件级状态包：客户端（含客机）本地自算下一个冰锥位置的汇聚粒子
		net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.syncFrostSpikeChargeVisual(player, 1);
	}

	/** 客户端「松开」→ 停止蓄力（保留已凝聚的冰锥）。 */
	public static void stopCharge(ServerPlayerEntity player) {
		State s = STATES.get(player.getUuid());
		if (s != null && s.charging) {
			s.charging = false; s.chargeTicks = 0;
			// 事件级状态包：通知客户端停本地汇聚粒子
			net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.syncFrostSpikeChargeVisual(player, 0);
		}
	}

	/** 客户端「点按」→ 按生成顺序（最旧优先）发射一根冰锥。 */
	public static void fire(ServerPlayerEntity player) {
		if (!isFrostspine(player)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // 0.2s 内置发射冷却
		State s = STATES.get(player.getUuid());
		if (s == null) return;
		cleanupDead(s);
		int idx = oldestSlot(s);
		if (idx < 0) return;
		FrostThornEntity thorn = s.slots[idx];
		s.slots[idx] = null;
		// 发射前把服务端位置刷新到当前环绕位（HOVER 态服务端不逐 tick 移动，防与客户端自算打架）
		thorn.setHoverTransform(FrostThornEntity.hoverTarget(player, idx), FrostThornEntity.hoverYaw(player), FrostThornEntity.hoverPitch(player));
		// 从冰锥当前(环绕)位置发射；飞行中自然靠拢到发射瞬间的准星射线，汇入后沿射线继续飞
		thorn.launch(player.getEyePos(), player.getRotationVector());
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.9f, 1.4f);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, FIRE_CD);
	}

/**
	 * 凝棘（次技能）蓄力锥一舞台中心：头顶正上方环绕位（slot 0）。
	 *
	 * <p>法阵渲染、蓄力粒子汇聚中心、冰锥发射点统一用此处——三处严格同点，避免「合成位置与实际出生位置不一致」。
	 * 冰锥初速 = 头顶指向准星远点的直线方向（出生即纯直线、尖朝速度方向），无弯曲汇入——弯曲拐大弯会被看成「旋转着飞」。</p>
	 */
	public static Vec3d secondaryFocus(ServerPlayerEntity player) {
		return FrostThornEntity.hoverTarget(player, 0);
	}

	/** 凝棘（次技能）开始蓄力：需身上有环绕冰锥才生效；进入后禁用主技能凝聚、移速降 10%。 */
	public static void startSecondaryCharge(ServerPlayerEntity player) {
		if (!isFrostspine(player)) return;
		State s = STATES.get(player.getUuid());
		if (s == null) return;
		cleanupDead(s);
		if (countThorns(s) == 0) return; // 无环绕冰锥：按次键无效
		s.charging = false; s.chargeTicks = 0; // 清主技能蓄力（客户端粒子随次技能 mode 2 接管）
		s.secondaryCharging = true;
		s.secondaryTicks = 0;
		s.secondaryLevel = 0;
		applyChargeSlow(player);
		// 蓄力法阵（纯视觉，跟随施法者头顶；中央冰锥随蓄力等级放大）
		if (player.getWorld() instanceof ServerWorld sw) {
			FrostArrayEntity array = new FrostArrayEntity(sw, player);
			sw.spawnEntity(array);
			s.arrayEntity = array;
			// 潮涌核心蓄力启动音
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.0f);
			// 事件级状态包：客户端（含客机）本地自算头顶汇聚粒子（替代持续粒子波广播）
			net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.syncFrostSpikeChargeVisual(player, 2);
		}
	}

	/**
	 * 凝棘（次技能）松开：发射一根强化冰锥（伤害/大小/速度按已消耗冰锥数强化）。
	 * 一个冰锥都没消耗（level==0）则取消不发射（环绕冰锥保留）。
	 */
	public static void releaseSecondary(ServerPlayerEntity player) {
		State s = STATES.get(player.getUuid());
		if (s == null) return;
		if (!s.secondaryCharging) return;
		int level = s.secondaryLevel;
		endSecondaryCharge(player, s); // 退出蓄力 + 恢复移速
		if (level <= 0) return;         // 没消耗任何冰锥：取消发射
		if (!(player.getWorld() instanceof ServerWorld sw)) return;
		FrostThornEntity thorn = new FrostThornEntity(sw, player);
		Vec3d dir = player.getRotationVector();
		// 从头顶法阵中心（与粒子/法阵严格同点）发射；初速 = 头顶指向准星远点的直线方向（纯直线无弯曲）
		Vec3d origin = secondaryFocus(player);
		Vec3d aimPoint = player.getEyePos().add(dir.multiply(16.0));
		Vec3d flyDir = aimPoint.subtract(origin).normalize();
		// 先 launchEnhanced 再 spawnEntity：生成包才会携带正确位置/朝向/FLY 态/全精度速度
		// （若先 spawn，生成包捕获的是构造器位置+朝向 0°，客户端起步姿态错误 → 起飞瞬间甩转）
		thorn.launchEnhanced(origin, flyDir, level, player.getEyePos(), dir);
		sw.spawnEntity(thorn);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 1.0f, 1.0f);
	}

	/** 蓄力期移速降为 10%（先移除同 UUID 旧修改器防叠加）。 */
	private static void applyChargeSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SECONDARY_SLOW_UUID);
			speed.addTemporaryModifier(new EntityAttributeModifier(
					SECONDARY_SLOW_UUID, "Frost Forge Charge Slow", SECONDARY_SLOW_AMOUNT,
					EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}

	/** 结束凝棘蓄力：移除减速 + 清蓄力态。所有结束路径统一走此方法，防减速泄漏。 */
	private static void endSecondaryCharge(ServerPlayerEntity player, State s) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(SECONDARY_SLOW_UUID);
		if (s != null) {
			s.secondaryCharging = false; s.secondaryTicks = 0; s.secondaryLevel = 0;
			if (s.arrayEntity != null) { s.arrayEntity.discard(); s.arrayEntity = null; }
		}
		// 事件级状态包：通知客户端停本地汇聚粒子（含客机）
		net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.syncFrostSpikeChargeVisual(player, 0);
	}

	/** 每服务端 tick 对每个在线玩家调用（挂 SscAddonServerEvents 世界 tick）。 */
	public static void tick(ServerPlayerEntity player) {
		State s = STATES.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !isFrostspine(player)) { endSecondaryCharge(player, s); clearAll(s); STATES.remove(player.getUuid()); return; }
		// 净化：主人被 SP 悦灵净化 → 全部环绕冰锥碎裂（飞行中的由实体自身 tick 处理）
		if (player.hasStatusEffect(SscAddon.PURIFIED)) { endSecondaryCharge(player, s); clearAll(s);
			if (s.charging) net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.syncFrostSpikeChargeVisual(player, 0);
			s.charging = false; s.chargeTicks = 0; return; }
		cleanupDead(s);
		// 凝棘次技能蓄力：每 1 秒消耗一个环绕冰锥强化（无冰锥可消耗则停在当前强化等待松开）
		if (s.secondaryCharging) {
			s.secondaryTicks++;
			// 潮涌核心蓄力氛围音（循环嗡鸣，充能感；压低避免盖住每秒的紫水晶消耗钟声）
			if (player.age % 25 == 0 && player.getWorld() instanceof ServerWorld amb) {
				amb.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 0.35f, 1.0f);
			}
			// 持续汇聚已移到客户端本地自算（事件级状态包驱动，网络包归零）

				if (s.secondaryTicks >= SECONDARY_CONSUME_INTERVAL) {
					s.secondaryTicks = 0;
				int idx = consumeSlot(s); // 固定消耗顺序：上→左→右→左上→右上（slot 0→4）
					if (idx >= 0) {
						if (player.getWorld() instanceof ServerWorld sw) {
							// 消耗成形：头顶法阵中心（大冰锥真实成形点）一波密集汇聚（与持续粒子同位）
							spawnInwardIceParticles(sw, secondaryFocus(player), true);
							// 被吸走的冰锥就地一点碎冰反馈（吸走感）
							Vec3d taken = FrostThornEntity.hoverTarget(player, idx);
							sw.spawnParticles(ParticleTypes.ITEM_SNOWBALL, taken.x, taken.y, taken.z, 5, 0.1, 0.1, 0.1, 0.03);
						}
						if (s.slots[idx] != null) s.slots[idx].discard();
					s.slots[idx] = null;
					s.secondaryLevel = Math.min(MAX_THORNS, s.secondaryLevel + 1);
					float pitch = 0.8f + s.secondaryLevel * 0.22f; // 越来越高的紫水晶音
					if (player.getWorld() instanceof ServerWorld sw) {
							// 双音：经验球叮声本身响亮（主音）+ 紫水晶 CHIME 作水晶泛音。
							// CHIME 原版响度极低且 volume>1 只扩可闻距离不增源响度，单提音量无效
							sw.playSound(null, player.getX(), player.getY(), player.getZ(),
									SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.2f, pitch);
							sw.playSound(null, player.getX(), player.getY(), player.getZ(),
									SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8f, pitch);
					}
				}
			}
			// 同步法阵中央冰锥大小（随蓄力等级放大）
			if (s.arrayEntity != null && !s.arrayEntity.isRemoved()) s.arrayEntity.setLevel(s.secondaryLevel);
		} else if (s.charging) {
			s.chargeTicks++;
			// 蓄力时持续汇聚：已移到客户端本地自算（事件级状态包驱动，网络包归零）
			if (s.chargeTicks >= CHARGE_INTERVAL) { s.chargeTicks = 0; spawnOrReplaceThorn(player, s); }
		}
		updateHoverPositions(player, s);
		if (isEmpty(s) && !s.charging && !s.secondaryCharging) STATES.remove(player.getUuid());
	}

	/** 形态切换 / 断线清理。 */
	public static void clear(ServerPlayerEntity player) {
		State s = STATES.remove(player.getUuid());
		if (s != null) { endSecondaryCharge(player, s); clearAll(s); }
	}

	/**
	 * 玩家退出：环绕冰锥随玩家消失——各槽存在时间写入持久化存档，实体全部清除；
	 * 飞行中的冰锥直接清空（不持久化）。重进后由 onJoin 按存档重建。
	 */
	public static void onDisconnect(ServerPlayerEntity player) {
		State s = STATES.remove(player.getUuid());
		if (s != null) endSecondaryCharge(player, s);
		FrostSpikeState state = FrostSpikeState.get(player.getServer());
		int[] ticks = new int[MAX_THORNS];
		java.util.Arrays.fill(ticks, -1);
		if (s != null) {
			for (int i = 0; i < MAX_THORNS; i++) {
				FrostThornEntity t = s.slots[i];
				if (t != null && !t.isRemoved()) {
					ticks[i] = t.getHoverTicks();
					t.discard();           // 环绕冰锥随玩家消失（数据已存档）
				}
			}
		}
		state.thorns.put(player.getUuid(), ticks);
		state.markDirty();
		// 飞行中的冰锥：同主人的全部清空（速度/起点不持久化，留着会冻在空中）
		if (player.getWorld() instanceof ServerWorld sw) {
			for (FrostThornEntity e : sw.getEntitiesByClass(FrostThornEntity.class,
					player.getBoundingBox().expand(256),
					ex -> ex.getState() == FrostThornEntity.STATE_FLY && player.getUuid().equals(ex.getOwnerUuid().orElse(null)))) {
				e.discard();
			}
		}
	}

	/** 玩家重进：按退出前存档重建环绕冰锥（槽位与存在时间延续）。 */
	public static void onJoin(ServerPlayerEntity player) {
		FrostSpikeState state = FrostSpikeState.get(player.getServer());
		int[] ticks = state.thorns.remove(player.getUuid());
		if (ticks == null) return;
		state.markDirty();
		if (!isFrostspine(player)) return;
		if (!(player.getWorld() instanceof ServerWorld sw)) return;
		State s = STATES.computeIfAbsent(player.getUuid(), k -> new State());
		for (int i = 0; i < MAX_THORNS; i++) {
			if (ticks[i] < 0) continue;
			FrostThornEntity thorn = new FrostThornEntity(sw, player);
			thorn.setSlot(i);
			thorn.restoreHoverTicks(ticks[i]);
			thorn.setHoverTransform(FrostThornEntity.hoverTarget(player, i), FrostThornEntity.hoverYaw(player), FrostThornEntity.hoverPitch(player));
			sw.spawnEntity(thorn);
			s.slots[i] = thorn;
		}
		if (isEmpty(s)) STATES.remove(player.getUuid());
	}

	/**
	 * 重进游戏自认领（幂等，每服务端 tick 由孤儿冰锥自行调用）：
	 * NBT 恢复的环绕冰锥通过 OWNER_UUID 找回主人，重新挂进 slots 并恢复 owner 引用；
	 * 主人离线则等待上线或存在时间到期自融；主人已非寒棘狐则直接碎裂（等同 clearAll）。
	 */
	public static void adopt(FrostThornEntity thorn) {
		if (!(thorn.getWorld() instanceof ServerWorld sw)) return;
		var oid = thorn.getOwnerUuid();
		if (oid.isEmpty()) { thorn.discard(); return; } // 无主环绕冰锥无意义
		ServerPlayerEntity player = sw.getServer().getPlayerManager().getPlayer(oid.get());
		if (player == null) return;                      // 主人离线：先留着
		if (player.isDead() || !isFrostspine(player)) { thorn.discard(); return; }
		thorn.setOwner(player);                           // 恢复 ProjectileEntity owner 引用（净化判定/伤害归属用）
		State s = STATES.computeIfAbsent(player.getUuid(), k -> new State());
		cleanupDead(s);
		int idx = thorn.getSlot();
		if (idx >= 0 && idx < MAX_THORNS && s.slots[idx] == thorn) return; // 已认领
		if (idx < 0 || idx >= MAX_THORNS || s.slots[idx] != null) {
			idx = firstEmptySlot(s);
			if (idx < 0) { thorn.discard(); return; }     // 超出 5 根（异常）→ 丢弃
			thorn.setSlot(idx);
		}
		s.slots[idx] = thorn;
	}

	private static void spawnOrReplaceThorn(ServerPlayerEntity player, State s) {
		ServerWorld sw = (ServerWorld) player.getWorld();
		int idx = firstEmptySlot(s);
		if (idx < 0) { // 已满 5 根 → 替换存在时间最久（剩余最短）的
			idx = oldestSlot(s);
			if (idx < 0) return;
			if (s.slots[idx] != null) s.slots[idx].discard();
		}
		FrostThornEntity thorn = new FrostThornEntity(sw, player);
		thorn.setSlot(idx);
		// 生成时直接落在环绕位（否则生成包首帧在眼睛处闪现一帧）
		thorn.setHoverTransform(FrostThornEntity.hoverTarget(player, idx), FrostThornEntity.hoverYaw(player), FrostThornEntity.hoverPitch(player));
		sw.spawnEntity(thorn);
		s.slots[idx] = thorn;
		// 成形汇聚粒子已移到客户端（冰锥实体自身前 20t 在自己当前位置发）：中心与渲染位置完全重合，
		// 服务端预测点与客户端插值位置的错位彻底消失
		int count = countThorns(s);
		float pitch = 0.9f + count * 0.12f; // 紫水晶凝聚音，pitch 随冰锥数递增
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_AMETHYST_BLOCK_STEP, SoundCategory.PLAYERS, 1.0f, pitch);
	}

	/**
	 * 在冰锥中点周围**半径 1 格**的球形范围内生成向内汇聚的冰晶粒子（自定义「汇聚冰晶」粒子）。
	 *
	 * <p>原版粒子物理模型（WhiteAsh/Snowflake 带重力、DUST 随机寿命）都无法精确「抵达中心即消失」，
	 * 故用自定义粒子 {@link net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon#INWARD_ICE_PARTICLE}：
	 * 无重力、无摩擦、匀速直线、固定寿命 {@code 20} tick。初速 = 方向 × (1格/20t) = 0.05/tick——
	 * 粒子恰好 20 tick 走完 1 格抵达中心，寿命尽 = 抵达 = 移除（末 5t 淡出）。
	 * 生成即此粒子的唯一职责，速度精确指向中心，视觉为清晰的向心汇聚流。</p>
	 *
	 * @param burst 成形时刻的密集波（数量更多），平时每 4 tick 一波常规汇聚
	 */
	private static void spawnInwardIceParticles(ServerWorld sw, Vec3d center, boolean burst) {
		int count = burst ? 16 : 12; // 数量已按网络压力调优（count=0 模式每颗粒子一个 S2C 包）
		for (int i = 0; i < count; i++) {
			// 球面均匀随机方向（u∈[-1,1] 均匀 + θ∈[0,2π) 均匀 → 球面均匀）
			double u = sw.random.nextDouble() * 2 - 1;
			double theta = sw.random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(1 - u * u);
			double dx = r * Math.cos(theta), dy = u, dz = r * Math.sin(theta);
			// 初速 = -(方向) × (1格 / 20t)：count=0 模式下 dx/dy/dz 即精确初速（粒子端零物理匀速直线）
			double speed = 1.0 / 20.0;
			sw.spawnParticles(SscAddon.INWARD_ICE_PARTICLE, center.x + dx, center.y + dy, center.z + dz,
					0, -dx * speed, -dy * speed, -dz * speed, 1.0);
		}
	}

	private static void updateHoverPositions(ServerPlayerEntity player, State s) {
		// HOVER 态服务端不逐 tick 移动（无移动→无移动包，客户端独占每 tick 自算贴合位置，避免与延迟网络包打架致鬼畜）。
		// 仅当服务端残留位置离玩家超过 40 格（超出常规移动范围、追踪快失效且远不可见）才校正一次，维持实体在追踪范围内。
		for (int i = 0; i < MAX_THORNS; i++) {
			FrostThornEntity t = s.slots[i];
			if (t == null || t.isRemoved() || !t.isHover()) continue;
			if (t.squaredDistanceTo(player) > 40.0 * 40.0) {
				t.setHoverTransform(FrostThornEntity.hoverTarget(player, i), FrostThornEntity.hoverYaw(player), FrostThornEntity.hoverPitch(player));
			}
		}
	}

	private static void cleanupDead(State s) {
		for (int i = 0; i < MAX_THORNS; i++) {
			FrostThornEntity t = s.slots[i];
			if (t != null && (t.isRemoved() || !t.isHover())) s.slots[i] = null;
		}
	}

	private static void clearAll(State s) {
		for (int i = 0; i < MAX_THORNS; i++) {
			if (s.slots[i] != null && !s.slots[i].isRemoved()) s.slots[i].discard();
			s.slots[i] = null;
		}
	}

	private static int firstEmptySlot(State s) {
		for (int i = 0; i < MAX_THORNS; i++) if (s.slots[i] == null) return i;
		return -1;
	}

	/** 存在时间最久（hoverTicks 最大 = 最旧 = 剩余最短）的 slot。 */
	private static int oldestSlot(State s) {
		int idx = -1, max = -1;
		for (int i = 0; i < MAX_THORNS; i++) {
			FrostThornEntity t = s.slots[i];
			if (t != null && t.getHoverTicks() > max) { max = t.getHoverTicks(); idx = i; }
		}
		return idx;
	}

	/** 凝棘消耗顺序：优先头顶正上方（slot 0）；其被耗后，剩余按剩余存在时间排列——最快消失的（hoverTicks 最大）先被消耗。 */
	private static int consumeSlot(State s) {
		if (s.slots[0] != null) return 0;
		return oldestSlot(s);
	}

	private static int countThorns(State s) {
		int c = 0;
		for (int i = 0; i < MAX_THORNS; i++) if (s.slots[i] != null) c++;
		return c;
	}

	private static boolean isEmpty(State s) {
		return countThorns(s) == 0;
	}

	private static boolean isFrostspine(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_FROSTSPINE);
	}
}
