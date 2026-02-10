package net.onixary.shapeShifterCurseFabric.ssc_addon.condition;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.apoli.util.Comparison;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import dev.emi.trinkets.api.TrinketsApi;

public class SscAddonConditions {

    public static void register() {
        register(new ConditionFactory<>(new Identifier("ssc_addon", "has_reverse_thermometer"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof PlayerEntity player) {
                    return TrinketsApi.getTrinketComponent(player).map(component -> 
                        component.isEquipped(Registries.ITEM.get(new Identifier("shape-shifter-curse", "charm_of_reverse_thermometer")))
                    ).orElse(false);
                }
                return false;
            }));

        register(new ConditionFactory<>(new Identifier("ssc_addon", "has_trinket"),
            new SerializableData()
                .add("item", SerializableDataTypes.ITEM),
            (data, entity) -> {
                if (entity instanceof PlayerEntity player) {
                    return TrinketsApi.getTrinketComponent(player).map(component -> 
                        component.isEquipped((net.minecraft.item.Item)data.get("item"))
                    ).orElse(false);
                }
                return false;
            }));

        register(new ConditionFactory<>(new Identifier("ssc_addon", "item_on_cooldown"),
            new SerializableData()
                .add("item", SerializableDataTypes.ITEM),
            (data, entity) -> {
                if (entity instanceof PlayerEntity player) {
                    return player.getItemCooldownManager().isCoolingDown((net.minecraft.item.Item)data.get("item"));
                }
                return false;
            }));

        register(new ConditionFactory<>(new Identifier("ssc_addon", "has_blue_fire_amulet"),
            new SerializableData(),
            (data, entity) -> {
                if (entity instanceof PlayerEntity player) {
                     return TrinketsApi.getTrinketComponent(player).map(component -> 
                         component.isEquipped(SscAddon.BLUE_FIRE_AMULET)
                     ).orElse(false);
                }
                return false;
            }));

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
