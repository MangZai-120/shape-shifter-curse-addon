package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.registry.Registry;

public class SscAddonPowers {
	public static void register() {
		Registry.register(ApoliRegistries.POWER_FACTORY, AdaptiveSwimmingPower.createFactory().getSerializerId(), AdaptiveSwimmingPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, TrueInvisibilityAbilityPower.createFactory().getSerializerId(), TrueInvisibilityAbilityPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, TrueInvisibilityDashAbilityPower.createFactory().getSerializerId(), TrueInvisibilityDashAbilityPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, MistFormAbilityPower.createFactory().getSerializerId(), MistFormAbilityPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, BatSonicWaveAbilityPower.createFactory().getSerializerId(), BatSonicWaveAbilityPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, ParasiticFruitSeedPower.createFactory().getSerializerId(), ParasiticFruitSeedPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, ParasiticSporeBombPower.createFactory().getSerializerId(), ParasiticSporeBombPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, FruitBatPlantGrowthPower.createFactory().getSerializerId(), FruitBatPlantGrowthPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, SnowFoxSpFormSpeedPower.createFactory().getSerializerId(), SnowFoxSpFormSpeedPower.createFactory());
		Registry.register(ApoliRegistries.POWER_FACTORY, EffectEfficiencyReductionPower.createFactory().getSerializerId(), EffectEfficiencyReductionPower.createFactory());
	}
}
