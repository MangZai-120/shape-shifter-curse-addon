package net.jackcooper.shapeShifterCurseAddon.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * SSCA 附属实体注册（jackcooper 署名）。主类 onInitialize 调用 {@link #init()} 触发类加载完成注册。
 */
public final class RegAddonEntities {

	private RegAddonEntities() {}

	/** 魔法蜘蛛「攻击模式」蓄力蛛丝弹：命中方块 / 实体后按蓄力档在半径内贴面铺减速蛛网（web_membrane）。 */
	public static final EntityType<WebMembraneBullet> WEB_MEMBRANE_BULLET = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "web_membrane_bullet"),
			FabricEntityTypeBuilder.<WebMembraneBullet>create(SpawnGroup.MISC, WebMembraneBullet::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeChunks(10)
					.trackedUpdateRate(1)
					.build());

	public static void init() {
		// 触发静态初始化即完成注册
	}
}
