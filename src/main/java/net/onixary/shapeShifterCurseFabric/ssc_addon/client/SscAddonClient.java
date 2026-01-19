package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.TridentEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
    
    private TridentEntityModel tridentModel;
    
    // SP Allay Keys
    public static final KeyBinding KEY_ALLAY_HEAL = new KeyBinding(
        "key.ssc_addon.allay_heal", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_R, 
        CATEGORY
    );

    
    // SP Familiar Fox Keys
    public static final KeyBinding KEY_FOX_FIRE = new KeyBinding(
        "key.ssc_addon.fox_fire", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_R, 
        CATEGORY
    );
    
    public static final KeyBinding KEY_BLUE_RING = new KeyBinding(
        "key.ssc_addon.blue_ring", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_G, 
        CATEGORY
    );
    
    // SP Axolotl Keys
    public static final KeyBinding KEY_VORTEX = new KeyBinding(
        "key.ssc_addon.vortex", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_G, 
        CATEGORY
    );

    public static final KeyBinding KEY_PLAY_DEAD = new KeyBinding(
        "key.ssc_addon.play_dead", 
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_Z, 
        CATEGORY
    );

    @Override
    public void onInitializeClient() {
        System.out.println("SSC ADDON DEBUG: Registering Client KeyBindings...");
        
        // SP Allay Keys
        KeyBindingHelper.registerKeyBinding(KEY_ALLAY_HEAL);

        // SP Familiar Fox Keys
        KeyBindingHelper.registerKeyBinding(KEY_FOX_FIRE);
        KeyBindingHelper.registerKeyBinding(KEY_BLUE_RING);
        
        // SP Axolotl Keys
        KeyBindingHelper.registerKeyBinding(KEY_VORTEX);
        KeyBindingHelper.registerKeyBinding(KEY_PLAY_DEAD);

        // Register Water Spear Item Renderer to look like a Trident
        BuiltinItemRendererRegistry.INSTANCE.register(SscAddon.WATER_SPEAR, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            if (tridentModel == null) {
                // Initialize model only when client is fully ready and has loaded models
                tridentModel = new TridentEntityModel(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.TRIDENT));
            }
            matrices.push();
            matrices.scale(1.0F, -1.0F, -1.0F);
            VertexConsumer vertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumers, tridentModel.getLayer(new Identifier("textures/entity/trident.png")), false, stack.hasGlint());
            tridentModel.render(matrices, vertexConsumer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            matrices.pop();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (KEY_ALLAY_HEAL.isPressed()) {
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(1); // Key ID 1 for Allay Heal
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_KEY_PRESS, buf);
            }
            
            // Add other key checks here
        });
    }
}
