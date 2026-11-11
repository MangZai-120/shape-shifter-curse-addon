package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

public final class RegParasiticFruitStateComponent implements EntityComponentInitializer {
    public static final ComponentKey<ParasiticFruitStateComponent> STATE = ComponentRegistry.getOrCreate(
            new Identifier("ssc_addon", "parasitic_fruit_selection"), ParasiticFruitStateComponent.class);
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(STATE, ParasiticFruitStateComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
