package net.jackcooper.shapeShifterCurseAddon.event;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.additional_power.VirtualTotemPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormWitherSand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.ErosionSandPrismItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitheredSandRingItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;

/**
 * SSCA 玩家生命周期事件注册（从 SscAddon.registerPlayerEventHandlers 拆分而来）：
 * - 重生后清理特定物品冷却 + SP阿努比斯亡灵不死冷却
 * - 首次进入世界的欢迎消息（延迟按语言发送）
 * - 客机开始追踪玩家时补发形态组件（修复远程玩家显示原版模型）
 * - 进化美西螈投掷水矛蓄力期禁交互
 * - 断线时清理各技能静态状态 Map
 */
public final class SscAddonPlayerEvents {

	private SscAddonPlayerEvents() {}

	public static void register() {
		// 兜底：玩家加入服务器时清理孤儿 mana 数据，修复老存档残留导致能量条不消失的 bug
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.StaleManaCleaner.register();

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.getItemCooldownManager().remove(SscAddon.LIFESAVING_CAT_TAIL);
				newPlayer.getItemCooldownManager().remove(SscAddon.PHANTOM_BELL);

				// SP阿努比斯之狼：死亡后重置亡灵不死被动冷却
				if (FormUtils.isAnubisWolfSP(newPlayer)) {
					for (VirtualTotemPower power : PowerHolderComponent.getPowers(newPlayer, VirtualTotemPower.class)) {
						if (power.getRemainingTicks() > 0) {
							power.modify(-power.getRemainingTicks());
							PowerHolderComponent.syncPower(newPlayer, power.getType());
						}
					}
				}
			}
		});

		// 玩家首次进入世界时发送欢迎消息（延迟3秒，等待客户端语言设置到达服务端后根据语言发送对应文本）
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			// 重连/换维度回归后：强制把契灵标记 + 金沙岚侵蚀印记状态重新同步给客户端，
			// 避免重连后客户端 HUD/渲染缓存为空，直到下一次状态变更才被动恢复。
			server.execute(() -> {
				try { net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager.resyncToPlayer(player); } catch (Throwable ignored) {}
				try { net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand.resyncToPlayer(player); } catch (Throwable ignored) {}
			});
			String welcomeTag = "ssc_addon_welcomed";
			if (!player.getCommandTags().contains(welcomeTag)) {
				player.addCommandTag(welcomeTag);
				final java.util.UUID playerUuid = player.getUuid();
				java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
						.execute(() -> {
							// 3 秒延迟到达时服务端可能已关闭/重启 —— 防御性检查，避免 IllegalStateException
							if (!server.isRunning()) return;
							server.execute(() -> {
							var p = server.getPlayerManager().getPlayer(playerUuid);
							if (p == null) return;
							String url = "https://github.com/MangZai-120/shape-shifter-curse-addon/issues";
							String wikiUrl = "https://www.mcmod.cn/class/24327.html";
							// 根据玩家客户端语言选择显示文本
							String lang = SscAddon.PLAYER_LANGUAGES.getOrDefault(playerUuid, "en_us");
							boolean isChinese = lang.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
							MutableText githubLink = Text.literal(url)
									.setStyle(Style.EMPTY
											.withColor(Formatting.AQUA)
											.withUnderline(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
							MutableText wikiLink = Text.literal(wikiUrl)
									.setStyle(Style.EMPTY
											.withColor(Formatting.AQUA)
											.withUnderline(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, wikiUrl)));
							if (isChinese) {
								// 第一行：欢迎+百科链接+bug说明+GitHub链接+崩溃说明
								p.sendMessage(Text.empty()
										.append(Text.literal("欢迎游玩幻形者诅咒扩展，游玩教程在MC百科上：").formatted(Formatting.GOLD))
										.append(wikiLink)
										.append(Text.literal("。由于作者水平有限，模组难免会有bug，如有bug请将日志发到GitHub上：").formatted(Formatting.GOLD))
										.append(githubLink)
										.append(Text.literal("；若后续更新版本导致崩溃请将崩溃日志以及必要信息文件发到模组的GitHub上，谢谢！").formatted(Formatting.GOLD)));
								// 第二行：请不要只发送照片（蓄意空格）
								p.sendMessage(Text.literal("请 不 要 只 发 送 照 片 过 来 谢 谢！").formatted(Formatting.RED));
								// 第三行：ps提示
								p.sendMessage(Text.literal("ps：此对话只显示这一次").formatted(Formatting.GRAY));
							} else {
								p.sendMessage(Text.empty()
										.append(Text.literal("Welcome to Shape Shifter's Curse Addon! Tutorial is available on MCMOD Wiki: ").formatted(Formatting.GOLD))
										.append(wikiLink)
										.append(Text.literal(". Due to the author's limited expertise, the mod may have bugs. If you encounter any, please submit your logs on GitHub: ").formatted(Formatting.GOLD))
										.append(githubLink)
										.append(Text.literal("; If a future update causes a crash, please submit the crash log and necessary info files to the mod's GitHub, thank you!").formatted(Formatting.GOLD)));
								p.sendMessage(Text.literal("Please do NOT only send screenshots, thank you!").formatted(Formatting.RED));
								p.sendMessage(Text.literal("PS: This message will only be shown once.").formatted(Formatting.GRAY));
							}
							});
						});
			}
		});

		// #13 修复：后加入的客机看「先在场玩家(含主机)」是 vanilla 玩家模型而非形态模型。
		// 根因：主包 PlayerFormComponent 是 Cardinal Components 的「玩家组件」(registerForPlayers)，
		// CCA 只在玩家「自己登录」时做一次初始同步，不会在其它玩家开始追踪该玩家时自动补发，
		// 导致新观察者追踪到先在场玩家时拿不到其形态数据，于是渲染成原版模型。
		// 方案：监听实体「开始追踪」事件——任一玩家开始追踪另一名玩家时，对被追踪玩家重发其形态组件，
		// 让新观察者(以及跨维度/远距离重新进入视野的玩家)及时拿到正确形态。
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof net.minecraft.server.network.ServerPlayerEntity tracked) {
				try {
					net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.sync(tracked);
				} catch (Throwable ignored) {
					// 极端时序下组件容器可能尚未就绪，忽略即可，下次状态变更会自动同步
				}
			}
		});

		// 进化美西螈「投掷水矛」蓄力期：服务端禁用右键放置方块 / 使用方块与物品（蓄力时不能做其它交互）
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient && net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager.isCharging(player.getUuid())) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager.isCharging(player.getUuid())) {
				return net.minecraft.util.TypedActionResult.fail(player.getStackInHand(hand));
			}
			return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
		});


		// 玩家断线时清理所有静态状态Map，防止内存泄漏和重连后状态错乱
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {

			java.util.UUID uuid = handler.player.getUuid();
			System.out.println("[SSC_ADDON] DISCONNECT event fired for player: " + handler.player.getName().getString());
			SnowFoxSpMeleeAbility.clearPlayer(uuid);
			SnowFoxSpTeleportAttack.clearPlayer(uuid);
			SnowFoxSpFrostStorm.clearPlayer(uuid);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager.onPlayerDisconnect(handler.player);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindDashManager.onPlayerDisconnect(handler.player);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritLandingSurgeManager.onPlayerDisconnect(handler.player);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager.onPlayerDisconnect(handler.player);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexGuideManager.onPlayerDisconnect(uuid);
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AxolotlWaterSpurtHandler.onPlayerDisconnect(uuid);
			AnubisWolfSpDeathDomain.clearPlayer(handler.player);
			AnubisWolfSpSummonWolves.clearPlayer(uuid);
			AllaySPTotem.clearPlayer(handler.player);
			GoldenSandstormErosionBrand.clearPlayer(handler.player);
			GoldenSandstormWitherSand.clearPlayer(handler.player);
			GoldenSandstormRegen.clearPlayer(uuid);
			// 灵魂能量：Apoli resource 本身会随玩家NBT持久化，不再在断线时清零
			ErosionSandPrismItem.clearPlayer(uuid);
			WitheredSandRingItem.clearPlayer(uuid);
			AllaySPJukebox.onPlayerDisconnect(handler.player);
			UndeadNeutralState.clearPlayer(uuid);
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearPlayer(uuid);
			SscAddon.PLAYER_LANGUAGES.remove(uuid);
			// 契灵：清理袭击 bossBar + raid 状态，防止 bossBar 残留与 Map 泄漏
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.onPlayerDisconnect(uuid);
			// 白名单 GUI 限频表：移除退出玩家的时间戳，防止僵尸 UUID 积累
			net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.onPlayerDisconnect(uuid);
			// 海晶荧光坠增强激光：combo 中途断线时清理待机法阵实体 + 移速 modifier，防止残留
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentLaserManager.onPlayerDisconnect(uuid);
			System.out.println("[SSC_ADDON] DISCONNECT cleanup completed");
		});
	}
}
