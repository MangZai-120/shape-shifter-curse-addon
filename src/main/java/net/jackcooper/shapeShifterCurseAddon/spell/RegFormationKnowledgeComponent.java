package net.jackcooper.shapeShifterCurseAddon.spell;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

/**
 * 玩家法术知识组件的 CCA 注册入口（jackcooper）。
 * 通过 fabric.mod.json 的 "cardinal-components-entity" entrypoint 加载。
 */
public class RegFormationKnowledgeComponent implements EntityComponentInitializer {
	public static final ComponentKey<FormationKnowledgeComponent> FORMATION_KNOWLEDGE =
			ComponentRegistry.getOrCreate(new Identifier("ssc_addon", "formation_knowledge"), FormationKnowledgeComponent.class);

	@Override
	public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
		// 为玩家注册组件，重生时复制数据（与 RegEvolutionComponent 一致）
		registry.registerForPlayers(
				FORMATION_KNOWLEDGE,
				player -> new FormationKnowledgeComponent(),
				RespawnCopyStrategy.ALWAYS_COPY
		);
	}
}
