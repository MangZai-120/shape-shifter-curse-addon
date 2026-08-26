package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyStorageTankBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlockEntities;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 能量储罐液面渲染器（jackcooper）：在玻璃区域 (2,2,2)~(14,14,14) 内部画一个随网络能量比例
 * 升降的「能量液」长方体（参照 Kotori316/FluidTank 的 BER 液面方案）。
 *
 * <p>高度映射：{@code h = MIN_H + ratio * (MAX_H - MIN_H)}，ratio 由服务端按网络总能量/总容量
 * 计算并经 BE 数据包同步（见 {@link EnergyStorageTankBlockEntity#syncFillRatio}）。
 * 液面盒比玻璃内壁再内缩 0.01 格防 z-fighting；顶/底面也画，俯视能看到液面顶盖。
 *
 * <p>动态流动效果：液体贴图为 {@code ssc_addon:textures/block/energy_liquid.png}（16x512 竖排
 * 32 帧帧动画 + 同名 .mcmeta），放进方块图集后由原版 SpriteAnimator 逐帧自动上传播放，
 * 本渲染器无需任何逐帧代码。UV 采样对齐 FluidTank 的 Box.scala：液面底边锚定贴图底部，
 * 液面越高从贴图底部向上截取的区域越大（满罐 = 整张贴图，半罐 = 底半段）。
 *
 * <p>渲染层用 {@code RenderLayer.getTranslucent()}（绑定方块图集，与 FluidTank 的
 * RenderType.translucent 同款）；光照传全亮保持能量自发光感（同岩浆处理方式）。
 */
@Environment(EnvType.CLIENT)
public class EnergyStorageTankRenderer implements BlockEntityRenderer<EnergyStorageTankBlockEntity> {

	/** 玻璃区域边界（与模型 glass 元素 from/to 一致，单位：1/16 格）。 */
	private static final float MIN = 2f / 16f + 0.01f;
	private static final float MAX = 14f / 16f - 0.01f;
	/** 最低液面高度：空罐旁有微量能量时也可见一薄层（对齐 FluidTank 的 minRatio 思路）。 */
	private static final float MIN_H = 2.05f / 16f;
	/** 最高液面高度（留顶框边距）。 */
	private static final float MAX_H = 13.95f / 16f;

	/** 全亮光照（能量液体自发光，同岩浆的处理方式）。 */
	private static final int FULL_BRIGHT = 0xF000F0;

	/** 能量液体帧动画贴图（方块图集精灵，图集动画器自动逐帧播放）。 */
	private static final Identifier ENERGY_LIQUID = new Identifier("ssc_addon", "block/energy_liquid");

	public EnergyStorageTankRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public void render(EnergyStorageTankBlockEntity tank, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider vcp, int light, int overlay) {
		float ratio = tank.getClientFillRatio();
		if (ratio < 0f) {
			return; // 尚未同步（区块竞态/刚放置），不画
		}
		if (ratio <= 0.001f) {
			return; // 空罐，无液体
		}
		float h = MIN_H + ratio * (MAX_H - MIN_H);

		// 从方块图集取动画精灵（minU/maxU/minV/maxV 即当前动画帧的图集区域，逐帧自动更新）
		// 图集 ID 用 PlayerScreenHandler.BLOCK_ATLAS_TEXTURE（非弃用本尊；SpriteAtlasTexture 同名常量仅是转发且已弃用）
		Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
				.getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
				.getSprite(ENERGY_LIQUID);

		VertexConsumer buffer = vcp.getBuffer(RenderLayer.getTranslucent());
		matrices.push();
		Matrix4f pos = matrices.peek().getPositionMatrix();
		Matrix3f normal = matrices.peek().getNormalMatrix();

		// FluidTank 式底段采样：液面底边锚定贴图底部（V 大端），液面越高向上截取越多
		float uMin = sprite.getMinU();
		float uMax = sprite.getMaxU();
		float vMax = sprite.getMaxV();                                   // 贴图底部 = 液体底
		float vMin = vMax - ratio * (sprite.getMaxV() - sprite.getMinV()); // 液面顶对应的 V

		// 六面盒：底/顶/四侧；颜色纯白（纹理自带狐火青蓝色调）
		// 底面（法线 -Y，从下往上看）：采样底段区域
		quad(buffer, pos, normal,
				MIN, MIN_H, MIN,   MAX, MIN_H, MIN,   MAX, MIN_H, MAX,   MIN, MIN_H, MAX,
				uMin, vMin, uMax, vMax, light, overlay, Direction.DOWN);
		// 顶面（法线 +Y，液面顶盖）：采样同底段区域
		quad(buffer, pos, normal,
				MIN, h, MIN,   MIN, h, MAX,   MAX, h, MAX,   MAX, h, MIN,
				uMin, vMin, uMax, vMax, light, overlay, Direction.UP);
		// 北面（-Z）：V 随高度映射（底=贴图底，液面=截取顶）
		quad(buffer, pos, normal,
				MIN, MIN_H, MIN,   MIN, h, MIN,   MAX, h, MIN,   MAX, MIN_H, MIN,
				uMin, vMax, uMax, vMin, light, overlay, Direction.NORTH);
		// 南面（+Z）
		quad(buffer, pos, normal,
				MIN, MIN_H, MAX,   MAX, MIN_H, MAX,   MAX, h, MAX,   MIN, h, MAX,
				uMin, vMax, uMax, vMin, light, overlay, Direction.SOUTH);
		// 西面（-X）
		quad(buffer, pos, normal,
				MIN, MIN_H, MIN,   MIN, MIN_H, MAX,   MIN, h, MAX,   MIN, h, MIN,
				uMin, vMax, uMax, vMin, light, overlay, Direction.WEST);
		// 东面（+X）
		quad(buffer, pos, normal,
				MAX, MIN_H, MIN,   MAX, h, MIN,   MAX, h, MAX,   MAX, MIN_H, MAX,
				uMin, vMax, uMax, vMin, light, overlay, Direction.EAST);

		matrices.pop();
	}

	/**
	 * 按逆时针（面向观察者）写一个四边形顶点并附法线。
	 * 顶点顺序：(x0,y0,z0)→(x1,y1,z1)→(x2,y2,z2)→(x3,y3,z3)，UV 依次为
	 * (u0,v0)、(u1,v0)、(u1,v1)、(u0,v1)。光照取全亮（能量液体自发光）。
	 */
	private static void quad(VertexConsumer vc, Matrix4f pos, Matrix3f normal,
	                         float x0, float y0, float z0,
	                         float x1, float y1, float z1,
	                         float x2, float y2, float z2,
	                         float x3, float y3, float z3,
	                         float u0, float v0, float u1, float v1,
	                         int light, int overlay, Direction dir) {
		float nx = 0f, ny = 0f, nz = 0f;
		switch (dir) {
			case UP -> { ny = 1; }
			case DOWN -> { ny = -1; }
			case NORTH -> { nz = -1; }
			case SOUTH -> { nz = 1; }
			case WEST -> { nx = -1; }
			case EAST -> { nx = 1; }
		}
		vc.vertex(pos, x0, y0, z0).color(255, 255, 255, 255).texture(u0, v0)
				.overlay(overlay).light(FULL_BRIGHT).normal(normal, nx, ny, nz).next();
		vc.vertex(pos, x1, y1, z1).color(255, 255, 255, 255).texture(u1, v0)
				.overlay(overlay).light(FULL_BRIGHT).normal(normal, nx, ny, nz).next();
		vc.vertex(pos, x2, y2, z2).color(255, 255, 255, 255).texture(u1, v1)
				.overlay(overlay).light(FULL_BRIGHT).normal(normal, nx, ny, nz).next();
		vc.vertex(pos, x3, y3, z3).color(255, 255, 255, 255).texture(u0, v1)
				.overlay(overlay).light(FULL_BRIGHT).normal(normal, nx, ny, nz).next();
	}

	/** 客户端注册入口：由 {@code RegAddonBlocks.clientInit()} 调用。 */
	@Environment(EnvType.CLIENT)
	public static void register() {
		// 原版注册入口（经 Fabric transitive-access-wideners 放宽为 public，替代已弃用的 Fabric BlockEntityRendererRegistry）
		BlockEntityRendererFactories.register(
				RegAddonBlockEntities.ENERGY_STORAGE_TANK_BE,
				EnergyStorageTankRenderer::new);
	}
}
