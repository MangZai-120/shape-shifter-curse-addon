package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.minecraft.client.option.KeyBinding;
import net.onixary.shapeShifterCurseFabric.integration.origins.OriginsClient;

/**
 * SSCA 主动技能键位管理：复用 SSC 原版 OriginsClient 中已注册的
 * usePrimaryActivePowerKeybind / useSecondaryActivePowerKeybind，
 * 不再单独注册 sp_primary / sp_secondary，避免与 SSC 在 G 键上发生
 * KeyBinding.KEY_TO_BINDINGS 一对一冲突（导致按键互相覆盖、技能失效）。
 *
 * Apoli 端仍以 "key.ssc_addon.sp_primary" / "key.ssc_addon.sp_secondary"
 * 作为 power keybinding ID，因此所有 powers JSON 无需改动。
 */
public class SscAddonKeybindings {
	public static final String CATEGORY = "key.categories.ssc_addon";

	private SscAddonKeybindings() {
		// This utility class should not be instantiated
	}

	/**
	 * 取得 SP 主动技能键位（实际为 SSC 原版 primary_active 键位对象）。
	 */
	public static KeyBinding getPrimaryKey() {
		return OriginsClient.usePrimaryActivePowerKeybind;
	}

	/**
	 * 取得 SP 副技能键位（实际为 SSC 原版 secondary_active 键位对象）。
	 */
	public static KeyBinding getSecondaryKey() {
		return OriginsClient.useSecondaryActivePowerKeybind;
	}

	/**
	 * 兼容旧调用：现在不再注册新键位，复用 SSC 已注册的键位。
	 */
	public static void register() {
		// no-op：SSC 原版的 OriginsClient.onInitializeClient 已注册
		// usePrimaryActivePowerKeybind / useSecondaryActivePowerKeybind
	}
}
