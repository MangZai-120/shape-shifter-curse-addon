package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.onixary.shapeShifterCurseFabric.integration.origins.OriginsClient;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import org.lwjgl.glfw.GLFW;

/**
 * SSCA 主动技能键位管理（特殊键位系统核心）。
 *
 * <p>原理：Apoli 客户端每 tick 用 {@code getKeyBinding("key.ssc_addon.sp_primary").isPressed()}
 * 判断技能键是否按下（见 ApoliClient.onInitializeClient 的 START_CLIENT_TICK）。该 ID 解析成哪个
 * KeyBinding 完全由我们通过 {@code ApoliClient.registerPowerKeybinding} 决定。
 *
 * <p>因此这里提供两个「虚拟 KeyBinding」并重写其 {@link KeyBinding#isPressed()} 动态返回：
 * <ul>
 *   <li>当前形态<b>启用</b>了自定义键位 → 返回该形态自定义键的物理按下状态；</li>
 *   <li>否则（<b>同步 SSC</b>）→ 返回 SSC 原版 G 键（primary_active / secondary_active）的状态。</li>
 * </ul>
 * 这样启用自定义后 Apoli 读不到 G、G 在该形态自动失效，只认自定义键；未启用则照旧跟随 G。
 * 全程零侵入：不 mixin Apoli、不改任何 powers JSON、技能执行链（onUse + 发包）由 Apoli 原样处理。
 */
public class SscAddonKeybindings {
	public static final String CATEGORY = "key.categories.ssc_addon";

	private static KeyBinding virtualPrimary;
	private static KeyBinding virtualSecondary;

	private SscAddonKeybindings() {
		// This utility class should not be instantiated
	}

	/** 取得 SP 主动技能键位（虚拟键，按形态路由 自定义键 / SSC G 键）。 */
	public static KeyBinding getPrimaryKey() {
		if (virtualPrimary == null) {
			virtualPrimary = new VirtualSkillKey("key.ssc_addon.virtual_sp_primary", true);
		}
		return virtualPrimary;
	}

	/** 取得 SP 副技能键位（虚拟键，按形态路由 自定义键 / SSC G 键）。 */
	public static KeyBinding getSecondaryKey() {
		if (virtualSecondary == null) {
			virtualSecondary = new VirtualSkillKey("key.ssc_addon.virtual_sp_secondary", false);
		}
		return virtualSecondary;
	}

	/** 兼容旧调用：现在改用虚拟键，无需向 GameOptions 注册。 */
	public static void register() {
		getPrimaryKey();
		getSecondaryKey();
	}

	/** 取得当前客户端玩家所处形态的 path（无则 null）。 */
	private static String getCurrentFormPath() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return null;
		}
		try {
			PlayerFormBase form = RegPlayerFormComponent.PLAYER_FORM.get(mc.player).getCurrentForm();
			if (form == null || form.FormID == null) {
				return null;
			}
			return form.FormID.getPath();
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 计算虚拟技能键当前是否按下。
	 *
	 * @param primary true=主技能，false=副技能
	 */
	public static boolean computePressed(boolean primary) {
		// 任意 GUI 界面打开时不触发技能（与原版键位一致）。
		// 自定义键用裸 GLFW 检测，不像原版键位会在开界面时被 unpress，
		// 故必须在此显式拦截，否则在「特殊键位设置」等界面内按绑定键会误放技能。
		if (MinecraftClient.getInstance().currentScreen != null) {
			return false;
		}
		String formPath = getCurrentFormPath();
		if (formPath != null) {
			SSCAddonClientConfig cfg = SSCAddonConfig.client();
			SSCAddonClientConfig.FormKeybind entry = cfg.formKeybinds.get(formPath);
			if (entry != null && entry.enabled) {
				// 启用自定义：只认自定义键（G 在此形态被自动屏蔽）
				return isPhysicalKeyDown(primary ? entry.primaryKey : entry.secondaryKey);
			}
		}
		// 同步 SSC：跟随原版 G 键
		KeyBinding sscKey = primary
				? OriginsClient.usePrimaryActivePowerKeybind
				: OriginsClient.useSecondaryActivePowerKeybind;
		return sscKey != null && sscKey.isPressed();
	}

	/** 判断给定 InputUtil 翻译键（键盘/鼠标）当前是否被物理按住。 */
	private static boolean isPhysicalKeyDown(String translationKey) {
		if (translationKey == null
				|| translationKey.isEmpty()
				|| "key.keyboard.unknown".equals(translationKey)) {
			return false;
		}
		try {
			InputUtil.Key key = InputUtil.fromTranslationKey(translationKey);
			long window = MinecraftClient.getInstance().getWindow().getHandle();
			if (key.getCategory() == InputUtil.Type.KEYSYM) {
				return InputUtil.isKeyPressed(window, key.getCode());
			}
			if (key.getCategory() == InputUtil.Type.MOUSE) {
				return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 虚拟键位：不绑定任何物理键，isPressed 动态计算。 */
	private static final class VirtualSkillKey extends KeyBinding {
		private final boolean primary;

		private VirtualSkillKey(String translationKey, boolean primary) {
			super(translationKey, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY);
			this.primary = primary;
		}

		@Override
		public boolean isPressed() {
			return computePressed(this.primary);
		}
	}
}
