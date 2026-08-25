package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;

/**
 * 能量装瓶器动态瓶子渲染器（jackcooper）：类炼药台的槽位可视化。
 *
 * <p>同步机制：服务端 BE 每 tick 快照对比槽位空↔非空（{@link EnergyBottlerBlockEntity#tick}），
 * 变化时经 BE 数据包同步全部槽位 ItemStack 到客户端镜像（{@code getClientStack}），
 * 本渲染器据此动态显示/隐藏瓶子——GUI 放取、漏斗、自动合成全部覆盖。
 *
 * <p>渲染方式：营地火范式（{@code ItemRenderer.renderItem} + FIXED 变换），直接渲染真实
 * ItemStack 模型（空玻璃瓶/能量药水瓶原版模型）。瓶子立放在前部凹陷空腔的底板上
 * （模型空腔 = x2~14, y2~14, z0~8，底板面 y=2/16，两排各三瓶，瓶口朝上前缘可见）：
 * <ul>
 *   <li>前排（腔内北半侧）：输入槽 0~2 的空玻璃瓶；</li>
 *   <li>后排（腔内南半侧）：输出槽 3~5 的能量药水瓶。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class EnergyBottlerRenderer implements BlockEntityRenderer<EnergyBottlerBlockEntity> {

	/** 前排 X 坐标（三瓶均布于空腔 x2~14 内）。 */
	private static final float[] FRONT_X = {0.25f, 0.5f, 0.75f};
	/** 后排 X 坐标（三瓶均布）。 */
	private static final float[] BACK_X = {0.25f, 0.5f, 0.75f};
	/** 前排 Z（空腔北半侧）。 */
	private static final float FRONT_Z = 0.25f;
	/** 后排 Z（贴近后墙，后墙内面 z=8/16）。 */
	private static final float BACK_Z = 0.40625f;
	/** 空腔底板面高度（底板顶面 y=2/16）。 */
	private static final float FLOOR_Y = 2f / 16f;
	/** 瓶缩放（瓶高约 0.4，腔高 0.75，不穿顶）。 */
	private static final float SCALE = 0.4f;
	/** 底部微抬升（离底板面防 z-fighting）。 */
	private static final float LIFT = 0.005f;

	private final ItemRenderer itemRenderer;

	public EnergyBottlerRenderer(BlockEntityRendererFactory.Context ctx) {
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(EnergyBottlerBlockEntity bottler, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider vcp, int light, int overlay) {
		long seed = bottler.getPos().asLong();
		int seedBase = (int) (seed & 0x7FFFFFFFL);

		// 前排：输入槽 0~2（空玻璃瓶，立放，朝向前缘开口）
		for (int i = 0; i < EnergyBottlerBlockEntity.LINES; i++) {
			ItemStack stack = bottler.getClientStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			matrices.push();
			matrices.translate(FRONT_X[i], FLOOR_Y + LIFT, FRONT_Z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f)); // 瓶面朝北（开口方向）
			matrices.scale(SCALE, SCALE, SCALE);
			itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light, overlay,
					matrices, vcp, bottler.getWorld(), seedBase + i);
			matrices.pop();
		}

		// 后排：输出槽 3~5（能量药水瓶，立放）
		for (int i = 0; i < EnergyBottlerBlockEntity.LINES; i++) {
			ItemStack stack = bottler.getClientStack(EnergyBottlerBlockEntity.LINES + i);
			if (stack.isEmpty()) {
				continue;
			}
			matrices.push();
			matrices.translate(BACK_X[i], FLOOR_Y + LIFT, BACK_Z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
			matrices.scale(SCALE, SCALE, SCALE);
			itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light, overlay,
					matrices, vcp, bottler.getWorld(), seedBase + 16 + i);
			matrices.pop();
		}
	}

	/** 客户端注册入口：由 {@code RegAddonBlocks.clientInit()} 调用。 */
	@Environment(EnvType.CLIENT)
	public static void register() {
		BlockEntityRendererRegistry.register(
				RegAddonBlockEntities.ENERGY_BOTTLER_BE,
				EnergyBottlerRenderer::new);
	}
}
