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

	public enum MancianimaTeleportMode {
		RAYCAST,
		PLATFORM
	}
}
