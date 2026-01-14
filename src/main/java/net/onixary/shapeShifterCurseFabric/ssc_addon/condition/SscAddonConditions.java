package net.onixary.shapeShifterCurseFabric.ssc_addon.condition;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.apoli.util.Comparison;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

public class SscAddonConditions {

    public static void register() {
        register(new ConditionFactory<>(new Identifier("ssc_addon", "has_mana_percent_safe"),
            new SerializableData()
                .add("mana_percent", SerializableDataTypes.DOUBLE)
                .add("comparison", ApoliDataTypes.COMPARISON),
            (data, entity) -> {
                if (!(entity instanceof PlayerEntity player)) return false;
                double requiredPercent = data.getDouble("mana_percent");
                Comparison comparison = data.get("comparison");
                
                double current = ManaUtils.getPlayerMana(player);
                double max = ManaUtils.getPlayerMaxMana(player);
                
                if (max <= 0) return false;
                
                return comparison.compare(current / max, requiredPercent);
            }));
    }

    private static void register(ConditionFactory<Entity> factory) {
        Registry.register(ApoliRegistries.ENTITY_CONDITION, factory.getSerializerId(), factory);
    }
}
