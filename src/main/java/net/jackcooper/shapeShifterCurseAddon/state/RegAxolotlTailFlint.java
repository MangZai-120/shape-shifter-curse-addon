package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.jackcooper.shapeShifterCurseAddon.ability.AxolotlTailFlint;

public final class RegAxolotlTailFlint implements EntityComponentInitializer {
    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(AxolotlTailFlint.STATE, player -> new AxolotlTailFlint(),
                RespawnCopyStrategy.INVENTORY);
    }
}
