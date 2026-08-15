package net.jackcooper.shapeShifterCurseAddon.client.model;

import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.entity.GhostCatEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * 幽灵野猫模型 - 复用食梦魔（form_wild_cat_sp）形态的 geo 模型与贴图
 * （资源本就在附属包 my_addon 命名空间下，直接引用，不复制文件）。
 */
public class GhostCatModel extends GeoModel<GhostCatEntity> {

	@Override
	public Identifier getModelResource(GhostCatEntity entity) {
		// 野猫形态 geo（人形直立布局：bipedHead + bipedBody + tail_0~3 + 四肢 + 耳链）
		return new Identifier("my_addon", "geo/form_wild_cat_sp.geo.json");
	}

	@Override
	public Identifier getTextureResource(GhostCatEntity entity) {
		return new Identifier("my_addon", "textures/form_wild_cat_sp/form_wild_cat_sp.png");
	}

	@Override
	public Identifier getAnimationResource(GhostCatEntity entity) {
		// 附属包占位动画（骨骼变换由渲染器程序化驱动，动画仅提供控制器挂载点）
		return new Identifier("ssc_addon", "animations/axolotl_shifter.animation.json");
	}
}
