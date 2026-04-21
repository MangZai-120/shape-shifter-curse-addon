package net.onixary.shapeShifterCurseFabric.ssc_addon.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// 返回中转菜单：先选择"客户端 / 服务端"再进入对应配置
		return SSCAddonConfigMenuScreen::new;
	}
}
