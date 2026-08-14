package net.jackcooper.shapeShifterCurseAddon.client.model;

import software.bernie.geckolib.model.GeoModel;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.entity.AxolotlShifterEntity;

/**
 * 美西螈幻形者模型 - 复刻 SSC 原版美西螈形态（axolotl_3）的 geo 模型与贴图，
 * 资源已复制进附属包（ssc_addon 命名空间），不依赖运行期引用原版资源。
 */
public class AxolotlShifterModel extends GeoModel<AxolotlShifterEntity> {

	@Override
	public Identifier getModelResource(AxolotlShifterEntity entity) {
		// 原版美西螈形态 geo（人形直立布局：bipedHead + bipedBody + tail_0~3 + 四肢）
		return new Identifier("ssc_addon", "geo/axolotl_shifter.geo.json");
	}

	@Override
	public Identifier getTextureResource(AxolotlShifterEntity entity) {
		return new Identifier("ssc_addon", "textures/entity/axolotl_shifter.png");
	}

	@Override
	public Identifier getAnimationResource(AxolotlShifterEntity entity) {
		// 附属包占位动画（骨骼变换由渲染器程序化驱动，动画仅提供控制器挂载点）
		return new Identifier("ssc_addon", "animations/axolotl_shifter.animation.json");
	}
}
