package net.onixary.shapeShifterCurseFabric.ssc_addon.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(SSCAddonConfig.class, parent).get();
    }
}
