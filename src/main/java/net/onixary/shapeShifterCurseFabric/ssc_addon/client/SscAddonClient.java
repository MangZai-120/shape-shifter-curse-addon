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
		});

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
			} catch (Throwable t) {
				LOGGER.error("[SSC_ADDON] 跨存档颜色重同步失败", t);
			}
		});

		// 注册契灵准星射线追踪（每客户端 tick 更新当前瞄准目标）
		try { MancianimaCrosshairTracker.register(); } catch (Throwable t) { LOGGER.error("[SSC_ADDON] CrosshairTracker register failed", t); }

		// 注册侵蚀烙印 S2C 同步包接收器
		ClientPlayNetworking.registerGlobalReceiver(GoldenSandstormErosionBrand.PACKET_BRAND_SYNC, (client, handler, buf, responseSender) -> {
			int count = buf.readInt();
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
			java.util.Map<java.util.UUID, String> marks = new java.util.HashMap<>();
			for (int i = 0; i < count; i++) {
				java.util.UUID uuid = buf.readUuid();
				String color = buf.readString();
				marks.put(uuid, color);
			}
			client.execute(() -> MancianimaMarkClientState.update(marks));
		});

		// 注册白名单 GUI S2C 同步包接收器：收到后打开/刷新 WhitelistManageScreen
		ClientPlayNetworking.registerGlobalReceiver(net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_WHITELIST_GUI_SYNC, (client, handler, buf, responseSender) -> {
			boolean customMode = buf.readBoolean();
			int n = buf.readInt();
			java.util.Set<java.util.UUID> set = new java.util.HashSet<>();
			for (int i = 0; i < n; i++) set.add(buf.readUuid());
			int m = buf.readInt();
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
		EntityRendererRegistry.register(SscAddon.FRIEND_MARKER_ENTITY_TYPE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.CLEAR_MARKER_ENTITY_TYPE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(SscAddon.WITCH_FAMILIAR_ENTITY, WitchFamiliarRenderer::new);

		// 女巫使魔刷怪蛋颜色注册
		ColorProviderRegistry.ITEM.register(
				(stack, tintIndex) -> ((SpawnEggItem) stack.getItem()).getColor(tintIndex),
				SscAddon.WITCH_FAMILIAR_SPAWN_EGG
		);

		// Register predicate for 3D model when held (0.0 = inventory/ground, 1.0 = held)
		ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "held"), (stack, world, entity, seed) ->
				entity != null && (entity.getMainHandStack() == stack || entity.getOffHandStack() == stack) ? 1.0F : 0.0F
		);

		// Also register "throwing" predicate for trident animation support if needed
		ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "throwing"), (stack, world, entity, seed) ->
				entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
		);

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
	}
}
