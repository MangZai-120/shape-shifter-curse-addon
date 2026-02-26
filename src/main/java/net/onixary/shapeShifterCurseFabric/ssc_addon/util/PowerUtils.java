package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class PowerUtils {
    private PowerUtils() {
    }

    public static int getResourceValue(ServerPlayerEntity player, Identifier resourceId) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
        }
        return 0;
    }

    public static void setResourceValue(ServerPlayerEntity player, Identifier resourceId, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
            }
        } catch (Exception e) {
        }
    }

    public static void setResourceValueClamped(ServerPlayerEntity player, Identifier resourceId, int value, int min, int max) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int clampedValue = Math.max(min, Math.min(max, value));
                variablePower.setValue(clampedValue);
            }
        } catch (Exception e) {
        }
    }

    public static void changeResourceValue(ServerPlayerEntity player, Identifier resourceId, int change) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
            }
        } catch (Exception e) {
        }
    }

    public static void syncPower(ServerPlayerEntity player, Identifier powerId) {
        try {
            PowerHolderComponent.sync(player);
        } catch (Exception e) {
        }
    }

    public static void setResourceValueAndSync(ServerPlayerEntity player, Identifier resourceId, int value) {
        setResourceValue(player, resourceId, value);
        syncPower(player, resourceId);
    }

    public static void changeResourceValueAndSync(ServerPlayerEntity player, Identifier resourceId, int change) {
        changeResourceValue(player, resourceId, change);
        syncPower(player, resourceId);
    }

    public static boolean hasResource(ServerPlayerEntity player, Identifier resourceId, int required) {
        return getResourceValue(player, resourceId) >= required;
    }
}
