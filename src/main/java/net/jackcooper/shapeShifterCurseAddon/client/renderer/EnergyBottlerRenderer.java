package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlock;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;

/**
 * 能量装瓶器动态瓶子渲染器（jackcooper）：类炼药台的槽位可视化。
 *
 * <p>同步机制：服务端 BE 每 tick 快照对比槽位空↔非空（{@link EnergyBottlerBlockEntity#tick}），
 * 变化时经 BE 数据包同步全部槽位 ItemStack 到客户端镜像（{@code getClientStack}），
 * 本渲染器据此动态显示/隐藏瓶子——GUI 放取、漏斗、自动合成全部覆盖。
 *
 * <p>两排瓶子均为 3D 真实物品模型（输入=原版玻璃瓶，输出=原版药水瓶 3D 模型）。
 * 锚点修正：FIXED 变换下物品模型以原点为中心（模型空间 y±0.5），故 translate 需额外
 * +0.5×SCALE 使瓶底精确贴底板面（否则陷入地下约 3.2px）。
 * <ul>
 *   <li>输入排（空玻璃瓶）：沿后墙下沿，z=11/16，x=4/8/12，立放原版模型；</li>
 *   <li>输出排（能量瓶）：腔内前部，z=3.75/16，x=4/8/12，立放 3D 药水瓶。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class EnergyBottlerRenderer implements BlockEntityRenderer<EnergyBottlerBlockEntity> {

	/** 输入排 X（三瓶均布，占位块中心）。 */
	private static final float[] IN_X = {4f / 16f, 8f / 16f, 12f / 16f};
	/** 输入排 Z（后墙下沿前方）。 */
	private static final float IN_Z = 11f / 16f;
	/** 输出排 X。 */
	private static final float[] OUT_X = {4f / 16f, 8f / 16f, 12f / 16f};
	/** 输出排 Z（腔内前部）。 */
	private static final float OUT_Z = 3.75f / 16f;
	/** 空腔底板面高度（底板顶面 y=2/16）。 */
	private static final float FLOOR_Y = 2f / 16f;
	/** 瓶缩放。 */
	private static final float SCALE = 0.4f;
	/** FIXED 变换下模型中心到底部的高度（模型空间 0.5 × 缩放）——锚点抬高量使瓶底贴地。 */
	private static final float HALF_SCALE = 0.5f * SCALE;
	/** 底部微抬升（防 z-fighting）。 */
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

		// 按方块朝向绕方块中心 (0.5, 0.5) 旋转（与 blockstate 的 y 旋转一致：east=90/south=180/west=270）
		float facingAngle = switch (bottler.getCachedState().get(EnergyBottlerBlock.FACING)) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		};

		// 输入排：输入槽 0~2（空玻璃瓶，3D 立放，沿后墙下沿）
		for (int i = 0; i < EnergyBottlerBlockEntity.LINES; i++) {
			ItemStack stack = bottler.getClientStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			renderBottle(stack, matrices, vcp, light, overlay, bottler,
					IN_X[i], IN_Z, facingAngle, seedBase + i);
		}

		// 输出排：输出槽 3~5（能量瓶，3D 立放原版药水模型）
		for (int i = 0; i < EnergyBottlerBlockEntity.LINES; i++) {
			ItemStack stack = bottler.getClientStack(EnergyBottlerBlockEntity.LINES + i);
			if (stack.isEmpty()) {
				continue;
			}
			renderBottle(stack, matrices, vcp, light, overlay, bottler,
					OUT_X[i], OUT_Z, facingAngle, seedBase + 16 + i);
		}
	}

	/**
	 * 在北向基准坐标 (x, z) 处立放渲染一个 3D 物品瓶，瓶底精确贴底板面。
	 * 坐标先绕方块中心 (0.5, 0.5) 旋转 facingAngle，再渲染；瓶身再转 180° 使瓶面朝开口方向。
	 */
	private void renderBottle(ItemStack stack, MatrixStack matrices, VertexConsumerProvider vcp,
	                          int light, int overlay, EnergyBottlerBlockEntity bottler,
	                          float x, float z, float facingAngle, int seed) {
		matrices.push();
		if (facingAngle != 0f) {
			matrices.translate(0.5f, 0f, 0.5f);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facingAngle));
			matrices.translate(-0.5f, 0f, -0.5f);
		}
		matrices.translate(x, FLOOR_Y + LIFT + HALF_SCALE, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f)); // 瓶面朝开口（北向基准）
		matrices.scale(SCALE, SCALE, SCALE);
		itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light, overlay,
				matrices, vcp, bottler.getWorld(), seed);
		matrices.pop();
	}

	/** 客户端注册入口：由 {@code RegAddonBlocks.clientInit()} 调用。 */
	@Environment(EnvType.CLIENT)
	public static void register() {
		// 原版注册入口（经 Fabric transitive-access-wideners 放宽为 public，替代已弃用的 Fabric BlockEntityRendererRegistry）
		BlockEntityRendererFactories.register(
				RegAddonBlockEntities.ENERGY_BOTTLER_BE,
				EnergyBottlerRenderer::new);
	}
}
