package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.entity.model.TridentEntityModel;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.WaterSpearEntityRenderer;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.resource.language.I18n;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana.SnowFoxSPManaBar;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
    
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
        System.out.println("SSC ADDON DEBUG: Registering Client KeyBindings...");
        
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
        });

        EntityRendererRegistry.register(SscAddon.WATER_SPEAR_ENTITY, WaterSpearEntityRenderer::new);

        // Register predicate for 3D model when held (0.0 = inventory/ground, 1.0 = held)
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "held"), (stack, world, entity, seed) -> 
            entity != null && (entity.getMainHandStack() == stack || entity.getOffHandStack() == stack) ? 1.0F : 0.0F
        );
        
        // Also register "throwing" predicate for trident animation support if needed
        ModelPredicateProviderRegistry.register(SscAddon.WATER_SPEAR, new Identifier("ssc_addon", "throwing"), (stack, world, entity, seed) -> 
            entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            /*if (KEY_ALLAY_HEAL.isPressed()) {
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(1); // Key ID 1 for Allay Heal
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_KEY_PRESS, buf);
            }*/
            
            // Add other key checks here
        });

        HudRenderCallback.EVENT.register(new SnowFoxSPManaBar());
    }
}
