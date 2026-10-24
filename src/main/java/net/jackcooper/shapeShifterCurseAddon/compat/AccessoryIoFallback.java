package net.jackcooper.shapeShifterCurseAddon.compat;

import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.onixary.shapeShifterCurseFabric.util.Accessory.DefaultAccessory;

/** Only SSC's unavailable default Curios adapter may fall back to Trinkets. */
public final class AccessoryIoFallback {
    private AccessoryIoFallback() { }

    public static boolean restoreTrinketsForStubCurios(boolean curiosImplementationLoaded) {
        if (curiosImplementationLoaded || !"curios".equals(AccessoryUtils.nowAccessoryModID)
                || AccessoryUtils.nowAccessoryMod == null
                || AccessoryUtils.nowAccessoryMod.getClass().getEnclosingClass() != DefaultAccessory.class) return false;
        var trinkets = AccessoryUtils.activeAccessoryModInterfaces.get("trinkets");
        if (trinkets == null) return false;
        AccessoryUtils.nowAccessoryMod = trinkets;
        AccessoryUtils.nowAccessoryModID = "trinkets";
        return true;
    }
}
