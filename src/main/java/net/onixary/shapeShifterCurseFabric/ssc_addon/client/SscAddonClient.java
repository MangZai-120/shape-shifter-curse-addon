package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.model.TridentEntityModel;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.AllaySPManaBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.SnowFoxSPManaBar;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.WaterSpearEntityRenderer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.screen.PotionBagScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
	private static final Logger log = LoggerFactory.getLogger(SscAddonClient.class);

	private TridentEntityModel tridentModel;
    
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
        log.info("SSC ADDON DEBUG: Registering Client KeyBindings...");
        
        SscAddonKeybindings.register();

        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (stack.getItem() == SscAddon.SHADOW_SHARD) {
                addSplitTooltip(lines, "item.ssc_addon.shadow_shard.tooltip");
            }
            if (stack.getItem() == SscAddon.NIGHT_VISION_SHARD) {
                addSplitTooltip(lines, "item.ssc_addon.night_vision_shard.tooltip");
            }
            if (stack.getItem() == SscAddon.ENDER_SHARD) {
                addSplitTooltip(lines, "item.ssc_addon.ender_shard.tooltip");
            }
            if (stack.getItem() == SscAddon.HUNT_SHARD) {
                addSplitTooltip(lines, "item.ssc_addon.hunt_shard.tooltip");
            }
            if (stack.getItem() == SscAddon.SCULK_SHARD) {
                addSplitTooltip(lines, "item.ssc_addon.sculk_shard.tooltip");
            }
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

        // Register predicate for 3D model when held (0.0 = inventory/ground, 1.0 = held)
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "held"), (stack, world, entity, seed) -> 
            entity != null && (entity.getMainHandStack() == stack || entity.getOffHandStack() == stack) ? 1.0F : 0.0F
        );
        
        // Also register "throwing" predicate for trident animation support if needed
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "throwing"), (stack, world, entity, seed) -> 
            entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
            }
            
            /*if (KEY_ALLAY_HEAL.isPressed()) {
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(1); // Key ID 1 for Allay Heal
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_KEY_PRESS, buf);
            }*/
            
            // Add other key checks here
        });

        HudRenderCallback.EVENT.register(new SnowFoxSPManaBar());
        HudRenderCallback.EVENT.register(new AllaySPManaBar());
        
        HandledScreens.register(SscAddon.POTION_BAG_SCREEN_HANDLER, PotionBagScreen::new);
    }
}
