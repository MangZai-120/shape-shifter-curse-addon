package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import me.shedaniel.autoconfig.AutoConfig;

/**
 * 配置访问统一入口。
 * 配置已按职责拆分为：
 * - SSCAddonClientConfig：仅本地显示效果，与服务器隔离
 * - SSCAddonServerConfig：玩法平衡 / 白名单总开关 等服务端权威设定
 */
public final class SSCAddonConfig {

private SSCAddonConfig() {
throw new UnsupportedOperationException("Utility class");
}

public static SSCAddonClientConfig client() {
return AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();
}

public static SSCAddonServerConfig server() {
return AutoConfig.getConfigHolder(SSCAddonServerConfig.class).getConfig();
}
}