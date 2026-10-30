package net.jackcooper.shapeShifterCurseAddon.util;

import java.util.Objects;
import virtuoel.pehkui.api.ScaleData;

/** Narrow no-op checks for SSC's instant, per-tick step/crawl scale assignments. */
public final class StableSscScaleWrites {
    private StableSscScaleWrites() {}

    public static boolean skipScale(ScaleData data, float desired) {
        // Other mods may give subclasses extra setter behavior or a different state model.
        return data.getClass() == ScaleData.class && Float.isFinite(desired)
                && data.getBaseScale() == desired && data.getPrevBaseScale() == desired
                && data.getInitialScale() == desired && data.getTargetScale() == desired;
    }

    public static boolean skipPersistence(ScaleData data, Boolean desired) {
        return data.getClass() == ScaleData.class && Objects.equals(data.getPersistence(), desired);
    }
}
