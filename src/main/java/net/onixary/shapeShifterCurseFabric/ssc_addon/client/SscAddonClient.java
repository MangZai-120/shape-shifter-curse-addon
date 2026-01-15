package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SscAddonClient implements ClientModInitializer {
    public static final String CATEGORY = "key.categories.ssc_addon";
    
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

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(KEY_FOX_FIRE);
        KeyBindingHelper.registerKeyBinding(KEY_BLUE_RING);
    }
}
