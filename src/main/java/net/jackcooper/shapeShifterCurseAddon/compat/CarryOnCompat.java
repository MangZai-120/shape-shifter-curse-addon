package net.jackcooper.shapeShifterCurseAddon.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/** Optional client bridge. The nested API holder is never loaded without Carry On. */
public final class CarryOnCompat {
    private CarryOnCompat() {}
    public static boolean isInCarryingAnimation(PlayerEntity player) {
        return player != null && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                && FabricLoader.getInstance().isModLoaded("carryon") && Api.isCarrying(player);
    }
    private static final class Api {
        private static boolean isCarrying(PlayerEntity player) {
            var carry = tschipp.carryon.common.carry.CarryOnDataManager.getCarryData(player);
            return carry != null && carry.isCarrying() && !player.isSwimming() && !player.isFallFlying();
        }
    }
}
