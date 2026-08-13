package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.StunEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AxolotlWaterSpurtHandler;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentLaserManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentTidalManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormWitherSand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.PlayDeadAbsorptionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexGuideManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindDashManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritLandingSurgeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WitherFrenzyManager;
import net.onixary.shapeShifterCurseFabric.util.CustomEdibleUtils;

import java.util.Collection;

/**
 * SSCA 服务端 tick / 生命周期相关事件注册（从 SscAddon 拆分而来）：
 * - registerTickHandlers：每 world/server tick 推进各形态被动与技能
 * - registerStunOrphanCleanup：水矛数量硬上限监测 + 定身(STUN)孤儿属性修正清理
 * - registerFeralBodyYawSync：多人下四足形态头身夹角收敛
 * - registerServerLifecycleHandlers：服务器启动/关闭/数据包重载时清理技能静态状态
 */
public final class SscAddonServerEvents {

	private SscAddonServerEvents() {}

	public static void registerTickHandlers() {
		ServerTickEvents.START_WORLD_TICK.register(world -> {
			// 在服务器线程上处理断线玩家的领域方块还原
			if (world.getRegistryKey() == World.OVERWORLD) {
				AnubisWolfSpDeathDomain.tickCleanup();
				// 契灵：每秒清理过期的恐惧减速 modifier
				if (world.getServer().getTicks() % 20 == 0) {
					MancianimaPassive
							.serverGlobalFleeCleanup(world.getServer());
					// 契灵劫掠军组生命周期推进（LINGER → MARCH → 脱适清理）
					MancianimaPassive
							.tickRaiderGroups(world.getServer());
				}
			}
			for (ServerPlayerEntity player : world.getPlayers()) {
				// 修复局域网多人游戏中远程玩家的自定义可食用物品Map未在服务端刷新的问题
				// 原版mod在集成服务器(EnvType.CLIENT)环境下跳过了OnServerTick，导致非主机玩家无法食用自定义食物（如悦灵吃紫水晶）
				if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
						&& player.age % 100 == 0) {
					CustomEdibleUtils.ReloadPlayerCustomEdible(player);
				}
				SnowFoxSpMeleeAbility.tick(player);
				SnowFoxSpTeleportAttack.tick(player);
				SnowFoxSpFrostStorm.tick(player);
				AllaySPGroupHeal.tick(player);
				AllaySPJukebox.tick(player);
				AnubisWolfSpDeathDomain.tick(player);
				AnubisWolfSpSummonWolves.tick(player);
				GoldenSandstormErosionBrand.tick(player);
				GoldenSandstormWitherSand.tick(player);
				GoldenSandstormRegen.tick(player);
				BatDesmodusBloodThirst.tick(player);
				MancianimaPassive.tick(player);
				VortexChargeManager.tick(player);
				net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.tick(player);
				net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.tick(player);			net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverMoonPoisonManager.tick(player);				WindSpiritClawManager.tick(player);
				WindDashManager.tick(player);
				WindSpiritLandingSurgeManager.tick(player);
				WaterSpearLeapManager.tick(player);
				VortexGuideManager.tick(player);
				AxolotlWaterSpurtHandler.tick(player);
				PlayDeadAbsorptionManager.tick(player);
				FluorescentTidalManager.tick(player);
				FluorescentLaserManager.tick(player);
				// 冥裁者凋零阶梯 / 凋零抗性追踪（凋零持续时长分层 + tick 跳过计数）
				WitherFrenzyManager.tick(player);
				EvolutionManager.tickPlayer(player);
			// SSCA 专属饰品登录守卫：登录宽容放行后，形态不符的自动卸下归还（Curios/Trinkets 双后端）
			net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.tick(player);
		}
	});

