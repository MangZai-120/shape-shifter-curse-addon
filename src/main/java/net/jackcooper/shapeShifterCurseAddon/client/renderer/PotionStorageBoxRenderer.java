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
import net.jackcooper.shapeShifterCurseAddon.block.PotionStorageBoxBlock;
import net.jackcooper.shapeShifterCurseAddon.block.PotionStorageBoxBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;

/**
 * 储药柜动态药水瓶渲染器（jackcooper）：按总装填档位在柜内展示药水瓶。
 *
 * <p>展示规则：柜内总瓶数每 12.5%（满柜 8槽×8=64 瓶，即每 8 瓶）点亮一个展示位，
 * 共 8 档——1~8 瓶 1 个、9~16 瓶 2 个……57~64 瓶 8 个（有任何瓶子时至少 1 个，见
 * {@link PotionStorageBoxBlockEntity#displayTier}）。同步触发同样按档位对比，
 * 跨档才发 BE 数据包（柜内瓶数变化但未跨档不刷包）。
 *
 * <p>展示位布局（北向基准，用户 Blockbench 参考定稿）：2×2 四角 × 两层——
 * 下层（y=2）贴柜底，上层（y=10）在下层正上方 8px。下层先亮、再上层，
 * 前左→前右→后左→后右逐个点亮。瓶子为 3D 真实药水物品模型，随方块 FACING 旋转。
 */
@Environment(EnvType.CLIENT)
public class PotionStorageBoxRenderer implements BlockEntityRenderer<PotionStorageBoxBlockEntity> {

	/** 四角 XZ 坐标（北向基准）：前左/前右/后左/后右（用户参考文件占位块中心）。 */
	private static final float[][] CORNERS = {
			{5f / 16f, 4.75f / 16f},   // 前左
			{11f / 16f, 4.75f / 16f},  // 前右
			{5f / 16f, 11f / 16f},     // 后左
			{11f / 16f, 11f / 16f},    // 后右
	};
	/** 下层底面高度（柜底板顶面 y=2/16）。 */
	private static final float FLOOR_Y = 2f / 16f;
	/** 上层底面高度（用户校准：下层上方 6px）。 */
	private static final float UPPER_Y = 8f / 16f;
	/** 瓶缩放（药水瓶 3D 模型，0.35）。 */
	private static final float SCALE = 0.35f;
	/** FIXED 变换下模型中心到底部的高度（模型空间 0.5 × 缩放）——锚点抬高量使瓶底贴板。 */
	private static final float HALF_SCALE = 0.5f * SCALE;
	/** 底部微抬升（防 z-fighting）。 */
	private static final float LIFT = 0.005f;

	private final ItemRenderer itemRenderer;

	public PotionStorageBoxRenderer(BlockEntityRendererFactory.Context ctx) {
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(PotionStorageBoxBlockEntity box, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider vcp, int light, int overlay) {
		// 展示档位：0~4 个瓶子（用客户端镜像的槽位数据按同一公式计算，与服务端判定一致）
		int tier = PotionStorageBoxBlockEntity.displayTier(
				PotionStorageBoxBlockEntity.totalBottles(
						java.util.stream.IntStream.range(0, PotionStorageBoxBlockEntity.SLOT_COUNT)
								.mapToObj(box::getClientStack)
								.toList()));
		if (tier <= 0) {
			return; // 空柜
		}

		// 用第一瓶非空的镜像作展示模型（能量药水瓶）
		ItemStack displayStack = ItemStack.EMPTY;
		for (int i = 0; i < PotionStorageBoxBlockEntity.SLOT_COUNT; i++) {
			if (!box.getClientStack(i).isEmpty()) {
				displayStack = box.getClientStack(i);
				break;
			}
		}
		if (displayStack.isEmpty()) {
			return;
		}

		// 按方块朝向绕方块中心 (0.5, 0.5) 旋转（与 blockstate 的 y 旋转一致）
		float facingAngle = switch (box.getCachedState().get(PotionStorageBoxBlock.FACING)) {
			case EAST -> 90f;
			case SOUTH -> 180f;
			case WEST -> 270f;
			default -> 0f;
		};
		long seed = box.getPos().asLong();
		int seedBase = (int) (seed & 0x7FFFFFFFL);

		for (int i = 0; i < tier && i < 8; i++) {
			// 前 4 档点亮下层四角，后 4 档点亮上层四角（同角落顺序）
			float[] xz = CORNERS[i % 4];
			float baseY = i < 4 ? FLOOR_Y : UPPER_Y;
			matrices.push();
			if (facingAngle != 0f) {
				matrices.translate(0.5f, 0f, 0.5f);
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facingAngle));
				matrices.translate(-0.5f, 0f, -0.5f);
			}
			matrices.translate(xz[0], baseY + LIFT + HALF_SCALE, xz[1]);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f)); // 瓶面朝门（开口方向）
			matrices.scale(SCALE, SCALE, SCALE);
			itemRenderer.renderItem(displayStack, ModelTransformationMode.FIXED, light, overlay,
					matrices, vcp, box.getWorld(), seedBase + i);
			matrices.pop();
		}
	}

	/** 客户端注册入口：由 {@code RegAddonBlocks.clientInit()} 调用。 */
	@Environment(EnvType.CLIENT)
	public static void register() {
		// 原版注册入口（经 Fabric transitive-access-wideners 放宽为 public，替代已弃用的 Fabric BlockEntityRendererRegistry）
		BlockEntityRendererFactories.register(
				RegAddonBlockEntities.POTION_STORAGE_BOX_BE,
				PotionStorageBoxRenderer::new);
	}
}
