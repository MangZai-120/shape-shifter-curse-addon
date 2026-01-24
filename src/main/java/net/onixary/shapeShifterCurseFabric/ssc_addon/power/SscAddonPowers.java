package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.registry.Registry;

public class SscAddonPowers {
    public static void register() {
         Registry.register(ApoliRegistries.POWER_FACTORY, AdaptiveSwimmingPower.createFactory().getSerializerId(), AdaptiveSwimmingPower.createFactory());
         Registry.register(ApoliRegistries.POWER_FACTORY, TrueInvisibilityAbilityPower.createFactory().getSerializerId(), TrueInvisibilityAbilityPower.createFactory());
         Registry.register(ApoliRegistries.POWER_FACTORY, TrueInvisibilityDashAbiltyPower.createFactory().getSerializerId(), TrueInvisibilityDashAbiltyPower.createFactory());
    }
}
