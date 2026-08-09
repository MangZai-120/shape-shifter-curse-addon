package net.jackcooper.shapeShifterCurseAddon.state;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

/**
 * 月织蛛模式开关组件的 CCA 注册入口。
 * 通过 fabric.mod.json 的 "cardinal-components-entity" entrypoint 加载。
 */
public class RegSpiderMoonWeaverStateComponent implements EntityComponentInitializer {
	public static final ComponentKey<SpiderMoonWeaverStateComponent> SPIDER_MOON_WEAVER_STATE =
			ComponentRegistry.getOrCreate(new Identifier("ssc_addon", "spider_moon_weaver_state"), SpiderMoonWeaverStateComponent.class);

	@Override
	public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
		// 为玩家注册组件，重生时复制数据（跨会话 / 跨死亡保留模式选择）
		registry.registerForPlayers(
				SPIDER_MOON_WEAVER_STATE,
				player -> new SpiderMoonWeaverStateComponent(),
				RespawnCopyStrategy.ALWAYS_COPY
		);
	}
}