		// END_SERVER_TICK：荧光幼灵技能 pendingCd 补设（球/盾消失后回调无法直接拿到 player）
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
			FluorescentTidalManager.tickPendingCd(players);
		});


		// 月织蛛「织网术」/「蛛丝荡漾」：玩家掉线清理状态，防僵尸 UUID 残留
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) -> {
			net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.onDisconnect(netHandler.player.getUuid());
			net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.onDisconnect(netHandler.player.getUuid());
		});

		// 减速蜘网施法者表：服务器停止时清空，防跨存档/重启残留
		ServerLifecycleEvents.SERVER_STOPPED.register(server ->
				net.jackcooper.shapeShifterCurseAddon.block.WebMembraneOwners.clear());
	}

	/**
	 * 兜底：清除残留的「定身(STUN)」攻击力/移速孤儿属性修正。
	 * STUN 用固定 UUID 的属性修正实现「攻击力 -100% / 移速 -100%」。由于
	 * GENERIC_ATTACK_DAMAGE 不是被同步追踪的属性、且换形态时不会被重建，一旦 STUN 经
	 * 非正常路径（如换形态清状态效果）被移除而未触发 onStatusEffectRemoved，这个 -100%
	 * 修正会以孤儿形式残留在玩家身上，导致「任意武器0伤、无图标、跨形态保留、过会才自愈」的bug。
	 * 此处每服务端 tick 对在线玩家做校正：没有 STUN 效果却仍带 STUN 的固定 UUID 修正 → 立即移除。
	 * （同时清理已存在于老存档的孤儿残留。）
	 */
	public static void registerStunOrphanCleanup() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				// [DEBUG] 水矛出现监测 + 硬上限：背包最多 1 把水矛，多余立即移除（兜底任何未知产出路径）
				// 性能优化：背包全扫（41 格）降频到每 10 tick 一次——水矛「有→无」触发合成CD重置最多晚 10t，肉眼不可察；STUN 属性校正仍每 tick（见下方）。
				if (server.getTicks() % 10 == 0 && FormUtils.isAxolotlSP(player)) {
					PlayerInventory inv = player.getInventory();
					int wsCnt = 0;
					for (int i = 0; i < inv.size(); i++) {
						if (inv.getStack(i).isOf(SscAddon.WATER_SPEAR)) {
							wsCnt++;
							if (wsCnt > 1) {
								inv.setStack(i, ItemStack.EMPTY);
								SscAddon.WS_DBG.warn("[WS-CAP] 移除多余水矛 slot={} @tick {}", i, server.getTicks());
								wsCnt--;
							}
						}
					}
					Integer wsPrev = SscAddon.WS_LAST_SPEAR_COUNT.put(player.getUuid(), wsCnt);
					if (wsPrev != null && wsCnt > wsPrev) {
						long wsT = server.getTicks();
						Long wsUntil = SscAddon.WATER_SPEAR_CRAFT_CD.get(player.getUuid());
						SscAddon.WS_DBG.warn("[WS-MONITOR] 水矛数 {}->{} @tick {} ; internalCD until={} cooling={} ; arrowCD={}",
								wsPrev, wsCnt, wsT, wsUntil, (wsUntil != null && wsT < wsUntil),
								player.getItemCooldownManager().isCoolingDown(Items.ARROW));
					}
					// 水矛从「有」变「无」(扛出/消耗) → 重启 Apoli 合成冷却，使「合成CD」从水矛消失那刻起算
					// 否则持矛期间 active_self 的 cooldown 会走完，扛出后可立即秒合成（用户反馈的 bug）
					if (wsPrev != null && wsPrev > 0 && wsCnt == 0) {
						long wsT = server.getTicks();
						SscAddon.WATER_SPEAR_CRAFT_CD.put(player.getUuid(), wsT + SscAddon.WATER_SPEAR_CRAFT_CD_TICKS);
						player.getItemCooldownManager().set(Items.ARROW, SscAddon.WATER_SPEAR_CRAFT_CD_TICKS);
						PowerUtils.resetCooldown(player,
								new Identifier("my_addon", "form_axolotl_sp_water_spear_craft_spear"));
						SscAddon.WS_DBG.warn("[WS-CD] 水矛消失 @tick {} → 重启合成冷却(从消失起算 {}t)", wsT, SscAddon.WATER_SPEAR_CRAFT_CD_TICKS);
					}
				}
				if (player.hasStatusEffect(SscAddon.STUN)) continue;
				EntityAttributeInstance atk =
						player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
				if (atk != null && atk.getModifier(StunEffect.ATTACK_MODIFIER_UUID) != null) {
					atk.removeModifier(StunEffect.ATTACK_MODIFIER_UUID);
				}
				EntityAttributeInstance spd =
						player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
				if (spd != null && spd.getModifier(StunEffect.SPEED_MODIFIER_UUID) != null) {
					spd.removeModifier(StunEffect.SPEED_MODIFIER_UUID);
				}
			}
		});
	}

	/**
	 * 修复多人下客机看主机时四足(FERAL)形态头部偶尔「转过身后」的视觉异常。
	 * 根因：vanilla 服务端 ServerPlayerEntity.bodyYaw 只在玩家「移动」时才被 tickHeadTurn 拉向 headYaw。
	 * 玩家站着只转鼠标时，移动包只上报 pos+yaw(=headYaw)+pitch，不带 bodyYaw，服务端 bodyYaw 保持陈旧值；
	 * 服务端再把「新 headYaw + 陈旧 bodyYaw」一起发给远端客机，远端 OtherClientPlayerEntity 直接采信，
	 * head−body 夹角于是很大。人形头骨绕颈部偏转视觉不明显，但四足形态头骨水平前伸，看上去就是「头扭过身后」。
	 * 主机走一步路 → 服务端 bodyYaw 被 tickHeadTurn 拉正 → 自愈。生物 bodyYaw 由服务端持续维护所以不受影响。
	 * 这里每服务端 tick 给已激活 Mod 的 FERAL 形态玩家补一个 tickHeadTurn 等效收敛：把 bodyYaw 限速拉向 headYaw，
	 * 并夹住头身夹角 ≤ 75°（与 vanilla LivingEntity.tickHeadTurn 一致），使服务端发出的 bodyYaw 不再陈旧。
	 * 仅作用于玩家自身的 bodyYaw（服务端权威字段），主客机都靠它，零客机预测冲突。
	 */
	public static void registerFeralBodyYawSync() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				IForm form =
						net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.getPlayerForm(player);
				if (form == null
						|| form.getBodyType() != PlayerFormBodyType.FERAL) {
					continue;
				}
				// 把 bodyYaw 朝 headYaw 收敛（vanilla tickHeadTurn 同款：限速 + 夹角钳制）。
				float headYaw = player.getHeadYaw();
				float bodyYaw = player.bodyYaw;
				float diff = MathHelper.wrapDegrees(headYaw - bodyYaw);
				// 头身夹角钳制到 ±75°（超出部分立即并入身体朝向，避免极端扭头）
				float clampedDiff = MathHelper.clamp(diff, -75.0f, 75.0f);
				float overflow = diff - clampedDiff;
				// 收敛速度：每 tick 最多转 10°，模拟身体平滑跟随视角
				float step = MathHelper.clamp(clampedDiff, -10.0f, 10.0f);
				float newBodyYaw = bodyYaw + step + overflow;
				if (newBodyYaw != bodyYaw) {
					player.bodyYaw = newBodyYaw;
					player.prevBodyYaw = newBodyYaw;
				}
			}
		});
	}

	public static void registerServerLifecycleHandlers() {
		// 服务器启动时清除所有技能静态状态（在世界加载之前触发）
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			System.out.println("[SSC_ADDON] SERVER_STARTING event fired, clearing all ability static state");
			SnowFoxSpMeleeAbility.clearAll();
			SnowFoxSpTeleportAttack.clearAll();
			SnowFoxSpFrostStorm.clearAll();
			AnubisWolfSpDeathDomain.clearAll();
			AnubisWolfSpSummonWolves.clearAll();
			AllaySPTotem.clearAll();
			GoldenSandstormErosionBrand.clearAll();
			GoldenSandstormWitherSand.clearAll(server);
			GoldenSandstormRegen.clearAll();
			BatDesmodusBloodThirst.clearAll();
			UndeadNeutralState.clearAll();
			MancianimaPassive.clearAll();
			SscAddonActions.clearAll();
			FluorescentLaserManager.clearAll();   // 海晶荧光坠增强激光：清残留待机法阵实体
			System.out.println("[SSC_ADDON] SERVER_STARTING ability state cleared");
		});
		// 服务器关闭前还原所有死亡领域方块（在世界存档之前触发）
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			System.out.println("[SSC_ADDON] SERVER_STOPPING event fired, calling forceRestoreAll");
			AnubisWolfSpDeathDomain.forceRestoreAll();
			System.out.println("[SSC_ADDON] SERVER_STOPPING forceRestoreAll completed");
		});
		// 数据包重新加载成功后清除所有技能静态状态
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (!success) {
				System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD: reload failed, skipping ability state clear");
				return;
			}
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD: reload successful, clearing all ability static state");
			SnowFoxSpMeleeAbility.clearAll();
			SnowFoxSpTeleportAttack.clearAll();
			SnowFoxSpFrostStorm.clearAll();
			// reload 可能遇到玩家正在释放领域，必须先强制还原方块再清状态，避免世界里残留灵魂沙
			AnubisWolfSpDeathDomain.forceRestoreAll();
			AnubisWolfSpSummonWolves.clearAll();
			AllaySPTotem.clearAll();
			GoldenSandstormErosionBrand.clearAll();
			GoldenSandstormWitherSand.clearAll(server);
			GoldenSandstormRegen.clearAll();
			BatDesmodusBloodThirst.clearAll();
			UndeadNeutralState.clearAll();
			MancianimaPassive.clearAll();
			SscAddonActions.clearAll();
			FluorescentLaserManager.clearAll();   // 海晶荧光坠增强激光：清残留待机法阵实体
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD ability state cleared");
		});
	}
}
