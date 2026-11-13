package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.evolution.AxolotlTree;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionTalents;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.DoubleTapTrigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「水流冲刺」（water_spurt 节点）—— 水陆统一双击真正疾跑键。
 *
 * <p>350ms 内按下、松开、再次按下疾跑键触发；长按、双击 W 和自动疾跑不触发。</p>
 * <p>水下免费；陆地消耗湿润度。水陆仍分别使用原有 5 秒冷却和 HUD 资源，
 * 已解锁 water_spurt 节点才生效。收包时识别按键边沿，避免同 tick 多个包被合并而漏判。</p>
 */
public final class AxolotlWaterSpurtHandler {

	/** 陆地冲刺的服务端扣费和客户端资源门槛共用同一数值。 */
	public static int landAirCostForHud(net.minecraft.entity.player.PlayerEntity player) {
		return net.jackcooper.shapeShifterCurseAddon.compat.UpgradeAxolotlPerks.landDashAirCost(player, BAL.i("land_moisture_cost", LAND_MOISTURE_COST));
	}

	// 以下均为默认值；运行时从 balance 快照读取（abilities.axolotl_water_spurt）
	private static final int CD_TICKS = 100;   // 5 秒
	private static final Identifier WATER_HUD = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_water");
	private static final Identifier LAND_HUD = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_land");
	private static final double BURST = 1.6;    // 前冲力度（水陆一致）
	/** 陆地冲刺消耗的湿润度（air 值；参照 WaterSpearLeapManager 的 18=6%，冲刺更轻，取 12≈4%）。水下冲刺同 SSC 免费。 */
	private static final int LAND_MOISTURE_COST = 12;

	/** balance 快照读取（快照未初始化回退默认常量） */
	private static final BalanceReader BAL = new BalanceReader("abilities.axolotl_water_spurt");

	public static final int DOUBLE_TAP_WINDOW_MILLIS = 350;
	private static final Map<UUID, DoubleTapTrigger> SPRINT_INPUT = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> WATER_CD = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> LAND_CD = new ConcurrentHashMap<>();

	/** 客户端发「疾跑键按住状态」包时调用（仅进化美西蟠本机会发；区分真正疾跑键与双击 W/游泳自动疾跑）。 */
	public static void setClientSprintHeld(ServerPlayerEntity player, boolean held) {
		UUID id = player.getUuid();
		if (!FormUtils.isUpgradeAxolotl(player)
				|| !EvolutionTalents.isActive(player, AxolotlTree.NODE_WATER_SPURT)) {
			SPRINT_INPUT.remove(id);
			return;
		}
		if (SPRINT_INPUT.computeIfAbsent(id, key -> new DoubleTapTrigger(DOUBLE_TAP_WINDOW_MILLIS))
				.update(held, System.nanoTime() / 1_000_000L)) {
			tryDash(player);
		}
	}

	private AxolotlWaterSpurtHandler() {
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		int wcd = WATER_CD.getOrDefault(id, 0);
		if (wcd > 0) WATER_CD.put(id, wcd - 1);
		int lcd = LAND_CD.getOrDefault(id, 0);
		if (lcd > 0) LAND_CD.put(id, lcd - 1);

		if (!FormUtils.isUpgradeAxolotl(player)) {
			SPRINT_INPUT.remove(id);
			return;
		}

		// HUD 资源仅在 CD 活跃期同步（空闲稳态 wcd==lcd==0 零资源读写）：CD 归零后补发一次 0，
		// 之后不再触碰资源；触发冲刺时在分支内直接写 HUD（值必变，无需比对）。
		// 归零补发若因极端时序被应用层丢弃（换维度/传送瞬间实体未追踪），空闲期每 100t 低频对账
		// 一次自愈（服务端本地读一次比对，值不符才重发——正常态零额外包，异常态最多残留 5s）。
		boolean waterCooling = wcd > 0;
		boolean landCooling = lcd > 0;
		boolean idleAudit = !waterCooling && !landCooling && wcd != 1 && lcd != 1 && player.age % 100 == 0;
		if (waterCooling || wcd == 1 || (idleAudit
				&& PowerUtils.getResourceValue(player, WATER_HUD) != 0)) {
			// 每 tick 恰好 -1 的 CD 倒计时：递减期走批量预测通道，归零帧/对账走立即同步（2026-09-24）
			if (waterCooling) PowerUtils.countDownAndSync(player, WATER_HUD, Math.max(0, wcd - 1));
			else PowerUtils.setResourceValueAndSync(player, WATER_HUD, Math.max(0, wcd - 1));
		}
		if (landCooling || lcd == 1 || (idleAudit
				&& PowerUtils.getResourceValue(player, LAND_HUD) != 0)) {
			if (landCooling) PowerUtils.countDownAndSync(player, LAND_HUD, Math.max(0, lcd - 1));
			else PowerUtils.setResourceValueAndSync(player, LAND_HUD, Math.max(0, lcd - 1));
		}
		if (!EvolutionTalents.isActive(player, AxolotlTree.NODE_WATER_SPURT)) {
			SPRINT_INPUT.remove(id);
		}
	}

	private static void tryDash(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		boolean inWater = player.isTouchingWater();
		int cdTicks = BAL.i("cd_ticks", CD_TICKS);

		if (inWater) {
			if (WATER_CD.getOrDefault(id, 0) <= 0) {
				doDash(player);
				WATER_CD.put(id, cdTicks);
				PowerUtils.setResourceValueAndSync(player, WATER_HUD, cdTicks);
			}
			return;
		}

		if (LAND_CD.getOrDefault(id, 0) <= 0) {
			int landCost = landAirCostForHud(player);
			if (player.getAir() < landCost) {
				return; // 湿润度不足，不触发
			}
			player.setAir(player.getAir() - landCost);
			doDash(player);
			LAND_CD.put(id, cdTicks);
			PowerUtils.setResourceValueAndSync(player, LAND_HUD, cdTicks);
		}
	}

	/** 沿视线前冲一次（水陆一致的位移与视听反馈）。 */
	private static void doDash(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVector();
		double burst = BAL.d("burst", BURST);
		player.addVelocity(look.x * burst, look.y * burst, look.z * burst);
		player.velocityModified = true;

		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.2f, 1.0f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.3f, 0.7f);
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6, player.getZ(),
				60, 0.6, 0.4, 0.6, 0.6);
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.5, player.getZ(),
				40, 0.5, 0.4, 0.5, 0.5);
		net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnWaterBurst(
				sw, player, player.getX(), player.getY() + 0.6, player.getZ(), 1.2);
	}

	public static void onPlayerDisconnect(UUID id) {
		SPRINT_INPUT.remove(id);
		WATER_CD.remove(id);
		LAND_CD.remove(id);
	}
}
