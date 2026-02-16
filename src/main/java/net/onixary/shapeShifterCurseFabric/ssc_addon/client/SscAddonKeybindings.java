package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SscAddonKeybindings {
    public static final String CATEGORY = "key.categories.ssc_addon";

    private SscAddonKeybindings() {
        // This utility class should not be instantiated
    }

    // Replaced specific keys with generic SP keys
    public static final KeyBinding KEY_SP_PRIMARY = new KeyBinding(
        "key.ssc_addon.sp_primary", // Unified Primary
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_G, 
        CATEGORY
    );

    public static final KeyBinding KEY_SP_SECONDARY = new KeyBinding(
        "key.ssc_addon.sp_secondary", // Unified Secondary
        InputUtil.Type.KEYSYM, 
        GLFW.GLFW_KEY_R, 
        CATEGORY
    );

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY_SP_PRIMARY);
        KeyBindingHelper.registerKeyBinding(KEY_SP_SECONDARY);
    }
}
