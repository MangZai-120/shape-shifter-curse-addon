package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConfigChangeManager {
    private static final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();

    private ConfigChangeManager() {
    }

    public static void register(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public static void notifyChange(SSCAddonConfig config) {
        for (ConfigChangeListener listener : listeners) {
            listener.onConfigChanged(config);
        }
    }
}
