package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "ssc-addon")
public class SSCAddonConfig implements ConfigData {
    
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public BookLanguage bookLanguage = BookLanguage.CHINESE;

    public enum BookLanguage {
        CHINESE,
        ENGLISH
    }
}
