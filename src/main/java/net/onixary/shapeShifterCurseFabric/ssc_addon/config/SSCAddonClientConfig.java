package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * 客户端配置 - 仅影响本地显示效果，与服务器隔离。
 * 玩家可在自己客户端任意修改而不影响他人/服务器。
 */
@Config(name = "ssc_addon_client")
public class SSCAddonClientConfig implements ConfigData {

	@ConfigEntry.Gui.Tooltip
	public boolean showCdBar = true;

	@ConfigEntry.Gui.Tooltip
	public boolean showCdSeconds = true;

	// ===== 技能 CD 条位置（与原版本能/能量条一致的 1-9 九宫格锚点 + X/Y 偏移）=====
	// 不在 GUI 直接展示（由 BarPositionEditorScreen 可视化编辑），默认对齐原快捷栏左右两侧。
	/** CD 条锚点类型（1-9 九宫格），默认 8=下中。 */
	@ConfigEntry.Gui.Excluded
	public int cdBarPosType = 8;
	/** 主技能 CD 条（左侧）X 偏移：相对锚点的额外平移。 */
	@ConfigEntry.Gui.Excluded
	public int cdBarPosOffsetX = -98;
	/** CD 条 Y 偏移：相对锚点的额外平移。 */
	@ConfigEntry.Gui.Excluded
	public int cdBarPosOffsetY = -21;

	/**
	 * 契灵 - 次要技能瞬移模式
	 * RAYCAST: 直接朝着准星方向传送（按下立即传送，碰墙停止）
	 * PLATFORM: 平台锁定模式（按下显示落点预览，松开后传送到锁定平台）
	 */
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public MancianimaTeleportMode mancianimaTeleportMode = MancianimaTeleportMode.RAYCAST;

	/**
	 * 是否启用 SSCA 自己的颜色编辑器与 20 槽预设管理。
	 * 开启后会在打开原版 SSC 颜色编辑菜单时自动替换为 SSCA 的 AdvancedColorScreen。
	 * 关闭时所有 SSCA 颜色拦截/UI 入口均停用，使用原版 SSC 颜色编辑功能；已保存数据保留。
	 */
	@ConfigEntry.Gui.Tooltip
	public boolean enableColorEditor = false;

	/**
	 * 已看过颜色编辑器新手教程的存档/服务器 ID 列表（按存档看一遍）。
	 * 不在 GUI 中展示，由教程逻辑自动维护。
	 */
	@ConfigEntry.Gui.Excluded
	public java.util.List<String> colorTutorialSeenSaves = new java.util.ArrayList<>();

	/**
	 * 按形态的「特殊键位」配置（key = 形态 ID 的 path，如 "snow_fox_sp"）。
	 * 由 SSCA 自定义的「特殊键位设置」二级菜单维护，不在 AutoConfig GUI 中展示。
	 * 某形态启用后，其主/副技能触发键从「同步 SSC 的 G 键」改为自定义按键。
	 */
	@ConfigEntry.Gui.Excluded
	public java.util.Map<String, FormKeybind> formKeybinds = new java.util.HashMap<>();

	public enum MancianimaTeleportMode {
		RAYCAST,
		PLATFORM
	}

	/** 单个形态的特殊键位设置：是否启用 + 主/副技能自定义键（InputUtil 翻译键，如 "key.keyboard.f"）。 */
	public static class FormKeybind {
		public boolean enabled = false;
		public String primaryKey = "key.keyboard.unknown";
		public String secondaryKey = "key.keyboard.unknown";

		public FormKeybind() {
		}
	}
}
