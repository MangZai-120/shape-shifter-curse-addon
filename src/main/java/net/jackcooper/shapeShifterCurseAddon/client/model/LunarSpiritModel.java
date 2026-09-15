package net.jackcooper.shapeShifterCurseAddon.client.model;

import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 月灵模型（jackcooper）：引用 Blockbench 生成的一套资源（my_addon 命名空间）——
 * geo（球身 + 四片翅膀 + 月冠，7 骨骼）+ 三段动画（summon/fly/idle）+ 三色变体贴图。
 * 贴图按实体 VARIANT 切换（粉/蓝/绿）。
 */
public class LunarSpiritModel extends GeoModel<LunarSpiritEntity> {

	@Override
	public Identifier getModelResource(LunarSpiritEntity entity) {
		return new Identifier("my_addon", "geo/moon_spirit.geo.json");
	}

	@Override
	public Identifier getTextureResource(LunarSpiritEntity entity) {
		// 三色变体贴图（0 粉 / 1 蓝 / 2 绿）
		return switch (entity.getVariant()) {
			case LunarSpiritEntity.VARIANT_PINK -> new Identifier("my_addon", "textures/entity/moon_spirit_pink.png");
			case LunarSpiritEntity.VARIANT_CYAN -> new Identifier("my_addon", "textures/entity/moon_spirit_cyan.png");
			default -> new Identifier("my_addon", "textures/entity/moon_spirit_green.png");
		};
	}

	@Override
	public Identifier getAnimationResource(LunarSpiritEntity entity) {
		return new Identifier("my_addon", "animations/moon_spirit.animation.json");
	}
}
