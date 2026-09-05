package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 月尘魔法书键位（jackcooper）。全部默认未绑定（玩家在「控制」设置里自定义）：
 * <ul>
 *   <li>{@link #KEY_SWITCH}：按住时滚轮切换当前选中魔法；</li>
 *   <li>{@link #KEY_CAST}：释放当前选中魔法；</li>
 *   <li>{@link #KEY_DIRECT}[0..6]：直接释放书内第 1~7 槽魔法。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class SpellcastKeybindings {
	public static final String CATEGORY = "key.categories.ssc_addon";

	public static KeyBinding KEY_SWITCH;
	public static KeyBinding KEY_CAST;
	public static final KeyBinding[] KEY_DIRECT = new KeyBinding[7];

	private SpellcastKeybindings() {
	}

	public static void register() {
		KEY_SWITCH = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ssc_addon.spell_switch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		KEY_CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ssc_addon.spell_cast", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		for (int i = 0; i < 7; i++) {
			KEY_DIRECT[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					"key.ssc_addon.spell_cast_" + (i + 1), InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
		}
	}
}
