package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.client.model.LunarSpiritModel;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.layer.LunarSpiritOverlayLayer;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 月灵渲染器（jackcooper）：Blockbench geo 模型渲染
 * （球状身体 + 四片半透明翅膀 + 月冠，7 骨骼）。
 *
 * <p>三段 GeckoLib 动画（summon 出生展开 → fly 飞行 → idle 悬浮振翅）由实体控制器驱动，
 * 三色变体贴图经 DataTracker 同步（多人客机一致），发光 overlay 走加法发光层
 * （{@link LunarSpiritOverlayLayer}）。</p>
 *
 * <p>geo 坐标：模型整体 {@code withScale(0.6)} 缩放到约悦灵体量；
 * 飞行朝向由实体 tick 里设置的 yaw 驱动（GeoEntityRenderer 自动应用 bodyYaw）。</p>
 */
public class LunarSpiritRenderer extends GeoEntityRenderer<LunarSpiritEntity> {

	public LunarSpiritRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new LunarSpiritModel());
		this.shadowRadius = 0.15f;
		// geo 模型 1px=1 格太大（身体直径 10px），整体缩到悦灵体量
		this.withScale(0.6f);
		// 发光 overlay 层（月纹/翅脉自发光，黑暗中可见）
		addRenderLayer(new LunarSpiritOverlayLayer(this));
	}
}
