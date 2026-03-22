package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class AdaptiveSwimmingPower extends Power {
    private final float acceleration;
    private final float friction;

    public AdaptiveSwimmingPower(PowerType<?> type, LivingEntity entity, float acceleration, float friction) {
        super(type, entity);
        this.acceleration = acceleration;
        this.friction = friction;
    }
    
    public float getAcceleration() { return acceleration; }
    public float getFriction() { return friction; }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(
            new Identifier("my_addon", "adaptive_swimming"),
            new SerializableData()
                .add("acceleration", SerializableDataTypes.FLOAT, 0.02f)
                .add("friction", SerializableDataTypes.FLOAT, 0.9f),
            data -> (type, entity) -> new AdaptiveSwimmingPower(type, entity, 
                data.getFloat("acceleration"), 
                data.getFloat("friction"))
        ).allowCondition();
    }
}
