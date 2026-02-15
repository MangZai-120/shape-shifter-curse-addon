package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class EffectEfficiencyReductionPower extends Power {

    public EffectEfficiencyReductionPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    public static PowerFactory createFactory() {
        return new PowerFactory<>(new Identifier("ssc_addon", "effect_efficiency_reduction"),
            new SerializableData(),
            data ->
                (type, entity) -> new EffectEfficiencyReductionPower(type, entity)
        ).allowCondition();
    }
}
