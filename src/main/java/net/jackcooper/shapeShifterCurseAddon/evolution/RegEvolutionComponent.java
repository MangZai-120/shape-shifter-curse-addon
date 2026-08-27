package net.jackcooper.shapeShifterCurseAddon.evolution;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

/**
 * SSCA 进化加点系统 - 进化数据组件的 CCA 注册入口。
 * 通过 fabric.mod.json 的 "cardinal-components-entity" entrypoint 加载。
 */
public class RegEvolutionComponent implements EntityComponentInitializer {
    public static final ComponentKey<EvolutionComponent> EVOLUTION =
            ComponentRegistry.getOrCreate(new Identifier("ssc_addon", "evolution"), EvolutionComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // 为玩家注册组件，重生时复制数据（与原版 RegPlayerFormComponent 一致）
        registry.registerForPlayers(
                EVOLUTION,
                player -> new EvolutionComponent(),
                RespawnCopyStrategy.ALWAYS_COPY
        );
    }
}
