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
import net.minecraft.world.World;
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
			// 冻雪智被动「寒棘护体」反刺层过期清理（世界级，每 tick 轻量）
			net.jackcooper.shapeShifterCurseAddon.ability.FrostArmorManager.tick(world);
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
				net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager.tick(player);
				net.jackcooper.shapeShifterCurseAddon.ability.JumpKillManager.tick(player);
				net.jackcooper.shapeShifterCurseAddon.ability.VenomSkillManager.tick(player);
				// 跳蛛安全丝：纯锚点（跳杀已结束）6 秒倒计时推进，到时丝线消失
				net.jackcooper.shapeShifterCurseAddon.ability.JumpKillManager.tickAnchors(player);
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
				WitherFrenzyManager.tick(player);			// 食梦魔「入梦」状态推进（到期出梦清理 + 粉红描边同步）
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager.tick(player);
				// 食梦魔「恐惧」状态推进（入梦锁定/心跳/1s隐匿窗口/到期出梦+免疫）
				net.jackcooper.shapeShifterCurseAddon.ability.NightmareFearManager.tick(player);
				// 食梦魔「惊吓」幻影推进（复制品到期攻击结算）
				net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.tick(player);				EvolutionManager.tickPlayer(player);
			// SSCA 专属饰品登录守卫：登录宽容放行后，形态不符的自动卸下归还（Curios/Trinkets 双后端）
			net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.tick(player);
		}
	});

		// END_SERVER_TICK：荧光幼灵技能 pendingCd 补设（球/盾消失后回调无法直接拿到 player）
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Collection<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
			FluorescentTidalManager.tickPendingCd(players);
		});


		// 月织蛛「织网术」/「蛛丝荡漾」/ 食梦魔「惊吓」：玩家掉线清理状态，防僵尸 UUID 残留
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) -> {
			net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.onDisconnect(netHandler.player.getUuid());
			net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.onDisconnect(netHandler.player.getUuid());
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.onDisconnect(netHandler.player.getUuid());
			// 寒棘狐冰刺：退出时环绕冰锥随玩家消失（存档保留各槽存在时间，重进由 JOIN 恢复）
			net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager.onDisconnect(netHandler.player);
		});
		// 寒棘狐冰刺：重进后按退出前存档重建环绕冰锥（存在时间延续）
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager.onJoin(handler.player)));
		// 食梦魔「惊吓」：服务端监听目标攻击幽灵苦力怕（真实体受击判定）
		net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.registerEvents();

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
					// 鼠标拿起/拖拽中的水矛（光标携带）也算「仍在玩家手中」，不计为消失；
					// 否则左键拾取水矛时光标持有、背包扫描为 0 → 误判「水矛消失」→ 错误触发合成CD。
					// currentScreenHandler 在无打开容器时是 playerScreenHandler（玩家自身背包），始终非 null。
					// 必须在 put 取 wsPrev 之前累加，保证 wsCnt 与 wsPrev 都基于「背包+光标」完整口径。
					net.minecraft.screen.ScreenHandler handler = player.currentScreenHandler;
					if (handler != null && handler.getCursorStack().isOf(SscAddon.WATER_SPEAR)) {
						wsCnt += handler.getCursorStack().getCount();
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

	public static void registerServerLifecycleHandlers() {
		// 服务器启动时清除所有技能静态状态（在世界加载之前触发）
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			System.out.println("[SSC_ADDON] SERVER_STARTING event fired, clearing all ability static state");
			SnowFoxSpMeleeAbility.clearAll();
			SnowFoxSpTeleportAttack.clearAll();
			SnowFoxSpFrostStorm.clearAll();				net.jackcooper.shapeShifterCurseAddon.ability.FrostArmorManager.clearAll();			AnubisWolfSpDeathDomain.clearAll();
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
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.clearAll(server); // 惊吓：清幽灵苦力怕/复制品状态
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
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareSpookManager.clearAll(server); // 惊吓：清幽灵苦力怕/复制品状态
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD ability state cleared");
		});
	}
}
