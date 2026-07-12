package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import io.github.apace100.apoli.ApoliClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ErosionBrandClientState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkClientState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.AllaySPManaBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.AnubisWolfSPSoulBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.SnowFoxSPManaBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.MancianimaResistanceBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.hud.SkillCooldownBarRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.WaterSpearEntityRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.FluorescentLaserRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.WitchFamiliarRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.PotionBagScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SscAddonClient implements ClientModInitializer {
	public static final String CATEGORY = "key.categories.ssc_addon";
	private static final Logger LOGGER = LoggerFactory.getLogger(SscAddonClient.class);

	// 跨存档颜色重同步：JOIN 时置为正值倒数，归零时触发一次 sendUpdateCustomSetting
	private static int joinResyncDelay = 0;

	private void addSplitTooltip(List<Text> lines, String key) {
		if (I18n.hasTranslation(key)) {
			String translated = I18n.translate(key);
			for (String line : translated.split("\n")) {
				lines.add(Text.literal(line).formatted(Formatting.GRAY));
			}
		}
	}

	// SP Keybindings are now managed in SscAddonKeybindings.java

	@Override
	public void onInitializeClient() {
		LOGGER.info("[SSC_ADDON] Registering Client KeyBindings...");

		// 关键路径：键位注册必须成功，否则客机所有 SP 技能都无法激活。
		// 包裹 try-catch 防止任何意外（如类加载失败）静默吞掉异常导致客机无反应。
		try {
			SscAddonKeybindings.register();
			// 关键修复：复用 SSC 原版的 primary_active / secondary_active 键位对象，
			// 不再单独注册 G 键，避免与 SSC 的 KEY_TO_BINDINGS 注册冲突
			// （冲突会导致 SSC 与 SSCA 的主动技能在 G 键上互相覆盖、按键失效）。
			// Apoli 端仍以 ssc_addon.sp_primary / sp_secondary 作为 ID，所有 powers JSON 无需改动。
			ApoliClient.registerPowerKeybinding("key.ssc_addon.sp_primary", SscAddonKeybindings.getPrimaryKey());
			ApoliClient.registerPowerKeybinding("key.ssc_addon.sp_secondary", SscAddonKeybindings.getSecondaryKey());
			LOGGER.info("[SSC_ADDON] SP keybindings bound to SSC primary_active / secondary_active (shared with SSC to avoid G-key conflict)");
		} catch (Throwable t) {
			LOGGER.error("[SSC_ADDON] CRITICAL: Failed to register client keybindings - SP skills will not work on this client!", t);
		}

		// 客户端断线时清理侵蚀烙印缓存，防止重连后残留旧发光数据
		ClientPlayConnectionEvents.DISCONNECT.register((handler2, client2) -> {
			ErosionBrandClientState.clear();
			MancianimaMarkClientState.clear();
			net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.TidalTetherBeamRenderer.clear();
		});

		// 荧光幼灵「潮汐束缚」守卫者激光：服务端同步被拴目标 entityId，客户端逐帧画光束
		ClientPlayNetworking.registerGlobalReceiver(
				net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_TIDAL_TETHER,
				(client, handler, buf, responseSender) -> {
					int orbId = buf.readVarInt();
					int count = buf.readVarInt();
					if (count < 0 || count > 64) return;
					int[] ids = new int[count];
					for (int i = 0; i < count; i++) ids[i] = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.TidalTetherBeamRenderer
								.update(orbId, ids, client.world.getTime() + 20);
					});
				});
		// 逐帧渲染潮汐束缚光束（守卫者激光样式）
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(
				net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.TidalTetherBeamRenderer::render);

		// 修复跨存档颜色变白：cloth-config 里的自定义颜色是全局存储，但服务端 PlayerSkinComponent 按存档独立。
		// 进入新世界/服务器时，主动把本地 cloth-config 的颜色状态重发给当前服务端，避免新存档拿到默认白色。
		// 用一个静态倒计时 + 单次注册的 tick 监听，避免多次 JOIN 累积监听器。
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			joinResyncDelay = 20; // 约 1 秒，等待网络通道 & 玩家实体就绪
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
			if (joinResyncDelay <= 0) return;
			if (c.player == null || c.world == null) return;
			if (--joinResyncDelay > 0) return;
			try {
				net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C.sendUpdateCustomSetting(true);
				// 同时补发颜色包：SSC 主包的 sendUpdateCustomSetting 漏调 send，
				// 不在此处手动发，新存档/服务器的 PlayerSkinComponent 颜色不会被同步。
				net.onixary.shapeShifterCurseFabric.config.PlayerCustomConfig cfg =
						net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.playerCustomConfig;
				net.minecraft.network.PacketByteBuf cbuf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
				// 主包 onUpdatePlayerCustomColor 首位读的是 extraData boolean，
				// 漏写这一字节会导致服务端读越界、玩家被踢。
				cbuf.writeBoolean(false);
				cbuf.writeInt(net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(cfg.primaryColor));
				cbuf.writeInt(net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(cfg.accentColor1Color));
				cbuf.writeInt(net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(cfg.accentColor2Color));
				cbuf.writeInt(net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(cfg.eyeColorA));
				cbuf.writeInt(net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ARGB2ABGR(cfg.eyeColorB));
				cbuf.writeBoolean(cfg.primaryGreyReverse);
				cbuf.writeBoolean(cfg.accent1GreyReverse);
				cbuf.writeBoolean(cfg.accent2GreyReverse);
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new net.minecraft.util.Identifier(net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.MOD_ID, "update_custom_color"), cbuf);
				// 请求服务端把所有在场玩家的形态+皮肤同步过来（修复客机看其它玩家是默认白模型）。
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_REQUEST_ALL_FORM_SYNC,
						net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
			} catch (Throwable t) {
				LOGGER.error("[SSC_ADDON] 跨存档颜色重同步失败", t);
			}
		});

		// 注册契灵准星射线追踪（每客户端 tick 更新当前瞄准目标）
		try { MancianimaCrosshairTracker.register(); } catch (Throwable t) { LOGGER.error("[SSC_ADDON] CrosshairTracker register failed", t); }
		// 注册「SSCA 进化路线定义同步」接收器：服务端把 routes JSON 同步过来，供进化树 UI（多人）渲染。
		ClientPlayNetworking.registerGlobalReceiver(
				net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_EVO_ROUTES_SYNC,
				(client, handler, buf, responseSender) -> {
					int count = buf.readInt();
					if (count < 0 || count > 1000) return;
					java.util.Map<String, String> raw = new java.util.LinkedHashMap<>();
					for (int i = 0; i < count; i++) {
						String routeId = buf.readString(256);
						String json = buf.readString(2000000);
						raw.put(routeId, json);
					}
					client.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry.INSTANCE.applyClientSync(raw));
				});
		// 注册「广播所有玩家形态」接收器：服务端把在场玩家的 formID + 皮肤数据直接广播过来，
		// 客机按 UUID 直接写入其它玩家的 nowForm/nowFormID 与 PlayerSkinComponent（颜色/是否启用形态颜色等），
		// 绕过 CCA 同步的不确定性，修复刚进游戏看其它玩家是「白色人类模型」（enableFormColor 未同步=渲染原版人类模型）。
		ClientPlayNetworking.registerGlobalReceiver(
				net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_BROADCAST_FORMS,
				(client, handler, buf, responseSender) -> {
					int count = buf.readInt();
					if (count < 0 || count > 1000) return; // 防恶意服务端 OOM
					java.util.List<java.util.UUID> uuids = new java.util.ArrayList<>(count);
					java.util.List<String> formIds = new java.util.ArrayList<>(count);
					java.util.List<boolean[]> boolData = new java.util.ArrayList<>(count); // [keepOrig, enableColor, pGrey, a1Grey, a2Grey, enableSound]
					java.util.List<int[]> colorData = new java.util.ArrayList<>(count);    // [primary, accent1, accent2, eyeA, eyeB] (ABGR)
					for (int i = 0; i < count; i++) {
						uuids.add(buf.readUuid());
						formIds.add(buf.readString());
						boolean keepOrig = buf.readBoolean();
						boolean enableColor = buf.readBoolean();
						int primary = buf.readInt();
						int accent1 = buf.readInt();
						int accent2 = buf.readInt();
						int eyeA = buf.readInt();
						int eyeB = buf.readInt();
						boolean pGrey = buf.readBoolean();
						boolean a1Grey = buf.readBoolean();
						boolean a2Grey = buf.readBoolean();
						boolean enableSound = buf.readBoolean();
						boolData.add(new boolean[]{keepOrig, enableColor, pGrey, a1Grey, a2Grey, enableSound});
						colorData.add(new int[]{primary, accent1, accent2, eyeA, eyeB});
					}
					client.execute(() -> {
						if (client.world == null) return;
						for (int i = 0; i < uuids.size(); i++) {
							net.minecraft.entity.player.PlayerEntity p = client.world.getPlayerByUuid(uuids.get(i));
							if (p == null) continue;
							// 形态
							String fidStr = formIds.get(i);
							if (!fidStr.isEmpty()) {
								net.minecraft.util.Identifier fid = net.minecraft.util.Identifier.tryParse(fidStr);
								if (fid != null) {
									net.onixary.shapeShifterCurseFabric.player_form.IForm form =
											net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(fid);
									if (form != null) {
										net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent comp =
												net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT.get(p);
										comp.nowForm = form;
										comp.nowFormID = fid;
										// 关键：模型渲染读的是 origin 组件（PlayerOriginComponent）而非 nowForm。
										// 用形态的 layer 信息在客机重建 origin，渲染才会显示形态模型（否则只同步了 scale/动画 = 白色人类模型）。
										try {
											net.minecraft.util.Pair<net.minecraft.util.Identifier, net.minecraft.util.Identifier> layerData = form.getFormLayer();
											net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayer layer =
													net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayers.getLayer(layerData.getLeft());
											if (layer != null && layerData.getRight() != null) {
												net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin origin =
														net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry.get(layerData.getRight());
												if (origin != null) {
													net.onixary.shapeShifterCurseFabric.integration.origins.component.OriginComponent oc =
															(net.onixary.shapeShifterCurseFabric.integration.origins.component.OriginComponent)
																	net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents.ORIGIN.get(p);
													oc.setOrigin(layer, origin);
												}
											}
										} catch (Throwable ignored) {
											// power 客户端不全等极端情况，忽略；渲染只需 origins map 写入成功
										}
										// 同步形态缩放（Pehkui）：广播原先漏了 scale，导致客机看其它玩家模型偏大/站立、超出判定框。
										// 对该玩家应用其形态的 applyScale（缩放形态缩小、人类形态复位 1.0），与 nowForm/origin/skin 一致由客机本地重建。
										try {
											form.applyScale(p);
										} catch (Throwable ignored) {
											// Pehkui 未加载或异常时忽略，不影响其它同步
										}
									}
								}
							}
							// 皮肤（颜色 / 是否启用形态颜色等）
							boolean[] b = boolData.get(i);
							int[] cc = colorData.get(i);
							net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent skin =
									net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
							skin.setKeepOriginalSkin(b[0]);
							skin.setEnableFormColor(b[1]);
							skin.setFormColor(new net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ColorSetting(
									cc[0], cc[1], cc[2], cc[3], cc[4], b[2], b[3], b[4]));
							skin.setEnableFormRandomSound(b[5]);
						}
					});
				});

		// 注册侵蚀烙印 S2C 同步包接收器
		ClientPlayNetworking.registerGlobalReceiver(GoldenSandstormErosionBrand.PACKET_BRAND_SYNC, (client, handler, buf, responseSender) -> {
			int count = buf.readInt();
			// 安全守卫：防止被劫持服务器发超大 count 导致客机 OOM
			if (count < 0 || count > 10000) return;
			java.util.Map<java.util.UUID, String> brands = new java.util.HashMap<>();
			for (int i = 0; i < count; i++) {
				java.util.UUID uuid = buf.readUuid();
				String color = buf.readString();
				brands.put(uuid, color);
			}
			client.execute(() -> ErosionBrandClientState.update(brands));
		});

		// 注册契灵标记 S2C 同步包接收器
		ClientPlayNetworking.registerGlobalReceiver(MancianimaMarkManager.PACKET_MARK_SYNC, (client, handler, buf, responseSender) -> {
			int count = buf.readInt();
			// 安全守卫：防止被劫持服务器发超大 count 导致客机 OOM
			if (count < 0 || count > 10000) return;
			java.util.Map<java.util.UUID, String> marks = new java.util.HashMap<>();
			for (int i = 0; i < count; i++) {
				java.util.UUID uuid = buf.readUuid();
				String color = buf.readString();
				marks.put(uuid, color);
			}
			client.execute(() -> MancianimaMarkClientState.update(marks));
		});

		// 风灵「疾风连爪」：接收爪击阶段+准星条进度，更新客户端镜像
		ClientPlayNetworking.registerGlobalReceiver(net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_CLAW_STATE, (client, handler, buf, responseSender) -> {
			int phase = buf.readInt();
			float progress = buf.readFloat();
			client.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.client.ClawClientState.update(phase, progress));
		});

        // 风灵「风之冲刺」：接收阶段+目标悬浮Y，更新客户端镜像（驱动悬浮期绿色落点预览）
        ClientPlayNetworking.registerGlobalReceiver(net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_DASH_STATE, (client, handler, buf, responseSender) -> {
            int phase = buf.readInt();
            double targetY = buf.readDouble();
            client.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.client.DashClientState.update(phase, targetY));
        });

		// 注册白名单 GUI S2C 同步包接收器：收到后打开/刷新 WhitelistManageScreen
		ClientPlayNetworking.registerGlobalReceiver(net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_WHITELIST_GUI_SYNC, (client, handler, buf, responseSender) -> {
			boolean customMode = buf.readBoolean();
			int n = buf.readInt();
			if (n < 0 || n > 10000) return;
			java.util.Set<java.util.UUID> set = new java.util.HashSet<>();
			for (int i = 0; i < n; i++) set.add(buf.readUuid());
			int m = buf.readInt();
			if (m < 0 || m > 10000) return;
			java.util.List<net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.WhitelistManageScreen.MobEntry> mobs = new java.util.ArrayList<>();
			for (int i = 0; i < m; i++) {
				java.util.UUID u = buf.readUuid();
				String typeId = buf.readString();
				mobs.add(new net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.WhitelistManageScreen.MobEntry(u, typeId.isEmpty() ? null : typeId));
			}
			client.execute(() -> {
				if (client.currentScreen instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.WhitelistManageScreen s) {
					s.updateState(set, customMode, mobs);
				} else {
					client.setScreen(new net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.WhitelistManageScreen(set, customMode, mobs));
				}
			});
		});

		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (stack.getItem() == SscAddon.CORAL_BALL) {
				addSplitTooltip(lines, "item.ssc_addon.coral_ball.tooltip");
			}
		});

		EntityRendererRegistry.register(SscAddon.WATER_SPEAR_ENTITY, WaterSpearEntityRenderer::new);

		// 注册冰球渲染器（使用雪球材质）和冰风暴渲染器（粒子效果，空渲染器）
		EntityRendererRegistry.register(SscAddon.FROST_BALL_ENTITY, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.FROST_STORM_ENTITY, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.FOX_FIREBALL_ENTITY, ctx -> new net.minecraft.client.render.entity.FlyingItemEntityRenderer<>(ctx, 1F, true));
		EntityRendererRegistry.register(SscAddon.FRIEND_MARKER_ENTITY_TYPE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.CLEAR_MARKER_ENTITY_TYPE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.INFECTION_SPORE_BOMB_ENTITY, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.PARASITIC_SEED_ENTITY, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.WITCH_FAMILIAR_ENTITY, WitchFamiliarRenderer::new);
		// 荧光幼灵：潮汐球用 FlyingItemEntityRenderer 渲染潮涌方块作发光核心（对齐 red 火球标准）；
		// 法阵激光用自定义渲染器画发光法阵 + 穿墙光柱（自发光、粗彩带）
		EntityRendererRegistry.register(SscAddon.TIDAL_ORB_ENTITY, net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.TidalOrbRenderer::new);
		EntityRendererRegistry.register(SscAddon.LASER_BEAM_ENTITY, FluorescentLaserRenderer::new);

		// 寄生果蝠形态种子量能量条 HUD
		SeedEnergyHudRenderer.register();

		// 朔望九命剩余命数 HUD
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.NineLivesHudRenderer.register();

		// 女巫使魔刷怪蛋颜色注册
		ColorProviderRegistry.ITEM.register(
				(stack, tintIndex) -> ((SpawnEggItem) stack.getItem()).getColor(tintIndex),
				SscAddon.WITCH_FAMILIAR_SPAWN_EGG
		);

		// 凋零药水（3型）：tintIndex 0 = 液体层染成凋零色（避免空瓶外观）；其它层（玻璃瓶）不染色
		ColorProviderRegistry.ITEM.register(
				(stack, tintIndex) -> tintIndex == 0 ? 0x4A403A : 0xFFFFFF,
				SscAddon.WITHER_POTION, SscAddon.WITHER_POTION_SPLASH, SscAddon.WITHER_POTION_LINGERING
		);

		// Register predicate for 3D model when held (0.0 = inventory/ground, 1.0 = held)
		// GUI/GROUND 渲染上下文时强制返回 0，避免 override 把物品栏图标也切到 3D
		ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "held"), (stack, world, entity, seed) ->
				net.onixary.shapeShifterCurseFabric.ssc_addon.util.RenderContextTracker.isGuiContext() ? 0.0F :
				(entity != null && (entity.getMainHandStack() == stack || entity.getOffHandStack() == stack) ? 1.0F : 0.0F)
		);

		// Also register "throwing" predicate for trident animation support if needed
		ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "throwing"), (stack, world, entity, seed) ->
				entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
		);

		// 无限压缩能量药水：empty 谓词（1=空瓶充能中，切换为空瓶材质）。优先用世界时间戳判断，无世界时退回 NBT 标记
		net.minecraft.client.item.ClampedModelPredicateProvider infiniteEnergyEmptyPredicate = (stack, world, entity, seed) -> {
			net.minecraft.world.World w = world != null ? world : (entity != null ? entity.getWorld() : null);
			if (w != null) {
				return net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem.isRecharging(stack, w) ? 1.0F : 0.0F;
			}
			return net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem.isEmptyByNbt(stack) ? 1.0F : 0.0F;
		};
		ModelPredicateProviderRegistry.register(SscAddon.INFINITE_ENERGY_POTION, new Identifier("ssc_addon", "empty"), infiniteEnergyEmptyPredicate);
		ModelPredicateProviderRegistry.register(SscAddon.INFINITE_ENERGY_POTION_SPLASH, new Identifier("ssc_addon", "empty"), infiniteEnergyEmptyPredicate);
		ModelPredicateProviderRegistry.register(SscAddon.INFINITE_ENERGY_POTION_LINGERING, new Identifier("ssc_addon", "empty"), infiniteEnergyEmptyPredicate);

		// SP技能键位现在由Apoli框架自动处理，无需手动轮询
		// 如需添加新的非Apoli键位检测，可在此处注册

		HudRenderCallback.EVENT.register(new SnowFoxSPManaBar());
		HudRenderCallback.EVENT.register(new AllaySPManaBar());
		HudRenderCallback.EVENT.register(new AnubisWolfSPSoulBar());
		HudRenderCallback.EVENT.register(new SkillCooldownBarRenderer());
		HudRenderCallback.EVENT.register(new MancianimaResistanceBar());
		HudRenderCallback.EVENT.register(new net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.BatDesmodusBloodBar());

		// 契灵 - 次要技能瞬移：客户端按键监听 + 紫色粒子预览
		MancianimaTeleportClient.register();
		// 契灵 - 主要技能：三段标记
		MancianimaPrimaryClient.register();

		HandledScreens.register(SscAddon.POTION_BAG_SCREEN_HANDLER, PotionBagScreen::new);

		// SSCA 美西螈装死 - 提前结束检测器
		PlayDeadEndClient.register();

		// SSCA 美西螈漩涡蓄力 - 按键检测器
		VortexChargeClient.register();
		// 风灵「疾风连爪」 - 左键按住检测器
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.WindSpiritClawClient.register();
		// 风灵「风之冲刺」 - 主技能键检测器 + 悬浮期绿色落点预览
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.WindDashClient.register();
		// 荧光幼灵技能按键检测器（主要=潮汐波动 / 次要=水盾）
		FluorescentKeyClient.register();

		// SSCA 进化加点系统 - 在幻形者之书界面注入「进化加点」入口按钮（使魔形态显示）
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution.EvolutionBookHook.register();

		// SSCA 进化路线 - 在「翻开幻形者之书」开局界面注入「进入 SSCA 进化路线」入口按钮
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution.SscaStartBookHook.register();

		// SSCA 能量条 / 本能条位置可视化编辑器 - 在 SSC「客户端配置」cloth-config 界面注入入口按钮（fabric ScreenEvents，非 Mixin）
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.BarPositionEditorScreen.registerEntry();
	}
}
