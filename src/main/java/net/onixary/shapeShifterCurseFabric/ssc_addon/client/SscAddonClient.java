package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import io.github.apace100.apoli.ApoliClient;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker.AdvancedColorScreen;
import org.lwjgl.glfw.GLFW;
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

	/** 打开高级调色 Screen 的键位（默认未绑定，玩家自行设置）。 */
	public static KeyBinding openAdvancedColorKey;

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

		// 契灵 - 次要技能瞬移：客户端按键监听 + 紫色粒子预览
		MancianimaTeleportClient.register();
		// 契灵 - 主要技能：三段标记
		MancianimaPrimaryClient.register();

		HandledScreens.register(SscAddon.POTION_BAG_SCREEN_HANDLER, PotionBagScreen::new);

		// ====== 高级调色：键位 + 客户端指令 + cloth-config 入口按钮 ======
		registerAdvancedColorEntries();
		net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker.ClothConfigInjector.register();
	}

	private void registerAdvancedColorEntries() {
		// 注册一个默认未绑定的键位，玩家可在控制设置里自行绑定
		openAdvancedColorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ssc_addon.open_advanced_color",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));

		// 每客户端 tick 检查键位（wasPressed 仅消费一次按下事件）
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openAdvancedColorKey == null) return;
			while (openAdvancedColorKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new AdvancedColorScreen(null));
				}
			}
		});

		// 注册客户端指令 /ssca_palette advanced — 纯客户端，不发包
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("ssca_palette")
					.then(ClientCommandManager.literal("advanced")
							.executes(ctx -> {
								MinecraftClient mc = MinecraftClient.getInstance();
								if (mc.player == null) return 0;
								mc.send(() -> mc.setScreen(new AdvancedColorScreen(null)));
								return 1;
							})));
		});
	}
}
