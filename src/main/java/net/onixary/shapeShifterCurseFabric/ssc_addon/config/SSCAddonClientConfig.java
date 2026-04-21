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
}
