package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

/** 固定状态随玩家保存和重生复制，不受 Origins 能力重建影响。 */
public final class RegInstinctAnchorComponent implements EntityComponentInitializer {
    public static final ComponentKey<InstinctAnchorComponent> ANCHOR = ComponentRegistry.getOrCreate(
            new Identifier("ssc_addon", "instinct_anchor"), InstinctAnchorComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(ANCHOR, player -> new InstinctAnchorComponent(), RespawnCopyStrategy.ALWAYS_COPY);
    }
}
